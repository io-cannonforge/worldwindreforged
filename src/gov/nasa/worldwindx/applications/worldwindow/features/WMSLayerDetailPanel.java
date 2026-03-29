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
 */

// Created by seaglassfoundry.com - WMS layer metadata detail panel with legend display,
// opacity control, image format selection, and CRS compatibility indicator

package gov.nasa.worldwindx.applications.worldwindow.features;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerAttribution;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerDimension;
import gov.nasa.worldwind.ogc.wms.WMSLayerStyle;
import gov.nasa.worldwind.ogc.wms.WMSLogoURL;
import gov.nasa.worldwindx.applications.worldwindow.core.WMSLayerInfo;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerNode;
import gov.nasa.worldwindx.applications.worldwindow.util.Util;

/**
 * Panel that displays detailed metadata for a selected WMS layer, including abstract,
 * keywords, CRS, dimensions, styles with legends, attribution, opacity control,
 * and image format selection.
 */
@SuppressWarnings("serial")
public class WMSLayerDetailPanel extends JPanel
{
    private static final Color SECTION_HEADER_COLOR = new Color(60, 80, 120);
    private static final Color LABEL_COLOR = new Color(80, 80, 80);
    private static final Color VALUE_COLOR = new Color(40, 40, 40);
    private static final Color CRS_OK_COLOR = new Color(40, 140, 40);
    private static final Color CRS_WARN_COLOR = new Color(200, 140, 0);
    private static final int MAX_LEGEND_WIDTH = 280;

    private final WMSPanel parentPanel;
    private final JPanel contentPanel;
    private final JLabel placeholderLabel;

    // Layer controls
    private JPanel opacityPanel;
    private JSlider opacitySlider;
    private JLabel opacityValueLabel;
    private JComboBox<String> formatCombo;

    // Current state
    private LayerNode currentLayerNode;
    private WMSLayerInfo currentInfo;
    private WMSCapabilities capabilities;

    // Legend cache
    private final Map<String, BufferedImage> legendCache = new HashMap<>();

    public WMSLayerDetailPanel(WMSPanel parentPanel)
    {
        super(new BorderLayout());
        this.parentPanel = parentPanel;
        this.setOpaque(false);
        this.setBorder(new EmptyBorder(8, 10, 8, 10));

        this.contentPanel = new JPanel();
        this.contentPanel.setLayout(new BoxLayout(this.contentPanel, BoxLayout.Y_AXIS));
        this.contentPanel.setOpaque(false);

        this.placeholderLabel = new JLabel("Select a layer to view details", SwingConstants.CENTER);
        this.placeholderLabel.setForeground(new Color(140, 140, 140));
        this.placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.ITALIC, 11f));

        this.add(this.placeholderLabel, BorderLayout.CENTER);
    }

    public void setCapabilities(WMSCapabilities caps)
    {
        this.capabilities = caps;
    }

    public void setLayerInfo(WMSLayerInfo info, LayerNode layerNode)
    {
        this.currentInfo = info;
        this.currentLayerNode = layerNode;
        rebuildContent();
    }

    public void clearInfo()
    {
        this.currentInfo = null;
        this.currentLayerNode = null;
        this.capabilities = null;
        this.removeAll();
        this.add(this.placeholderLabel, BorderLayout.CENTER);
        this.revalidate();
        this.repaint();
    }

    public void refreshOpacityVisibility()
    {
        if (this.currentInfo != null && this.currentLayerNode != null)
            rebuildContent();
    }

    private void rebuildContent()
    {
        this.removeAll();
        this.contentPanel.removeAll();

        if (this.currentInfo == null)
        {
            this.add(this.placeholderLabel, BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
            return;
        }

        WMSLayerCapabilities layerCaps = this.currentInfo.getLayerCaps();
        if (layerCaps == null)
        {
            addLabel("No metadata available for this layer.");
            this.add(this.contentPanel, BorderLayout.NORTH);
            this.revalidate();
            this.repaint();
            return;
        }

        // Title
        String title = layerCaps.getTitle();
        if (title != null)
        {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setForeground(VALUE_COLOR);
            titleLabel.setAlignmentX(LEFT_ALIGNMENT);
            titleLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
            this.contentPanel.add(titleLabel);
        }

        // Queryable badge + CRS compatibility
        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgePanel.setOpaque(false);
        badgePanel.setAlignmentX(LEFT_ALIGNMENT);

        if (layerCaps.isQueryable())
        {
            JLabel queryableBadge = makeBadge("Queryable", new Color(40, 140, 40), Color.WHITE);
            badgePanel.add(queryableBadge);
        }

        // CRS compatibility check
        boolean hasCrs84 = hasSupportedCRS(layerCaps);
        if (hasCrs84)
        {
            badgePanel.add(makeBadge("CRS:84 / EPSG:4326", CRS_OK_COLOR, Color.WHITE));
        }
        else
        {
            badgePanel.add(makeBadge("No standard CRS", CRS_WARN_COLOR, Color.WHITE));
        }

        if (badgePanel.getComponentCount() > 0)
        {
            this.contentPanel.add(badgePanel);
            this.contentPanel.add(Box.createVerticalStrut(6));
        }

        // Abstract
        String layerAbstract = layerCaps.getLayerAbstract();
        if (layerAbstract != null && !layerAbstract.isEmpty())
        {
            addSectionHeader("Description");
            JTextArea abstractArea = new JTextArea(layerAbstract);
            abstractArea.setLineWrap(true);
            abstractArea.setWrapStyleWord(true);
            abstractArea.setEditable(false);
            abstractArea.setOpaque(false);
            abstractArea.setFont(abstractArea.getFont().deriveFont(Font.PLAIN, 11f));
            abstractArea.setForeground(VALUE_COLOR);
            abstractArea.setAlignmentX(LEFT_ALIGNMENT);
            abstractArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            this.contentPanel.add(abstractArea);
            this.contentPanel.add(Box.createVerticalStrut(6));
        }

        // Keywords
        Set<String> keywords = layerCaps.getKeywords();
        if (keywords != null && !keywords.isEmpty())
        {
            addSectionHeader("Keywords");
            StringBuilder kb = new StringBuilder();
            for (String kw : keywords)
            {
                if (kb.length() > 0) kb.append(", ");
                kb.append(kw);
            }
            addValueLabel(kb.toString());
        }

        // Geographic extent
        Sector bbox = layerCaps.getGeographicBoundingBox();
        if (bbox != null)
        {
            addSectionHeader("Geographic Extent");
            String extent = String.format("Lat: %.2f\u00B0 to %.2f\u00B0, Lon: %.2f\u00B0 to %.2f\u00B0",
                bbox.getMinLatitude().degrees, bbox.getMaxLatitude().degrees,
                bbox.getMinLongitude().degrees, bbox.getMaxLongitude().degrees);
            addValueLabel(extent);
        }

        // CRS/SRS list
        Set<String> crsSet = layerCaps.getCRS();
        if (crsSet == null || crsSet.isEmpty())
            crsSet = layerCaps.getSRS();
        if (crsSet != null && !crsSet.isEmpty())
        {
            addSectionHeader("Coordinate Systems (" + crsSet.size() + ")");
            StringBuilder crsText = new StringBuilder();
            int count = 0;
            for (String crs : crsSet)
            {
                if (count > 0) crsText.append(", ");
                crsText.append(crs);
                count++;
                if (count >= 10)
                {
                    crsText.append(", ... (").append(crsSet.size() - 10).append(" more)");
                    break;
                }
            }
            addValueLabel(crsText.toString());
        }

        // Dimensions
        Set<WMSLayerDimension> dimensions = layerCaps.getDimensions();
        if (dimensions != null && !dimensions.isEmpty())
        {
            addSectionHeader("Dimensions");
            for (WMSLayerDimension dim : dimensions)
            {
                String dimText = dim.getName();
                if (dim.getUnits() != null) dimText += " (" + dim.getUnits() + ")";
                if (dim.getDefaultValue() != null) dimText += " [default: " + dim.getDefaultValue() + "]";
                addValueLabel(dimText);
            }
        }

        // Styles with legend support
        Set<WMSLayerStyle> styles = layerCaps.getStyles();
        if (styles != null && !styles.isEmpty())
        {
            addSectionHeader("Styles");
            for (final WMSLayerStyle style : styles)
            {
                String styleName = style.getName();
                String styleTitle = style.getTitle();
                String display = styleName != null ? styleName : "(default)";
                if (styleTitle != null && !styleTitle.equals(styleName))
                    display += " - " + styleTitle;
                addValueLabel(display);

                // Legend URLs
                Set<WMSLogoURL> legendURLs = style.getLegendURLs();
                if (legendURLs != null && !legendURLs.isEmpty())
                {
                    for (final WMSLogoURL legend : legendURLs)
                    {
                        if (legend.getOnlineResource() != null && legend.getOnlineResource().getHref() != null)
                        {
                            final String legendUrl = legend.getOnlineResource().getHref();
                            loadAndDisplayLegend(legendUrl);
                        }
                    }
                }
            }
        }

        // Attribution
        Set<WMSLayerAttribution> attributions = layerCaps.getAttributions();
        if (attributions != null && !attributions.isEmpty())
        {
            addSectionHeader("Attribution");
            for (WMSLayerAttribution attr : attributions)
            {
                if (attr.getTitle() != null)
                    addValueLabel(attr.getTitle());
            }
        }

        // Last update
        String lastUpdate = layerCaps.getLastUpdate();
        if (lastUpdate != null && !lastUpdate.isEmpty())
        {
            addSectionHeader("Last Updated");
            addValueLabel(lastUpdate);
        }

        // Image format selection
        if (this.capabilities != null)
        {
            Set<String> formats = this.capabilities.getImageFormats();
            if (formats != null && !formats.isEmpty())
            {
                addSectionHeader("Image Format");

                this.formatCombo = new JComboBox<>();
                String preferredFormat = null;
                for (String fmt : formats)
                {
                    this.formatCombo.addItem(fmt);
                    if ("image/png".equalsIgnoreCase(fmt))
                        preferredFormat = fmt;
                }
                if (preferredFormat != null)
                    this.formatCombo.setSelectedItem(preferredFormat);

                this.formatCombo.setAlignmentX(LEFT_ALIGNMENT);
                this.formatCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
                this.formatCombo.addActionListener(new ActionListener()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        if (currentInfo != null && formatCombo.getSelectedItem() != null)
                            currentInfo.getParams().setValue(AVKey.IMAGE_FORMAT, formatCombo.getSelectedItem().toString());
                    }
                });
                this.contentPanel.add(this.formatCombo);
                this.contentPanel.add(Box.createVerticalStrut(6));
            }
        }

        // Opacity slider (only visible when layer is active)
        if (this.currentLayerNode != null && this.currentLayerNode.getLayer() != null)
        {
            addSectionHeader("Opacity");

            this.opacityPanel = new JPanel(new BorderLayout(8, 0));
            this.opacityPanel.setOpaque(false);
            this.opacityPanel.setAlignmentX(LEFT_ALIGNMENT);
            this.opacityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

            final Layer activeLayer = this.currentLayerNode.getLayer();
            int currentOpacity = (int) (activeLayer.getOpacity() * 100);

            this.opacitySlider = new JSlider(0, 100, currentOpacity);
            this.opacitySlider.setOpaque(false);
            this.opacitySlider.setToolTipText("Adjust layer opacity");

            this.opacityValueLabel = new JLabel(currentOpacity + "%");
            this.opacityValueLabel.setForeground(LABEL_COLOR);
            this.opacityValueLabel.setPreferredSize(new Dimension(35, 20));

            this.opacitySlider.addChangeListener(new ChangeListener()
            {
                @Override
                public void stateChanged(ChangeEvent e)
                {
                    int val = opacitySlider.getValue();
                    opacityValueLabel.setText(val + "%");
                    activeLayer.setOpacity(val / 100.0);
                    if (parentPanel.getController() != null && parentPanel.getController().getWWd() != null)
                        parentPanel.getController().getWWd().redraw();
                }
            });

            this.opacityPanel.add(this.opacitySlider, BorderLayout.CENTER);
            this.opacityPanel.add(this.opacityValueLabel, BorderLayout.EAST);
            this.contentPanel.add(this.opacityPanel);
            this.contentPanel.add(Box.createVerticalStrut(6));
        }

        // Finalize layout
        this.contentPanel.add(Box.createVerticalGlue());
        this.add(this.contentPanel, BorderLayout.NORTH);
        this.revalidate();
        this.repaint();
    }

    // ---- Legend Loading ----

    private void loadAndDisplayLegend(final String legendUrl)
    {
        // Check cache first
        if (legendCache.containsKey(legendUrl))
        {
            BufferedImage cached = legendCache.get(legendUrl);
            if (cached != null)
                addLegendImage(cached, legendUrl);
            else
                addValueLabel("Legend unavailable");
            return;
        }

        // Show loading placeholder
        final JLabel loadingLabel = new JLabel("Loading legend...");
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.ITALIC, 10f));
        loadingLabel.setForeground(new Color(130, 130, 130));
        loadingLabel.setAlignmentX(LEFT_ALIGNMENT);
        this.contentPanel.add(loadingLabel);

        // Load asynchronously
        new SwingWorker<BufferedImage, Void>()
        {
            @Override
            protected BufferedImage doInBackground() throws Exception
            {
                return ImageIO.read(new URL(legendUrl));
            }

            @Override
            protected void done()
            {
                try
                {
                    BufferedImage image = get();
                    legendCache.put(legendUrl, image);
                    contentPanel.remove(loadingLabel);
                    if (image != null)
                        addLegendImage(image, legendUrl);
                    else
                        addValueLabel("Legend unavailable");
                    contentPanel.revalidate();
                    contentPanel.repaint();
                }
                catch (Exception e)
                {
                    legendCache.put(legendUrl, null);
                    contentPanel.remove(loadingLabel);
                    addValueLabel("Legend unavailable");
                    contentPanel.revalidate();
                    contentPanel.repaint();
                    Util.getLogger().log(Level.FINE, "Failed to load legend: " + legendUrl, e);
                }
            }
        }.execute();
    }

    private void addLegendImage(final BufferedImage image, final String url)
    {
        // Scale to fit if needed
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > MAX_LEGEND_WIDTH)
        {
            double scale = (double) MAX_LEGEND_WIDTH / w;
            w = MAX_LEGEND_WIDTH;
            h = (int) (h * scale);
        }

        Image scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        JLabel imageLabel = new JLabel(new ImageIcon(scaled));
        imageLabel.setAlignmentX(LEFT_ALIGNMENT);
        imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imageLabel.setToolTipText("Click to view full size");
        imageLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        imageLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                showFullSizeLegend(image, url);
            }
        });

        this.contentPanel.add(imageLabel);
        this.contentPanel.add(Box.createVerticalStrut(6));
    }

    private void showFullSizeLegend(BufferedImage image, String url)
    {
        JDialog dialog = new JDialog();
        dialog.setTitle("Layer Legend");
        dialog.setModal(false);

        JLabel fullLabel = new JLabel(new ImageIcon(image));
        JScrollPane scroll = new JScrollPane(fullLabel);
        scroll.setPreferredSize(new Dimension(
            Math.min(image.getWidth() + 40, 800),
            Math.min(image.getHeight() + 40, 600)));

        dialog.add(scroll);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---- UI Helpers ----

    private void addSectionHeader(String text)
    {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setForeground(SECTION_HEADER_COLOR);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(4, 0, 2, 0));
        this.contentPanel.add(header);
    }

    private void addLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(LABEL_COLOR);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        this.contentPanel.add(label);
    }

    private void addValueLabel(String text)
    {
        JLabel label = new JLabel("<html><body style='width:250px'>" + escapeHtml(text) + "</body></html>");
        label.setForeground(VALUE_COLOR);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 8, 3, 0));
        this.contentPanel.add(label);
    }

    private JLabel makeBadge(String text, Color bgColor, Color fgColor)
    {
        JLabel badge = new JLabel(" " + text + " ");
        badge.setOpaque(true);
        badge.setBackground(bgColor);
        badge.setForeground(fgColor);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9f));
        badge.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        return badge;
    }

    private boolean hasSupportedCRS(WMSLayerCapabilities layerCaps)
    {
        Set<String> crs = layerCaps.getCRS();
        Set<String> srs = layerCaps.getSRS();

        if (crs != null)
        {
            for (String c : crs)
            {
                if ("CRS:84".equalsIgnoreCase(c) || "EPSG:4326".equalsIgnoreCase(c))
                    return true;
            }
        }
        if (srs != null)
        {
            for (String s : srs)
            {
                if ("CRS:84".equalsIgnoreCase(s) || "EPSG:4326".equalsIgnoreCase(s))
                    return true;
            }
        }
        return false;
    }

    private String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
