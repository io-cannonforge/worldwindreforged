/*
 * WorldWind Reforged — WCSElevations
 * seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Rewritten example demonstrating WCS (Web Coverage Service) elevation data loading.
 * Original NASA example opened a separate JFrame for controls; this version follows
 * the standard tabbed-sidebar + split-pane layout used by all modern examples.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import gov.nasa.worldwind.Factory;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.WCSTiledImageLayer;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100Capabilities;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100CoverageOfferingBrief;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100DescribeCoverage;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates loading WCS (Web Coverage Service) 1.0.0 data — both elevation and
 * derived products (slope maps, aspect, hillshade, etc.).
 * <p>
 * The default server is the USGS 3DEP (3D Elevation Program) WCS service.  Coverages
 * are automatically classified as either <em>elevation</em> (fed into the globe's
 * compound elevation model for terrain deformation) or <em>overlay</em> (rendered as
 * coloured imagery draped on the terrain).  The Controls tab shows both categories.
 * <p>
 * Additional WCS servers can be connected by entering a new URL and clicking Connect.
 *
 * @see gov.nasa.worldwind.ogc.wcs.wcs100.WCS100Capabilities
 * @see gov.nasa.worldwind.terrain.CompoundElevationModel
 * @see gov.nasa.worldwind.layers.WCSTiledImageLayer
 *
 * seaglassfoundry.com — rewritten for WorldWind Reforged
 */
public class WCSElevations extends ApplicationTemplate
{
    // USGS 3DEP (3D Elevation Program) WCS — active replacement for the retired NASA WorldWind WCS server.
    private static final String DEFAULT_SERVER =
        "https://elevation.nationalmap.gov/arcgis/services/3DEPElevation/ImageServer/WCSServer";

    // ── AppFrame ──────────────────────────────────────────────────────────────

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;

        // ── WCS state ────────────────────────────────────────────────────────
        private WCS100Capabilities caps;
        private final List<CoverageEntry> coverageEntries = new ArrayList<>();

        // ── Controls (assigned in buildControlPanel, used across methods) ─────
        private JTextField serverField;
        private JButton    connectBtn;
        private JLabel     statusDot;
        private JLabel     statusLabel;
        private JPanel     coveragesPanel;

        public AppFrame()
        {
            super(true, true, false);

            // Start over the western US where 3DEP elevation data is most dramatic.
            getWwd().getView().setEyePosition(Position.fromDegrees(37.0, -112.0, 5_000_000));

            JPanel wcsControls = buildControlPanel();

            // Modified by seaglassfoundry.com — standard tabbed-sidebar + split-pane layout
            // replacing the original separate JFrame approach.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(wcsControls);
                controlScroll.setBorder(null);
                tabs.addTab("Controls", controlScroll);

                this.controlPanel.add(tabs, BorderLayout.CENTER);

                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    this.wwjPanel, this.controlPanel);
                splitPane.setResizeWeight(0.67);
                splitPane.setDividerSize(5);
                splitPane.setContinuousLayout(true);
                this.getContentPane().add(splitPane, BorderLayout.CENTER);

                this.addComponentListener(new java.awt.event.ComponentAdapter() {
                    private boolean initialized;
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        if (!initialized) {
                            splitPane.setDividerLocation(getWidth() * 2 / 3);
                            initialized = true;
                        }
                    }
                });
            }

            // Auto-connect to the default USGS 3DEP server on startup.
            connectToServer(DEFAULT_SERVER);
        }

        // ── Control panel ─────────────────────────────────────────────────────

        private JPanel buildControlPanel()
        {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBackground(WWStyle.BG_DARK);

            // ── Server section ────────────────────────────────────────────────
            JPanel serverSection = new JPanel();
            serverSection.setLayout(new BoxLayout(serverSection, BoxLayout.Y_AXIS));
            serverSection.setBackground(WWStyle.BG_DARK);
            serverSection.setBorder(WWStyle.sectionBorder("Server"));

            serverField = WWStyle.textField(20);
            serverField.setText(DEFAULT_SERVER);
            serverField.setAlignmentX(Component.LEFT_ALIGNMENT);
            serverField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            serverSection.add(serverField);
            serverSection.add(vgap(WWStyle.GAP_XS));

            connectBtn = WWStyle.accentButton("Connect");
            connectBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            connectBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            connectBtn.addActionListener(e -> connectToServer(serverField.getText().trim()));
            serverField.addActionListener(e -> connectToServer(serverField.getText().trim()));
            serverSection.add(connectBtn);
            serverSection.add(vgap(WWStyle.GAP_XS));

            JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 0));
            statusRow.setBackground(WWStyle.BG_DARK);
            statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            statusDot   = WWStyle.statusDot(WWStyle.STATUS_IDLE);
            statusLabel = WWStyle.label("Not connected", false);
            statusRow.add(statusDot);
            statusRow.add(statusLabel);
            serverSection.add(statusRow);

            // ── Coverages section ─────────────────────────────────────────────
            JPanel coveragesSection = new JPanel();
            coveragesSection.setLayout(new BoxLayout(coveragesSection, BoxLayout.Y_AXIS));
            coveragesSection.setBackground(WWStyle.BG_DARK);
            coveragesSection.setBorder(WWStyle.sectionBorder("Coverages"));

            coveragesPanel = new JPanel();
            coveragesPanel.setLayout(new BoxLayout(coveragesPanel, BoxLayout.Y_AXIS));
            coveragesPanel.setBackground(WWStyle.BG_DARK);
            coveragesPanel.add(WWStyle.label("Connect to a server first.", false));
            coveragesSection.add(coveragesPanel);

            JLabel note = WWStyle.label(
                "<html><i>Note: elevation boundaries may show abrupt edges where coverage ends.</i></html>",
                false);
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            coveragesSection.add(vgap(WWStyle.GAP_XS));
            coveragesSection.add(note);

            root.add(serverSection);
            root.add(vgap(WWStyle.GAP_XS));
            root.add(coveragesSection);

            return root;
        }

        // ── Async server connection ───────────────────────────────────────────

        private void connectToServer(String serverUrl)
        {
            if (serverUrl.isEmpty())
                return;

            disconnectAll();
            setStatus(WWStyle.STATUS_WARN, "Connecting\u2026");
            connectBtn.setEnabled(false);

            new SwingWorker<WCS100Capabilities, Void>()
            {
                @Override
                protected WCS100Capabilities doInBackground() throws Exception
                {
                    URI uri = new URI(serverUrl);
                    WCS100Capabilities c = WCS100Capabilities.retrieve(uri);
                    c.parse();
                    return c;
                }

                @Override
                protected void done()
                {
                    try
                    {
                        caps = get();
                        populateCoverages();
                    }
                    catch (Exception ex)
                    {
                        setStatus(WWStyle.STATUS_ERROR, "Connection failed");
                        Logging.logger().warning("WCSElevations: " + ex.getMessage());
                    }
                    finally
                    {
                        connectBtn.setEnabled(true);
                    }
                }
            }.execute();
        }

        // ── Populate coverage checkboxes ──────────────────────────────────────

        private void populateCoverages()
        {
            coveragesPanel.removeAll();

            if (caps.getContentMetadata() == null
                || caps.getContentMetadata().getCoverageOfferings() == null
                || caps.getContentMetadata().getCoverageOfferings().isEmpty())
            {
                coveragesPanel.add(WWStyle.label("No coverages found.", false));
                coveragesPanel.revalidate();
                coveragesPanel.repaint();
                setStatus(WWStyle.STATUS_WARN, "No coverages");
                return;
            }

            List<WCS100CoverageOfferingBrief> offerings =
                new ArrayList<>(caps.getContentMetadata().getCoverageOfferings());
            offerings.sort((a, b) -> String.valueOf(a.getLabel()).compareTo(String.valueOf(b.getLabel())));

            // Partition into elevation and derived/overlay coverages.
            List<WCS100CoverageOfferingBrief> elevationOfferings = new ArrayList<>();
            List<WCS100CoverageOfferingBrief> overlayOfferings = new ArrayList<>();

            for (WCS100CoverageOfferingBrief offering : offerings)
            {
                if (isDerivedCoverage(offering))
                    overlayOfferings.add(offering);
                else
                    elevationOfferings.add(offering);
            }

            // Elevation section
            if (!elevationOfferings.isEmpty())
            {
                JLabel header = WWStyle.label("Elevation", true);
                header.setAlignmentX(Component.LEFT_ALIGNMENT);
                coveragesPanel.add(header);
                coveragesPanel.add(vgap(2));

                for (WCS100CoverageOfferingBrief offering : elevationOfferings)
                    addCoverageCheckBox(offering, false);
            }

            // Overlay section
            if (!overlayOfferings.isEmpty())
            {
                if (!elevationOfferings.isEmpty())
                    coveragesPanel.add(vgap(WWStyle.GAP_XS));

                JLabel header = WWStyle.label("Overlays", true);
                header.setAlignmentX(Component.LEFT_ALIGNMENT);
                coveragesPanel.add(header);
                coveragesPanel.add(vgap(2));

                for (WCS100CoverageOfferingBrief offering : overlayOfferings)
                    addCoverageCheckBox(offering, true);
            }

            setStatus(WWStyle.STATUS_OK, "Ready \u2014 " + offerings.size() + " coverage"
                + (offerings.size() != 1 ? "s" : ""));
            coveragesPanel.revalidate();
            coveragesPanel.repaint();
        }

        private void addCoverageCheckBox(WCS100CoverageOfferingBrief offering, boolean derived)
        {
            String label = offering.getLabel() != null ? offering.getLabel() : offering.getName();
            CoverageEntry entry = new CoverageEntry(
                offering.getName(), label, caps, derived);

            String cbLabel = derived ? label + " (overlay)" : label;
            JCheckBox cb = WWStyle.checkBox(cbLabel, false);
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> toggleCoverage(entry, cb.isSelected()));
            entry.checkBox = cb;

            coverageEntries.add(entry);
            coveragesPanel.add(cb);
        }

        // ── Coverage classification ──────────────────────────────────────────

        /** Keywords that indicate a derived product rather than raw elevation. */
        private static final String[] DERIVED_KEYWORDS = {
            "slope", "aspect", "hillshade", "curvature", "roughness", "shade", "relief",
            "contour"
        };

        private static boolean isDerivedCoverage(WCS100CoverageOfferingBrief offering)
        {
            // WCS 1.0.0 has no standard field that explicitly marks a coverage as
            // "elevation" vs "derived product".  The spec provides <semantic> and
            // <description> in the RangeSet for this purpose, but most servers
            // (including USGS 3DEP / ArcGIS) leave them empty or generic.
            // We therefore check the three text fields that *are* populated —
            // name, label, and offering-level description — for well-known
            // derived-product keywords.
            String name  = offering.getName()  != null ? offering.getName().toLowerCase()  : "";
            String label = offering.getLabel() != null ? offering.getLabel().toLowerCase() : "";
            String desc  = offering.getDescription() != null ? offering.getDescription().toLowerCase() : "";

            for (String kw : DERIVED_KEYWORDS)
            {
                if (name.contains(kw) || label.contains(kw) || desc.contains(kw))
                    return true;
            }

            // Also check keywords list if available.
            try
            {
                List<String> keywords = offering.getKeywords();
                if (keywords != null)
                {
                    for (String keyword : keywords)
                    {
                        if (keyword == null) continue;
                        String lower = keyword.toLowerCase();
                        for (String kw : DERIVED_KEYWORDS)
                        {
                            if (lower.contains(kw))
                                return true;
                        }
                    }
                }
            }
            catch (NullPointerException ignored)
            {
                // getKeywords() can NPE if the server didn't provide keywords.
            }

            return false;
        }

        // ── Coverage toggle ───────────────────────────────────────────────────

        private void toggleCoverage(CoverageEntry entry, boolean enable)
        {
            if (entry.derived)
                toggleOverlay(entry, enable);
            else
                toggleElevation(entry, enable);

            getWwd().redraw();
        }

        private void toggleElevation(CoverageEntry entry, boolean enable)
        {
            CompoundElevationModel compoundModel =
                (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();

            if (enable)
            {
                if (entry.elevationModel == null)
                    entry.elevationModel = createElevationModel(entry);

                if (entry.elevationModel == null)
                {
                    entry.checkBox.setSelected(false);
                    setStatus(WWStyle.STATUS_ERROR, "Failed to load: " + entry.displayName);
                    return;
                }

                if (!compoundModel.getElevationModels().contains(entry.elevationModel))
                    compoundModel.addElevationModel(entry.elevationModel);
            }
            else
            {
                if (entry.elevationModel != null)
                    compoundModel.removeElevationModel(entry.elevationModel);
            }

            getWwd().firePropertyChange(
                new PropertyChangeEvent(getWwd(), AVKey.ELEVATION_MODEL, null, compoundModel));
        }

        private void toggleOverlay(CoverageEntry entry, boolean enable)
        {
            if (enable)
            {
                if (entry.imageLayer == null)
                    entry.imageLayer = createImageLayer(entry);

                if (entry.imageLayer == null)
                {
                    entry.checkBox.setSelected(false);
                    setStatus(WWStyle.STATUS_ERROR, "Failed to load overlay: " + entry.displayName);
                    return;
                }

                if (!getWwd().getModel().getLayers().contains(entry.imageLayer))
                    insertBeforePlacenames(getWwd(), entry.imageLayer);
            }
            else
            {
                if (entry.imageLayer != null)
                    getWwd().getModel().getLayers().remove(entry.imageLayer);
            }
        }

        // ── Disconnect / cleanup ──────────────────────────────────────────────

        private void disconnectAll()
        {
            CompoundElevationModel compoundModel =
                (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();

            for (CoverageEntry entry : coverageEntries)
            {
                if (entry.elevationModel != null)
                    compoundModel.removeElevationModel(entry.elevationModel);
                if (entry.imageLayer != null)
                    getWwd().getModel().getLayers().remove(entry.imageLayer);
            }

            coverageEntries.clear();
            coveragesPanel.removeAll();
            coveragesPanel.add(WWStyle.label("Connect to a server first.", false));
            coveragesPanel.revalidate();
            coveragesPanel.repaint();
            caps = null;
        }

        // ── Coverage factories ────────────────────────────────────────────────

        /**
         * Fetches DescribeCoverage and builds an AVList with common WCS config.
         * Returns null on failure.
         */
        private static AVList fetchCoverageConfig(CoverageEntry entry)
        {
            AVList configParams = new AVListImpl();
            configParams.setValue(AVKey.COVERAGE_IDENTIFIERS, entry.name);
            configParams.setValue(AVKey.DISPLAY_NAME, entry.displayName);

            // WCS servers can be slow — increase timeouts.
            configParams.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
            configParams.setValue(AVKey.URL_READ_TIMEOUT, 30000);
            configParams.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

            try
            {
                String descUrl = entry.caps.getCapability().getGetOperationAddress("DescribeCoverage");
                WCS100DescribeCoverage desc = WCS100DescribeCoverage.retrieve(new URI(descUrl), entry.name);
                desc.parse();
                configParams.setValue(AVKey.DOCUMENT, desc);
            }
            catch (Exception e)
            {
                Logging.logger().warning("WCSElevations: DescribeCoverage failed for " + entry.name
                    + ": " + e.getMessage());
                return null;
            }

            return configParams;
        }

        /**
         * Creates an {@link ElevationModel} for the given coverage by fetching its
         * DescribeCoverage document and passing it to the WorldWind elevation model factory.
         */
        private static ElevationModel createElevationModel(CoverageEntry entry)
        {
            AVList configParams = fetchCoverageConfig(entry);
            if (configParams == null)
                return null;

            try
            {
                Factory factory = (Factory) WorldWind.createConfigurationComponent(AVKey.ELEVATION_MODEL_FACTORY);
                return (ElevationModel) factory.createFromConfigSource(entry.caps, configParams);
            }
            catch (Exception e)
            {
                Logging.logger().warning("WCSElevations: factory creation failed for " + entry.name
                    + ": " + e.getMessage());
                return null;
            }
        }

        /**
         * Creates a {@link WCSTiledImageLayer} for derived coverages (slope, aspect, etc.)
         * that renders scalar WCS data as coloured imagery draped on the globe.
         *
         * seaglassfoundry.com — new method for WorldWind Reforged
         */
        private static Layer createImageLayer(CoverageEntry entry)
        {
            AVList configParams = fetchCoverageConfig(entry);
            if (configParams == null)
                return null;

            try
            {
                return WCSTiledImageLayer.fromWCS(entry.caps, configParams);
            }
            catch (Exception e)
            {
                Logging.logger().warning("WCSElevations: overlay creation failed for " + entry.name
                    + ": " + e.getMessage());
                return null;
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void setStatus(Color dotColor, String text)
        {
            SwingUtilities.invokeLater(() -> {
                statusDot.setForeground(dotColor);
                statusLabel.setText(text);
            });
        }

        private static JPanel vgap(int height)
        {
            JPanel p = new JPanel();
            p.setBackground(WWStyle.BG_DARK);
            p.setPreferredSize(new Dimension(0, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            return p;
        }

        // ── Per-coverage state ────────────────────────────────────────────────

        private static class CoverageEntry
        {
            final String name;
            final String displayName;
            final WCS100Capabilities caps;
            final boolean derived;
            ElevationModel elevationModel;  // for elevation coverages
            Layer imageLayer;               // for derived/overlay coverages
            JCheckBox checkBox;

            CoverageEntry(String name, String displayName, WCS100Capabilities caps, boolean derived)
            {
                this.name = name;
                this.displayName = displayName;
                this.caps = caps;
                this.derived = derived;
            }
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind \u2014 WCS Elevations (USGS 3DEP)", AppFrame.class);
    }
}
