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
 * WWStyle.java: centralised color palette, font scale, spacing constants, border
 * factories, and Swing component factories for all WorldWind Reforged examples.
 */
package gov.nasa.worldwindx.examples.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Single source of truth for the WorldWind Reforged UI design system.
 * <p>
 * All examples must use the constants and factory methods defined here rather than
 * hardcoding colors, fonts, or borders. See {@code doc/ui-style-guide.md} for the
 * full specification.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 *     JLabel lbl  = WWStyle.label("Opacity:");
 *     JButton btn = WWStyle.accentButton("Apply");
 *     panel.setBackground(WWStyle.BG_DARK);
 *     panel.setBorder(WWStyle.sectionBorder("Options"));
 * </pre>
 *
 * seaglassfoundry.com
 */
public final class WWStyle {

    private WWStyle() {}

    // ── Font resolution ──────────────────────────────────────────────────────
    // Tries platform-native UI fonts in order; falls back to logical SansSerif.

    private static final String FONT_FAMILY = resolveUIFont();

    private static String resolveUIFont() {
        for (String name : new String[]{"Segoe UI", "SF Pro Text", "Ubuntu", "Helvetica Neue"}) {
            Font f = new Font(name, Font.PLAIN, 12);
            if (!f.getFamily().equals("Dialog")) return name;
        }
        return "SansSerif";
    }

    // ── Colors ───────────────────────────────────────────────────────────────

    // Background scale — darkest to lightest
    /** Outermost frame / window chrome. */
    public static final Color BG_BASE      = new Color(30,  30,  30);
    /** Primary panel background (sidebar, toolbar). */
    public static final Color BG_DARK      = new Color(45,  45,  48);
    /** Secondary / nested panel background. */
    public static final Color BG_PANEL     = new Color(60,  63,  65);
    /** Input fields, inactive list rows, button resting state. */
    public static final Color BG_FIELD     = new Color(69,  73,  74);
    /** Component hover state (non-accent). */
    public static final Color BG_HOVER     = new Color(79,  82,  84);
    /** Selected list row / focused component highlight. */
    public static final Color BG_SELECTED  = new Color(9,   71,  113);

    // Foreground / text
    /** Main labels, list items, enabled controls. */
    public static final Color FG_PRIMARY   = new Color(220, 220, 220);
    /** Disabled text, hints, secondary info. */
    public static final Color FG_SECONDARY = new Color(160, 160, 160);
    /** Completely disabled / placeholder. */
    public static final Color FG_DISABLED  = new Color(96,  96,  96);

    // Accent (interactive)
    /** Primary buttons, active slider track, focused border. */
    public static final Color ACCENT         = new Color(0,   122, 204);
    /** Accent button hover. */
    public static final Color ACCENT_HOVER   = new Color(28,  151, 234);
    /** Accent button pressed. */
    public static final Color ACCENT_PRESSED = new Color(0,   95,  163);

    // Status / semantic
    /** Success, connected, online. */
    public static final Color STATUS_OK    = new Color(80,  200, 120);
    /** Warning, degraded. */
    public static final Color STATUS_WARN  = new Color(230, 180, 50);
    /** Error, offline, failed. */
    public static final Color STATUS_ERROR = new Color(210, 70,  70);
    /** Unknown / not yet checked. */
    public static final Color STATUS_IDLE  = new Color(140, 140, 140);

    // Borders / dividers
    /** Panel borders, divider lines, input field borders. */
    public static final Color BORDER       = new Color(80,  83,  85);
    /** Subtle separators inside panels. */
    public static final Color BORDER_LIGHT = new Color(96,  99,  101);

    // ── Fonts ────────────────────────────────────────────────────────────────

    /** Section titles in titled borders, major headings. */
    public static final Font FONT_HEADING = new Font(FONT_FAMILY, Font.BOLD,  13);
    /** Default label, combo box, list item. */
    public static final Font FONT_BASE    = new Font(FONT_FAMILY, Font.PLAIN, 12);
    /** Button labels, emphasised text. */
    public static final Font FONT_BOLD    = new Font(FONT_FAMILY, Font.BOLD,  12);
    /** Status bar text, secondary info, hints. */
    public static final Font FONT_SMALL   = new Font(FONT_FAMILY, Font.PLAIN, 11);
    /** Status dots, badges, very compact indicators. */
    public static final Font FONT_XS      = new Font(FONT_FAMILY, Font.PLAIN, 10);

    // ── Spacing ──────────────────────────────────────────────────────────────

    /** 4 px — tight internal spacing (icon + text, dot + label). */
    public static final int GAP_XS        = 4;
    /** 8 px — standard component gap (between form rows, between buttons). */
    public static final int GAP_S         = 8;
    /** 16 px — section gap (between titled panels, major groups). */
    public static final int GAP_M         = 16;
    /** 24 px — large section gap (top/bottom padding of major areas). */
    public static final int GAP_L         = 24;
    /** 8 px — inner padding of panels and sections, all sides. */
    public static final int PAD_INNER     = 8;
    /** Fixed preferred width of the right-side control panel sidebar. */
    public static final int SIDEBAR_WIDTH = 220;
    /** Default WorldWindow canvas size. */
    public static final Dimension CANVAS_DEFAULT = new Dimension(900, 600);

    // ── Borders ──────────────────────────────────────────────────────────────

    /**
     * Titled section border — use for every major group in a sidebar panel.
     * Produces a 1 px line border in {@link #BORDER} with a bold title and 8 px inner padding.
     */
    public static Border sectionBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER, 1), title);
        tb.setTitleFont(FONT_BOLD);
        tb.setTitleColor(FG_SECONDARY);
        return BorderFactory.createCompoundBorder(
            tb,
            BorderFactory.createEmptyBorder(PAD_INNER, PAD_INNER, PAD_INNER, PAD_INNER));
    }

    /**
     * Divider border — 1 px top line in {@link #BORDER} with standard padding.
     * Use between sections that share the same panel.
     */
    public static Border dividerBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(GAP_S, PAD_INNER, GAP_S, PAD_INNER));
    }

    /**
     * Field border — 1 px line in {@link #BORDER} with 4/6 px insets.
     * Applied automatically by {@link #textField(int)}; use for any text container.
     */
    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6));
    }

    /**
     * Padding-only border — {@link #PAD_INNER} on all sides, no visible line.
     */
    public static Border padded() {
        return BorderFactory.createEmptyBorder(PAD_INNER, PAD_INNER, PAD_INNER, PAD_INNER);
    }

    /**
     * Padding-only border with explicit vertical and horizontal insets.
     */
    public static Border padded(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    // ── Labels ───────────────────────────────────────────────────────────────

    /**
     * Standard body label — {@link #FONT_BASE}, {@link #FG_PRIMARY}.
     */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BASE);
        l.setForeground(FG_PRIMARY);
        return l;
    }

    /**
     * Label with explicit style — primary uses body style, non-primary uses small / secondary.
     */
    public static JLabel label(String text, boolean primary) {
        JLabel l = new JLabel(text);
        l.setFont(primary ? FONT_BASE : FONT_SMALL);
        l.setForeground(primary ? FG_PRIMARY : FG_SECONDARY);
        return l;
    }

    /**
     * Section heading label — {@link #FONT_HEADING}, {@link #FG_PRIMARY}.
     */
    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEADING);
        l.setForeground(FG_PRIMARY);
        return l;
    }

    /**
     * Coloured dot indicator — "●" glyph in {@link #FONT_XS} with the given colour.
     * Use one of the {@code STATUS_*} constants for the colour.
     */
    public static JLabel statusDot(Color color) {
        JLabel l = new JLabel("●");
        l.setFont(FONT_XS);
        l.setForeground(color);
        return l;
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    /**
     * Primary / accent button — solid {@link #ACCENT} background, white text, hover brightens.
     * Use for the single primary action in a panel (e.g. "Apply", "Go").
     */
    public static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(80, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e)  { b.setBackground(ACCENT_HOVER); }
            @Override public void mouseExited(MouseEvent e)   { b.setBackground(ACCENT); }
            @Override public void mousePressed(MouseEvent e)  { b.setBackground(ACCENT_PRESSED); }
            @Override public void mouseReleased(MouseEvent e) { b.setBackground(ACCENT_HOVER); }
        });
        return b;
    }

    /**
     * Secondary button — field-color background, border, hover turns accent.
     * Use for secondary actions (e.g. "Reset", "Cancel").
     */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BASE);
        b.setForeground(FG_PRIMARY);
        b.setBackground(BG_FIELD);
        b.setOpaque(true);
        b.setBorder(fieldBorder());
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT); b.setForeground(Color.WHITE); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(BG_FIELD); b.setForeground(FG_PRIMARY); }
        });
        return b;
    }

    /**
     * Flat / icon-style button — no background at rest, hover shows {@link #BG_HOVER}.
     * Use for toolbar icons and inline controls.
     */
    public static JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BASE);
        b.setForeground(FG_PRIMARY);
        b.setBackground(BG_DARK);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setOpaque(true); b.setBackground(BG_HOVER); }
            @Override public void mouseExited(MouseEvent e)  { b.setOpaque(false); b.repaint(); }
        });
        return b;
    }

    // ── Text Fields ──────────────────────────────────────────────────────────

    /**
     * Styled text field — {@link #FONT_BASE}, {@link #BG_FIELD} background, {@link #fieldBorder()}.
     */
    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(FONT_BASE);
        f.setForeground(FG_PRIMARY);
        f.setBackground(BG_FIELD);
        f.setCaretColor(FG_PRIMARY);
        f.setBorder(fieldBorder());
        return f;
    }

    // ── Combo Boxes ──────────────────────────────────────────────────────────

    /**
     * Styled combo box — {@link #FONT_BASE}, {@link #BG_FIELD} background.
     * FlatLaf handles drop-down rendering.
     */
    public static <T> JComboBox<T> comboBox(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setFont(FONT_BASE);
        c.setForeground(FG_PRIMARY);
        c.setBackground(BG_FIELD);
        return c;
    }

    // ── Sliders ──────────────────────────────────────────────────────────────

    /**
     * Styled horizontal slider — {@link #ACCENT} thumb via FlatLaf client property.
     */
    public static JSlider slider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setBackground(BG_DARK);
        s.setForeground(FG_SECONDARY);
        s.putClientProperty("JSlider.thumbColor", ACCENT);
        return s;
    }

    // ── Check Boxes ──────────────────────────────────────────────────────────

    /**
     * Styled check box — {@link #FONT_BASE}, {@link #FG_PRIMARY} text, {@link #BG_DARK} background.
     */
    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setFont(FONT_BASE);
        cb.setForeground(FG_PRIMARY);
        cb.setBackground(BG_DARK);
        return cb;
    }

    // ── Scroll Panes ─────────────────────────────────────────────────────────

    /**
     * Styled scroll pane — no visible border, {@link #BG_DARK} viewport, thin FlatLaf scrollbar.
     */
    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_DARK);
        sp.setBackground(BG_DARK);
        return sp;
    }

    // ── Separators ───────────────────────────────────────────────────────────

    /**
     * Horizontal separator line in {@link #BORDER} colour, sized to fill available width.
     */
    public static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER);
        s.setBackground(BG_DARK);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    // ── Panels ───────────────────────────────────────────────────────────────

    /**
     * Standard sidebar panel — {@link #BG_DARK} background and {@link #padded()} border.
     */
    public static JPanel sidebarPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_DARK);
        p.setBorder(padded());
        return p;
    }

    /**
     * Form row panel — {@code FlowLayout(LEFT)} with standard {@link #GAP_XS} gaps,
     * {@link #BG_DARK} background. Use to hold label + control pairs.
     */
    public static JPanel rowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP_XS, GAP_XS));
        p.setBackground(BG_DARK);
        return p;
    }

    // ── FlatLaf UIManager overrides ──────────────────────────────────────────

    /**
     * Applies FlatLaf-specific {@code UIManager} overrides to align built-in component
     * rendering with the WorldWind Reforged colour palette.
     * <p>
     * Call this <em>after</em> {@code FlatDarkLaf.setup()} and before constructing any
     * Swing components.
     * </p>
     */
    public static void applyUIManagerOverrides() {
        UIManager.put("Panel.background",             BG_DARK);
        UIManager.put("TextField.background",         BG_FIELD);
        UIManager.put("TextField.foreground",         FG_PRIMARY);
        UIManager.put("TextArea.background",          BG_FIELD);
        UIManager.put("TextArea.foreground",          FG_PRIMARY);
        UIManager.put("ComboBox.background",          BG_FIELD);
        UIManager.put("ComboBox.foreground",          FG_PRIMARY);
        UIManager.put("List.background",              BG_PANEL);
        UIManager.put("List.foreground",              FG_PRIMARY);
        UIManager.put("List.selectionBackground",     BG_SELECTED);
        UIManager.put("List.selectionForeground",     FG_PRIMARY);
        UIManager.put("CheckBox.background",          BG_DARK);
        UIManager.put("CheckBox.foreground",          FG_PRIMARY);
        UIManager.put("RadioButton.background",       BG_DARK);
        UIManager.put("RadioButton.foreground",       FG_PRIMARY);
        UIManager.put("Label.foreground",             FG_PRIMARY);
        UIManager.put("TitledBorder.titleColor",      FG_SECONDARY);
        UIManager.put("ScrollBar.thumbColor",         BG_HOVER);
        UIManager.put("ScrollBar.trackColor",         BG_PANEL);
        UIManager.put("ScrollBar.width",              8);
        UIManager.put("ScrollBar.thumbArc",           999);     // fully rounded thumb
        UIManager.put("Component.focusColor",         ACCENT);
        UIManager.put("Component.borderColor",        BORDER);
        UIManager.put("Button.foreground",            FG_PRIMARY);
        UIManager.put("OptionPane.background",        BG_DARK);
        UIManager.put("OptionPane.messageForeground", FG_PRIMARY);
        UIManager.put("ToolTip.background",           BG_PANEL);
        UIManager.put("ToolTip.foreground",           FG_PRIMARY);
        UIManager.put("ToolTip.border",               BorderFactory.createLineBorder(BORDER, 1));
        UIManager.put("SplitPane.background",         BG_BASE);
        UIManager.put("TabbedPane.background",        BG_DARK);
        UIManager.put("TabbedPane.foreground",        FG_PRIMARY);
        UIManager.put("Table.background",             BG_PANEL);
        UIManager.put("Table.foreground",             FG_PRIMARY);
        UIManager.put("Table.selectionBackground",    BG_SELECTED);
        UIManager.put("Table.selectionForeground",    FG_PRIMARY);
        UIManager.put("TableHeader.background",       BG_DARK);
        UIManager.put("TableHeader.foreground",       FG_SECONDARY);
        UIManager.put("Tree.background",              BG_PANEL);
        UIManager.put("Tree.foreground",              FG_PRIMARY);
    }
}
