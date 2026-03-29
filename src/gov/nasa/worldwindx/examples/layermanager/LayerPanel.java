/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 *
 * Modifications and additions by seaglassfoundry.com — WorldWind Reforged project.
 * Enhanced with WWStyle dark theme and opacity controls.
 */

package gov.nasa.worldwindx.examples.layermanager;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Represents one layer in the layer manager's layer list.
 * <p>
 * Each row shows an eye-toggle button (visibility), the layer name, a compact opacity
 * slider (0–100 %), and up/down reorder buttons. All controls are styled via
 * {@link WWStyle}.
 * </p>
 *
 * seaglassfoundry.com — opacity slider and eye-toggle added for WorldWind Reforged
 *
 * @author tag
 * @version $Id: LayerPanel.java 1179 2013-02-15 17:47:37Z tgaskins $
 */
@SuppressWarnings("serial")
public class LayerPanel extends JPanel
{
    public static final ImageIcon UP_ARROW =
        new ImageIcon(LayerPanel.class.getResource("/images/up_arrow_16x16.png"));
    public static final ImageIcon DOWN_ARROW =
        new ImageIcon(LayerPanel.class.getResource("/images/down_arrow_16x16.png"));

    protected Layer layer;

    protected JToggleButton eyeToggle;
    protected JLabel nameLabel;
    protected JSlider opacitySlider;
    protected JButton upButton;
    protected JButton downButton;

    public LayerPanel(final WorldWindow wwd, final Layer layer)
    {
        super(new BorderLayout(WWStyle.GAP_XS, 0));
        this.setBackground(WWStyle.BG_DARK);
        this.layer = layer;

        // ── Eye toggle ────────────────────────────────────────────────────
        this.eyeToggle = new JToggleButton(layer.isEnabled() ? "\u25CF" : "\u25CB", layer.isEnabled());
        this.eyeToggle.setFont(WWStyle.FONT_XS);
        this.eyeToggle.setForeground(layer.isEnabled() ? WWStyle.ACCENT : WWStyle.FG_DISABLED);
        this.eyeToggle.setBackground(WWStyle.BG_DARK);
        this.eyeToggle.setBorderPainted(false);
        this.eyeToggle.setContentAreaFilled(false);
        this.eyeToggle.setFocusPainted(false);
        this.eyeToggle.setPreferredSize(new Dimension(18, 18));
        this.eyeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.eyeToggle.setToolTipText("Toggle layer visibility");
        this.eyeToggle.addItemListener(e -> {
            boolean enabled = this.eyeToggle.isSelected();
            layer.setEnabled(enabled);
            this.eyeToggle.setText(enabled ? "\u25CF" : "\u25CB");
            this.eyeToggle.setForeground(enabled ? WWStyle.ACCENT : WWStyle.FG_DISABLED);
            this.nameLabel.setForeground(enabled ? WWStyle.FG_PRIMARY : WWStyle.FG_DISABLED);
            wwd.redraw();
        });
        this.add(this.eyeToggle, BorderLayout.WEST);

        // ── Layer name label ──────────────────────────────────────────────
        this.nameLabel = new JLabel(layer.getName());
        this.nameLabel.setFont(WWStyle.FONT_BASE);
        this.nameLabel.setForeground(layer.isEnabled() ? WWStyle.FG_PRIMARY : WWStyle.FG_DISABLED);

        // ── Opacity slider ────────────────────────────────────────────────
        int initOpacity = (int) Math.round(layer.getOpacity() * 100);
        this.opacitySlider = WWStyle.slider(0, 100, initOpacity);
        this.opacitySlider.setPreferredSize(new Dimension(56, 18));
        this.opacitySlider.setToolTipText("Opacity: " + initOpacity + "%");
        this.opacitySlider.addChangeListener(e -> {
            layer.setOpacity(this.opacitySlider.getValue() / 100.0);
            this.opacitySlider.setToolTipText("Opacity: " + this.opacitySlider.getValue() + "%");
            wwd.redraw();
        });

        JPanel centerPanel = new JPanel(new BorderLayout(WWStyle.GAP_XS, 0));
        centerPanel.setBackground(WWStyle.BG_DARK);
        centerPanel.add(this.nameLabel, BorderLayout.CENTER);
        centerPanel.add(this.opacitySlider, BorderLayout.EAST);
        this.add(centerPanel, BorderLayout.CENTER);

        // ── Up / down reorder buttons ─────────────────────────────────────
        this.upButton = new JButton(UP_ARROW);
        this.upButton.addActionListener(e -> moveLayer(wwd, layer, -1));

        this.downButton = new JButton(DOWN_ARROW);
        this.downButton.addActionListener(e -> moveLayer(wwd, layer, +1));

        for (JButton btn : new JButton[]{this.upButton, this.downButton}) {
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(20, 20));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        JPanel buttonPanel = new JPanel(new GridLayout(1, 0, 2, 0));
        buttonPanel.setBackground(WWStyle.BG_DARK);
        buttonPanel.add(this.upButton);
        buttonPanel.add(this.downButton);
        this.add(buttonPanel, BorderLayout.EAST);

        int index = this.findLayerPosition(wwd, layer);
        this.upButton.setEnabled(index != 0);
        this.downButton.setEnabled(index != wwd.getModel().getLayers().size() - 1);
    }

    public Layer getLayer()
    {
        return this.layer;
    }

    public Font getLayerNameFont()
    {
        return this.nameLabel.getFont();
    }

    public void setLayerNameFont(Font font)
    {
        this.nameLabel.setFont(font);
    }

    protected void moveLayer(WorldWindow wwd, Layer layer, int direction)
    {
        // Moves the layer associated with this instance in the direction indicated relative to the other layers.

        int index = this.findLayerPosition(wwd, layer);
        if (index < 0)
            return; // layer not found

        LayerList layerList = wwd.getModel().getLayers();

        this.upButton.setEnabled(true);
        this.downButton.setEnabled(true);

        if (direction < 0 && index == 0) // can't move lowest layer any lower
        {
            this.upButton.setEnabled(false);
            return;
        }

        if (direction > 0 && index == layerList.size() - 1) // can't move highest layer any higher
        {
            this.downButton.setEnabled(false);
            return;
        }

        // Remove the layer from the layer list and then re-insert it.

        layerList.remove(layer);

        if (direction > 0)
            layerList.add(index + 1, layer);
        else if (direction < 0)
            layerList.add(index - 1, layer);

        // Update WorldWind so the change is visible.
        wwd.redraw();
    }

    protected int findLayerPosition(WorldWindow wwd, Layer layer)
    {
        // Determines the ordinal location of a layer in the layer list.

        for (int i = 0; i < wwd.getModel().getLayers().size(); i++)
        {
            if (layer == wwd.getModel().getLayers().get(i))
                return i;
        }

        return -1;
    }

}
