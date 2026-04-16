/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Part of the WorldWind Reforged project — seaglassfoundry.com
 * Minesweeper.java: classic Minesweeper played on the globe surface over the Strait of Hormuz.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.SurfaceSector;
import gov.nasa.worldwind.render.SurfaceText;
import gov.nasa.worldwind.view.orbit.OrbitView;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Classic Minesweeper played on the globe surface. The minefield is placed over the narrow
 * point of the Strait of Hormuz — each cell is a {@link SurfaceSector} draped on open water.
 * <p>
 * Left-click to reveal a cell; right-click to toggle a flag. Mine placement is deferred until
 * the first click so the opening move is always safe. Empty regions flood-fill automatically.
 * A green wave sweeps the board on a win; the camera shakes on a detonation.
 * </p>
 *
 * @author seaglassfoundry.com
 */
public class Minesweeper extends ApplicationTemplate
{
    // ── Difficulty presets: { rows, cols, mines } ─────────────────────────────────
    private static final int[][] DIFFICULTIES = { {9, 9, 10}, {16, 16, 40}, {16, 30, 99} };
    private static final String[] DIFF_NAMES  = { "Beginner", "Intermediate", "Expert" };

    // ── Grid location: narrow point of the Strait of Hormuz ───────────────────────
    private static final double GRID_CENTER_LAT = 26.55;
    private static final double GRID_CENTER_LON = 56.25;
    private static final double CELL_SIZE       = 0.01;   // degrees per cell (~1 110 m)

    // ── Cell colours — naval / sea theme ──────────────────────────────────────────
    private static final Color COLOR_HIDDEN   = new Color( 50,  80, 115);
    private static final Color COLOR_REVEALED = new Color(130, 170, 200);
    private static final Color COLOR_FLAGGED  = new Color(255, 150,   0);
    private static final Color COLOR_MINE     = new Color(200,  50,  50);

    private static final Color[] NUMBER_COLORS = {
        new Color(  0,   0, 255),  // 1 — blue
        new Color(  0, 128,   0),  // 2 — dark green
        new Color(255,   0,   0),  // 3 — red
        new Color(  0,   0, 139),  // 4 — dark blue
        new Color(139,   0,   0),  // 5 — dark red
        new Color(  0, 128, 128),  // 6 — teal
        new Color(  0,   0,   0),  // 7 — black
        new Color(128, 128, 128),  // 8 — grey
    };

    // ─────────────────────────────────────────────────────────────────────────────
    //  Cell state & data
    // ─────────────────────────────────────────────────────────────────────────────

    private enum CellState { HIDDEN, REVEALED, FLAGGED }

    private static class Cell
    {
        int row, col, neighborCount;
        boolean isMine;
        CellState state = CellState.HIDDEN;
        SurfaceSector shape;   // coloured background rectangle
        SurfaceText   label;   // number / flag glyph — may be null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  AppFrame
    // ─────────────────────────────────────────────────────────────────────────────

    protected static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        private Cell[][]        grid;
        private RenderableLayer cellLayer;
        private RenderableLayer labelLayer;

        private int rows, cols, totalMines;
        private int flagCount, revealedCount;
        private int difficultyIndex = 1;           // default: Intermediate
        private boolean gameStarted, gameOver;
        private int     elapsedSeconds;
        private Cell    lastClickedMine;

        private Timer  gameTimer;
        private JLabel mineCountLabel;
        private JLabel timerLabel;

        public AppFrame()
        {
            super(true, false, false);             // status bar only — no layer panel, no stats
            int[] diff = DIFFICULTIES[difficultyIndex];
            rows = diff[0];  cols = diff[1];  totalMines = diff[2];
            initGame();
            JPanel gameControls = buildControlPanel();
            this.getContentPane().add(gameControls, BorderLayout.SOUTH);
            this.pack();
            this.getWwd().addSelectListener(new GameController(this));
        }

        // ── Game initialisation ───────────────────────────────────────────────────

        private void initGame()
        {
            gameStarted     = false;
            gameOver        = false;
            flagCount       = 0;
            revealedCount   = 0;
            elapsedSeconds  = 0;
            lastClickedMine = null;
            if (gameTimer != null) gameTimer.stop();

            if (cellLayer  != null) getWwd().getModel().getLayers().remove(cellLayer);
            if (labelLayer != null) getWwd().getModel().getLayers().remove(labelLayer);

            cellLayer  = new RenderableLayer();  cellLayer.setName("Minesweeper Cells");
            labelLayer = new RenderableLayer();  labelLayer.setName("Minesweeper Labels");
            labelLayer.setPickEnabled(false);    // labels must not intercept clicks

            double originLat = GRID_CENTER_LAT - (rows * CELL_SIZE / 2.0);
            double originLon = GRID_CENTER_LON - (cols * CELL_SIZE / 2.0);

            grid = new Cell[rows][cols];
            for (int r = 0; r < rows; r++)
            {
                for (int c = 0; c < cols; c++)
                {
                    Cell cell = new Cell();
                    cell.row = r;  cell.col = c;

                    double minLat = originLat + r * CELL_SIZE;
                    double maxLat = minLat + CELL_SIZE;
                    double minLon = originLon + c * CELL_SIZE;
                    double maxLon = minLon + CELL_SIZE;
                    Sector sector = Sector.fromDegrees(minLat, maxLat, minLon, maxLon);

                    BasicShapeAttributes attrs = new BasicShapeAttributes();
                    attrs.setInteriorMaterial(new Material(COLOR_HIDDEN));
                    attrs.setOutlineMaterial(new Material(new Color(25, 45, 65)));
                    attrs.setInteriorOpacity(0.88);
                    attrs.setDrawOutline(true);
                    attrs.setOutlineWidth(0.8);

                    BasicShapeAttributes hiAttrs = new BasicShapeAttributes(attrs);
                    hiAttrs.setInteriorMaterial(new Material(COLOR_HIDDEN.brighter()));

                    cell.shape = new SurfaceSector(attrs, sector);
                    cell.shape.setHighlightAttributes(hiAttrs);
                    cell.shape.setValue("CELL", cell);

                    cellLayer.addRenderable(cell.shape);
                    grid[r][c] = cell;
                }
            }

            // Insert layers: cells first, then labels above them.
            insertBeforePlacenames(getWwd(), cellLayer);
            insertBeforePlacenames(getWwd(), labelLayer);

            final double altitudeM = 30000.0 * ((double) Math.max(rows, cols) / 16.0);
            flyToGrid(altitudeM);

            if (mineCountLabel != null) updateMineCountLabel();
            if (timerLabel     != null) updateTimerLabel();
            getWwd().redraw();
        }

        // ── Camera fly-to ─────────────────────────────────────────────────────────

        /**
         * Flies the camera to the grid centre.  goTo() uses CLAMP_TO_GROUND mode and
         * captures orbitView.getGlobe() at creation time; that reference is null before
         * the first render.  If the globe is not yet available we register a one-shot
         * RenderingListener that fires after the first buffer-swap and then calls goTo().
         */
        private void flyToGrid(double altitudeM)
        {
            if (getWwd().getView().getGlobe() != null)
            {
                getWwd().getView().goTo(
                    Position.fromDegrees(GRID_CENTER_LAT, GRID_CENTER_LON, 0), altitudeM);
            }
            else
            {
                getWwd().addRenderingListener(new RenderingListener()
                {
                    public void stageChanged(RenderingEvent event)
                    {
                        if (RenderingEvent.AFTER_BUFFER_SWAP.equals(event.getStage()))
                        {
                            getWwd().removeRenderingListener(this);
                            javax.swing.SwingUtilities.invokeLater(() ->
                                getWwd().getView().goTo(
                                    Position.fromDegrees(GRID_CENTER_LAT, GRID_CENTER_LON, 0),
                                    altitudeM));
                        }
                    }
                });
            }
        }

        // ── Mine placement ────────────────────────────────────────────────────────

        private void startGame(int firstRow, int firstCol)
        {
            gameStarted = true;

            // Exclude first cell and all eight neighbours
            Set<Integer> excluded = new HashSet<>();
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                {
                    int nr = firstRow + dr, nc = firstCol + dc;
                    if (nr >= 0 && nr < rows && nc >= 0 && nc < cols)
                        excluded.add(nr * cols + nc);
                }

            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < rows * cols; i++)
                if (!excluded.contains(i)) candidates.add(i);
            Collections.shuffle(candidates);

            for (int i = 0; i < Math.min(totalMines, candidates.size()); i++)
            {
                int idx = candidates.get(i);
                grid[idx / cols][idx % cols].isMine = true;
            }

            // Compute neighbour counts
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                {
                    if (grid[r][c].isMine) continue;
                    int count = 0;
                    for (int dr = -1; dr <= 1; dr++)
                        for (int dc = -1; dc <= 1; dc++)
                        {
                            int nr = r + dr, nc = c + dc;
                            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && grid[nr][nc].isMine)
                                count++;
                        }
                    grid[r][c].neighborCount = count;
                }

            gameTimer = new Timer(1000, e -> { elapsedSeconds++;  updateTimerLabel(); });
            gameTimer.start();
        }

        // ── Reveal ────────────────────────────────────────────────────────────────

        private void revealCell(int row, int col)
        {
            Cell cell = grid[row][col];
            if (cell.state != CellState.HIDDEN || gameOver) return;

            if (!gameStarted) startGame(row, col);

            if (cell.isMine)
            {
                cell.state = CellState.REVEALED;
                lastClickedMine = cell;
                updateCellAppearance(cell);
                endGame(false);
                return;
            }

            cell.state = CellState.REVEALED;
            revealedCount++;
            updateCellAppearance(cell);
            if (cell.neighborCount == 0) floodReveal(row, col);
            checkWin();
            getWwd().redraw();
        }

        private void floodReveal(int startRow, int startCol)
        {
            Queue<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{ startRow, startCol });
            while (!queue.isEmpty())
            {
                int[] pos = queue.poll();
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++)
                    {
                        if (dr == 0 && dc == 0) continue;
                        int nr = pos[0] + dr, nc = pos[1] + dc;
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        Cell nb = grid[nr][nc];
                        if (nb.state != CellState.HIDDEN || nb.isMine) continue;
                        nb.state = CellState.REVEALED;
                        revealedCount++;
                        updateCellAppearance(nb);
                        if (nb.neighborCount == 0) queue.add(new int[]{ nr, nc });
                    }
            }
        }

        // ── Flag ──────────────────────────────────────────────────────────────────

        private void toggleFlag(Cell cell)
        {
            if (gameOver || !gameStarted || cell.state == CellState.REVEALED) return;
            if (cell.state == CellState.HIDDEN)
            {
                cell.state = CellState.FLAGGED;
                flagCount++;
            }
            else
            {
                cell.state = CellState.HIDDEN;
                flagCount--;
            }
            updateCellAppearance(cell);
            updateMineCountLabel();
            getWwd().redraw();
        }

        // ── Win / lose ────────────────────────────────────────────────────────────

        private void checkWin()
        {
            if (revealedCount == rows * cols - totalMines) endGame(true);
        }

        private void endGame(boolean won)
        {
            gameOver = true;
            if (gameTimer != null) gameTimer.stop();
            if (won)
            {
                // Immediately mark correctly-flagged mines green
                for (Cell[] row : grid)
                    for (Cell c : row)
                        if (c.isMine && c.state == CellState.FLAGGED)
                            ((BasicShapeAttributes) c.shape.getAttributes())
                                .setInteriorMaterial(new Material(new Color(30, 180, 80)));
                playWinAnimation();
            }
            else
            {
                playLoseAnimation(lastClickedMine);
            }
        }

        // ── Win animation: green wave from centre outward ─────────────────────────

        private void playWinAnimation()
        {
            List<List<Cell>> rings = buildRingsFromCenter();
            final int[] idx = { 0 };
            final int   total = rings.size();
            Timer waveTimer = new Timer(60, null);
            waveTimer.addActionListener(e ->
            {
                if (idx[0] >= total)
                {
                    waveTimer.stop();
                    showWinOverlay();
                    return;
                }
                float p = (float) idx[0] / total;
                Color waveColor = new Color(0, (int)(100 + 100 * p), (int)(40 + 40 * p));
                for (Cell cell : rings.get(idx[0]))
                    ((BasicShapeAttributes) cell.shape.getAttributes())
                        .setInteriorMaterial(new Material(waveColor));
                getWwd().redraw();
                idx[0]++;
            });
            waveTimer.start();
        }

        /** Returns grid cells in concentric rings expanding from the grid centre (BFS). */
        private List<List<Cell>> buildRingsFromCenter()
        {
            List<List<Cell>> rings = new ArrayList<>();
            boolean[][] visited = new boolean[rows][cols];
            int cr = rows / 2, cc = cols / 2;
            Queue<int[]> q = new ArrayDeque<>();
            q.add(new int[]{ cr, cc });
            visited[cr][cc] = true;
            int[][] dirs = { {-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1} };
            while (!q.isEmpty())
            {
                int sz = q.size();
                List<Cell> ring = new ArrayList<>();
                for (int i = 0; i < sz; i++)
                {
                    int[] pos = q.poll();
                    ring.add(grid[pos[0]][pos[1]]);
                    for (int[] d : dirs)
                    {
                        int nr = pos[0] + d[0], nc = pos[1] + d[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc])
                        {
                            visited[nr][nc] = true;
                            q.add(new int[]{ nr, nc });
                        }
                    }
                }
                rings.add(ring);
            }
            return rings;
        }

        private void showWinOverlay()
        {
            String html = "<html><center>"
                + "<b>\u2693&nbsp;&nbsp;STRAIT CLEARED!&nbsp;&nbsp;\u2693</b><br/>"
                + "Safe passage for all vessels.<br/>"
                + "Cleared in <b>" + elapsedSeconds + "s</b>"
                + "</center></html>";
            showSwingOverlay(html, new Color(0, 55, 25, 220), new Color(40, 210, 90), 4500);
        }

        // ── Lose animation: flash → shake → reveal all mines ─────────────────────

        private void playLoseAnimation(Cell minedCell)
        {
            if (minedCell == null) { revealAllMines();  showLoseOverlay();  return; }

            final int[]     flashCount = { 0 };
            final boolean[] isWhite    = { true };
            Timer flashTimer = new Timer(90, null);
            flashTimer.addActionListener(e ->
            {
                if (flashCount[0] >= 8)
                {
                    flashTimer.stop();
                    revealAllMines();
                    shakeCameraEffect();
                    return;
                }
                Color c = isWhite[0] ? Color.WHITE : new Color(220, 50, 50);
                ((BasicShapeAttributes) minedCell.shape.getAttributes())
                    .setInteriorMaterial(new Material(c));
                getWwd().redraw();
                isWhite[0] = !isWhite[0];
                flashCount[0]++;
            });
            flashTimer.start();
        }

        private void shakeCameraEffect()
        {
            double baseHeading = getWwd().getView().getHeading().degrees;
            double[] offsets   = { -4, 4, -3, 3, -2, 2, -1, 1, 0 };
            final int[] step   = { 0 };
            Timer shakeTimer = new Timer(55, null);
            shakeTimer.addActionListener(e ->
            {
                if (step[0] >= offsets.length)
                {
                    shakeTimer.stop();
                    showLoseOverlay();
                    return;
                }
                if (getWwd().getView() instanceof OrbitView)
                {
                    ((OrbitView) getWwd().getView())
                        .setHeading(Angle.fromDegrees(baseHeading + offsets[step[0]]));
                    getWwd().redraw();
                }
                step[0]++;
            });
            shakeTimer.start();
        }

        private void revealAllMines()
        {
            for (Cell[] row : grid)
                for (Cell c : row)
                    if (c.isMine && c.state != CellState.FLAGGED)
                    {
                        c.state = CellState.REVEALED;
                        updateCellAppearance(c);
                    }
            getWwd().redraw();
        }

        private void showLoseOverlay()
        {
            String html = "<html><center>"
                + "<b>\u26A0&nbsp;&nbsp;MINE DETONATED!&nbsp;&nbsp;\u26A0</b><br/>"
                + "The Strait of Hormuz is <b>CLOSED</b>.<br/>"
                + "<i>Better luck next time, Admiral.</i>"
                + "</center></html>";
            showSwingOverlay(html, new Color(80, 0, 0, 220), new Color(220, 50, 50), 5000);
        }

        // ── Swing overlay (replaces AnnotationLayer — MultiLineTextRenderer absent) ──

        private void showSwingOverlay(String html, Color bg, Color border, int durationMs)
        {
            java.awt.Component canvas = (java.awt.Component) getWwd();
            int w = 440, h = 90;
            int cx = canvas.getLocationOnScreen().x - getLocationOnScreen().x
                     + canvas.getWidth()  / 2;
            int cy = canvas.getLocationOnScreen().y - getLocationOnScreen().y
                     + canvas.getHeight() / 2;

            JLabel lbl = new JLabel(html, SwingConstants.CENTER);
            lbl.setOpaque(true);
            lbl.setBackground(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 220));
            lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 3),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
            lbl.setFont(new Font("Arial", Font.BOLD, 15));
            lbl.setForeground(Color.WHITE);
            lbl.setBounds(cx - w / 2, cy - h / 2, w, h);

            JLayeredPane layered = getRootPane().getLayeredPane();
            layered.add(lbl, JLayeredPane.POPUP_LAYER);
            layered.repaint();

            new Timer(durationMs, ev ->
            {
                layered.remove(lbl);
                layered.repaint();
                ((Timer) ev.getSource()).stop();
            }).start();
        }

        // ── Cell appearance ───────────────────────────────────────────────────────

        private void updateCellAppearance(Cell cell)
        {
            Color base;
            switch (cell.state)
            {
                case HIDDEN:   base = COLOR_HIDDEN;   break;
                case FLAGGED:  base = COLOR_FLAGGED;  break;
                case REVEALED: base = cell.isMine ? COLOR_MINE : COLOR_REVEALED;  break;
                default:       base = COLOR_HIDDEN;
            }

            ((BasicShapeAttributes) cell.shape.getAttributes())
                .setInteriorMaterial(new Material(base));
            ((BasicShapeAttributes) cell.shape.getHighlightAttributes())
                .setInteriorMaterial(new Material(base.brighter()));

            // Remove old label
            if (cell.label != null)
            {
                labelLayer.removeRenderable(cell.label);
                cell.label = null;
            }

            // Determine label
            String text      = null;
            Color  textColor = Color.WHITE;
            if (cell.state == CellState.FLAGGED)
            {
                text = "F";
            }
            else if (cell.state == CellState.REVEALED)
            {
                if (cell.isMine)
                {
                    text = "\u25CF";  // ● BLACK CIRCLE — geometrically centred, fits the cell
                }
                else if (cell.neighborCount > 0)
                {
                    text      = String.valueOf(cell.neighborCount);
                    textColor = NUMBER_COLORS[cell.neighborCount - 1];
                }
            }

            if (text != null)
            {
                LatLon centroid = cell.shape.getSector().getCentroid();
                Position pos    = new Position(centroid, 0);
                cell.label = new SurfaceText(text, pos);
                cell.label.setFont(new Font("Arial", Font.BOLD, 18));
                cell.label.setColor(textColor);
                cell.label.setTextSize(550);   // metres — fits comfortably in a 1 110 m cell
                labelLayer.addRenderable(cell.label);
            }
        }

        // ── New game ──────────────────────────────────────────────────────────────

        private void newGame()
        {
            int[] diff = DIFFICULTIES[difficultyIndex];
            rows = diff[0];  cols = diff[1];  totalMines = diff[2];
            initGame();
        }

        // ── Control panel ─────────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
            panel.setBackground(WWStyle.BG_DARK);
            panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                new Color(60, 60, 60)));

            mineCountLabel = label("Mines: " + totalMines);
            timerLabel     = label("Time: 0s");

            JButton newBtn = new JButton("New Game");
            newBtn.setFont(new Font("Arial", Font.BOLD, 12));
            newBtn.addActionListener(e -> newGame());

            JComboBox<String> diffBox = new JComboBox<>(DIFF_NAMES);
            diffBox.setSelectedIndex(difficultyIndex);
            diffBox.setFont(new Font("Arial", Font.PLAIN, 12));
            diffBox.addActionListener(e ->
            {
                int sel = diffBox.getSelectedIndex();
                if (sel != difficultyIndex) { difficultyIndex = sel;  newGame(); }
            });

            JLabel diffLabel = new JLabel("Difficulty:");
            diffLabel.setForeground(new Color(170, 190, 210));
            diffLabel.setFont(new Font("Arial", Font.PLAIN, 12));

            panel.add(mineCountLabel);
            panel.add(Box.createHorizontalStrut(20));
            panel.add(timerLabel);
            panel.add(Box.createHorizontalStrut(20));
            panel.add(newBtn);
            panel.add(Box.createHorizontalStrut(10));
            panel.add(diffLabel);
            panel.add(diffBox);
            return panel;
        }

        private static JLabel label(String text)
        {
            JLabel lbl = new JLabel(text);
            lbl.setFont(new Font("Arial", Font.BOLD, 13));
            lbl.setForeground(new Color(200, 220, 240));
            return lbl;
        }

        private void updateMineCountLabel()
        {
            if (mineCountLabel != null)
                mineCountLabel.setText("Mines: " + (totalMines - flagCount));
        }

        private void updateTimerLabel()
        {
            if (timerLabel != null)
                timerLabel.setText("Time: " + elapsedSeconds + "s");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Select listener (picks on cell layer only)
    // ─────────────────────────────────────────────────────────────────────────────

    protected static class GameController implements SelectListener
    {
        private final AppFrame frame;

        GameController(AppFrame frame) { this.frame = frame; }

        @Override
        public void selected(SelectEvent event)
        {
            Object top = event.getTopObject();
            if (!(top instanceof SurfaceSector)) return;
            Object cellObj = ((SurfaceSector) top).getValue("CELL");
            if (!(cellObj instanceof Cell)) return;
            Cell cell = (Cell) cellObj;

            String action = event.getEventAction();
            if (SelectEvent.LEFT_CLICK.equals(action))
            {
                event.consume();
                if (!frame.gameOver) frame.revealCell(cell.row, cell.col);
            }
            else if (SelectEvent.RIGHT_CLICK.equals(action))
            {
                event.consume();
                frame.toggleFlag(cell);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ─────────────────────────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        Configuration.setValue(AVKey.INITIAL_LATITUDE,  26.55);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, 56.25);
        Configuration.setValue(AVKey.INITIAL_ALTITUDE,  35000);
        ApplicationTemplate.start("WorldWind Minesweeper \u2014 Strait of Hormuz", AppFrame.class);
    }
}
