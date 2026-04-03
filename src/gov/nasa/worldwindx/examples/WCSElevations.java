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
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100Capabilities;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100CoverageOfferingBrief;
import gov.nasa.worldwind.ogc.wcs.wcs100.WCS100DescribeCoverage;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Demonstrates loading elevation data from a WCS (Web Coverage Service) 1.0.0 endpoint.
 * <p>
 * The default server is the USGS 3DEP (3D Elevation Program) WCS service, which provides
 * high-resolution elevation data for the United States.  The Controls tab provides a server
 * URL field, a Connect button, and a list of available coverages that can be toggled on and
 * off.  Each selected coverage is added to the globe's compound elevation model so its
 * terrain is rendered with the additional elevation detail.
 * <p>
 * Additional WCS servers can be connected by entering a new URL and clicking Connect.
 *
 * @see gov.nasa.worldwind.ogc.wcs.wcs100.WCS100Capabilities
 * @see gov.nasa.worldwind.terrain.CompoundElevationModel
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

            for (WCS100CoverageOfferingBrief offering : offerings)
            {
                CoverageEntry entry = new CoverageEntry(
                    offering.getName(),
                    offering.getLabel() != null ? offering.getLabel() : offering.getName(),
                    caps);

                JCheckBox cb = WWStyle.checkBox(entry.displayName, false);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.addActionListener(e -> toggleCoverage(entry, cb.isSelected()));
                entry.checkBox = cb;

                coverageEntries.add(entry);
                coveragesPanel.add(cb);
            }

            setStatus(WWStyle.STATUS_OK, "Ready \u2014 " + offerings.size() + " coverage"
                + (offerings.size() != 1 ? "s" : ""));
            coveragesPanel.revalidate();
            coveragesPanel.repaint();
        }

        // ── Coverage toggle ───────────────────────────────────────────────────

        private void toggleCoverage(CoverageEntry entry, boolean enable)
        {
            CompoundElevationModel compoundModel =
                (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();

            if (enable)
            {
                if (entry.model == null)
                    entry.model = createElevationModel(entry);

                if (entry.model == null)
                {
                    entry.checkBox.setSelected(false);
                    setStatus(WWStyle.STATUS_ERROR, "Failed to load: " + entry.displayName);
                    return;
                }

                if (!compoundModel.getElevationModels().contains(entry.model))
                    compoundModel.addElevationModel(entry.model);
            }
            else
            {
                if (entry.model != null)
                    compoundModel.removeElevationModel(entry.model);
            }

            getWwd().firePropertyChange(
                new PropertyChangeEvent(getWwd(), AVKey.ELEVATION_MODEL, null, compoundModel));
            getWwd().redraw();
        }

        // ── Disconnect / cleanup ──────────────────────────────────────────────

        private void disconnectAll()
        {
            CompoundElevationModel compoundModel =
                (CompoundElevationModel) getWwd().getModel().getGlobe().getElevationModel();

            for (CoverageEntry entry : coverageEntries)
            {
                if (entry.model != null)
                    compoundModel.removeElevationModel(entry.model);
            }

            coverageEntries.clear();
            coveragesPanel.removeAll();
            coveragesPanel.add(WWStyle.label("Connect to a server first.", false));
            coveragesPanel.revalidate();
            coveragesPanel.repaint();
            caps = null;
        }

        // ── Elevation model factory ───────────────────────────────────────────

        /**
         * Creates an {@link ElevationModel} for the given coverage by fetching its
         * DescribeCoverage document and passing it to the WorldWind elevation model factory.
         * Inlined from {@code WCSCoveragePanel.createComponent()}.
         */
        private static ElevationModel createElevationModel(CoverageEntry entry)
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
            ElevationModel model;
            JCheckBox checkBox;

            CoverageEntry(String name, String displayName, WCS100Capabilities caps)
            {
                this.name = name;
                this.displayName = displayName;
                this.caps = caps;
            }
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind \u2014 WCS Elevations (USGS 3DEP)", AppFrame.class);
    }
}
