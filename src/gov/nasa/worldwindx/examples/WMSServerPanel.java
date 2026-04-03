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
 * WMSServerPanel.java: server connection panel with editable combo box, status
 * indicator, and async capabilities loading via SwingWorker.
 */
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import gov.nasa.worldwind.ogc.OGCServiceInformation;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Panel for selecting and connecting to a WMS server. Provides an editable combo box
 * with preset servers, a connect button with status indicator, and displays service
 * metadata after a successful connection.
 *
 * <p>Capabilities are fetched asynchronously via {@link SwingWorker}. Connection
 * errors are displayed inline rather than in modal dialogs.</p>
 *
 * seaglassfoundry.com
 */
@SuppressWarnings("serial")
public class WMSServerPanel extends JPanel
{
    private static final Logger logger = Logger.getLogger(WMSServerPanel.class.getName());

    private static final String[] PRESET_SERVERS = {
        "https://ows.terrestris.de/osm/service",
        "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi",
        "https://basemap.nationalmap.gov/arcgis/services/USGSTopo/MapServer/WmsServer",
        "https://ows.mundialis.de/services/service",
        "https://gis.charttools.noaa.gov/arcgis/rest/services/MCS/NOAAChartDisplay/MapServer/exts/MaritimeChartService/WMSServer",
        "https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0r.cgi"
    };

    private final JComboBox<String> serverCombo;
    private final JButton connectButton;
    private final JLabel statusDot;
    private final JLabel statusLabel;
    private final JLabel errorLabel;
    private final JTextArea serviceInfoArea;
    private final JPanel serviceInfoPanel;
    private Consumer<WMSCapabilities> onConnected;
    private SwingWorker<WMSCapabilities, Void> currentWorker;

    public WMSServerPanel()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(WWStyle.BG_DARK);
        setBorder(WWStyle.sectionBorder("WMS Server"));

        // ── Server URL row ──────────────────────────────────────────────
        JPanel urlRow = new JPanel(new BorderLayout(WWStyle.GAP_XS, 0));
        urlRow.setBackground(WWStyle.BG_DARK);
        urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        serverCombo = new JComboBox<>(PRESET_SERVERS);
        serverCombo.setEditable(true);
        serverCombo.setFont(WWStyle.FONT_BASE);
        serverCombo.setForeground(WWStyle.FG_PRIMARY);
        serverCombo.setBackground(WWStyle.BG_FIELD);
        urlRow.add(serverCombo, BorderLayout.CENTER);

        connectButton = WWStyle.accentButton("Connect");
        connectButton.addActionListener(e -> connect());
        urlRow.add(connectButton, BorderLayout.EAST);

        add(urlRow);

        // ── Status row ──────────────────────────────────────────────────
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, WWStyle.GAP_XS, 2));
        statusRow.setBackground(WWStyle.BG_DARK);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        statusDot = WWStyle.statusDot(WWStyle.STATUS_IDLE);
        statusRow.add(statusDot);

        statusLabel = WWStyle.label("Ready", false);
        statusRow.add(statusLabel);

        add(statusRow);

        // ── Error label ─────────────────────────────────────────────────
        errorLabel = new JLabel(" ");
        errorLabel.setFont(WWStyle.FONT_SMALL);
        errorLabel.setForeground(WWStyle.STATUS_ERROR);
        errorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        errorLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        add(errorLabel);

        // ── Service info (hidden until connected) ───────────────────────
        serviceInfoPanel = new JPanel(new BorderLayout());
        serviceInfoPanel.setBackground(WWStyle.BG_DARK);
        serviceInfoPanel.setBorder(BorderFactory.createEmptyBorder(WWStyle.GAP_XS, 0, 0, 0));

        serviceInfoArea = new JTextArea(3, 20);
        serviceInfoArea.setFont(WWStyle.FONT_SMALL);
        serviceInfoArea.setForeground(WWStyle.FG_SECONDARY);
        serviceInfoArea.setBackground(WWStyle.BG_PANEL);
        serviceInfoArea.setEditable(false);
        serviceInfoArea.setLineWrap(true);
        serviceInfoArea.setWrapStyleWord(true);
        serviceInfoArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JScrollPane infoScroll = new JScrollPane(serviceInfoArea);
        infoScroll.setBorder(BorderFactory.createLineBorder(WWStyle.BORDER, 1));
        infoScroll.setPreferredSize(new Dimension(0, 60));
        serviceInfoPanel.add(infoScroll, BorderLayout.CENTER);
        serviceInfoPanel.setVisible(false);

        add(serviceInfoPanel);
    }

    public void setOnConnected(Consumer<WMSCapabilities> callback)
    {
        this.onConnected = callback;
    }

    private void connect()
    {
        // Cancel any in-progress loading
        if (currentWorker != null && !currentWorker.isDone())
            currentWorker.cancel(true);

        String serverUrl = (String) serverCombo.getSelectedItem();
        if (serverUrl == null || serverUrl.trim().isEmpty())
        {
            showError("Please enter a server URL");
            return;
        }
        serverUrl = serverUrl.trim();

        // Validate URI syntax
        final URI uri;
        try
        {
            uri = new URI(serverUrl);
        }
        catch (URISyntaxException e)
        {
            showError("Invalid URL format: " + e.getReason());
            return;
        }

        // Add to combo box if not already present
        boolean found = false;
        for (int i = 0; i < serverCombo.getItemCount(); i++)
        {
            if (serverUrl.equals(serverCombo.getItemAt(i)))
            {
                found = true;
                break;
            }
        }
        if (!found)
            serverCombo.addItem(serverUrl);

        // Show connecting state
        clearError();
        setConnectingState();

        currentWorker = new SwingWorker<>()
        {
            @Override
            protected WMSCapabilities doInBackground() throws Exception
            {
                WMSCapabilities caps = WMSCapabilities.retrieve(uri);
                caps.parse();
                return caps;
            }

            @Override
            protected void done()
            {
                if (isCancelled())
                    return;

                try
                {
                    WMSCapabilities caps = get();
                    setConnectedState(caps);
                    if (onConnected != null)
                        onConnected.accept(caps);
                }
                catch (Exception e)
                {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = classifyError(cause);
                    setErrorState(message);
                    logger.log(Level.WARNING, "WMS connection failed: " + uri + " — " + message);
                    logger.log(Level.FINE, "WMS connection error details", cause);
                }
            }
        };
        currentWorker.execute();
    }

    private void setConnectingState()
    {
        connectButton.setEnabled(false);
        statusDot.setForeground(WWStyle.STATUS_WARN);
        statusLabel.setText("Connecting...");
        serviceInfoPanel.setVisible(false);
    }

    private void setConnectedState(WMSCapabilities caps)
    {
        connectButton.setEnabled(true);
        statusDot.setForeground(WWStyle.STATUS_OK);

        OGCServiceInformation info = caps.getServiceInformation();
        String title = info != null ? info.getServiceTitle() : null;
        statusLabel.setText(title != null ? title : "Connected");

        // Show service info
        if (info != null)
        {
            StringBuilder sb = new StringBuilder();
            if (info.getServiceTitle() != null)
                sb.append(info.getServiceTitle());
            if (info.getServiceAbstract() != null)
            {
                if (sb.length() > 0)
                    sb.append("\n\n");
                sb.append(info.getServiceAbstract());
            }
            if (sb.length() > 0)
            {
                serviceInfoArea.setText(sb.toString());
                serviceInfoArea.setCaretPosition(0);
                serviceInfoPanel.setVisible(true);
            }
        }
    }

    private void setErrorState(String message)
    {
        connectButton.setEnabled(true);
        statusDot.setForeground(WWStyle.STATUS_ERROR);
        statusLabel.setText("Connection failed");
        showError(message);
        serviceInfoPanel.setVisible(false);
    }

    private void showError(String message)
    {
        errorLabel.setText(message);
    }

    private void clearError()
    {
        errorLabel.setText(" ");
    }

    private static String classifyError(Throwable cause)
    {
        String name = cause.getClass().getSimpleName();
        String msg = cause.getMessage();

        if (name.contains("URISyntax"))
            return "Invalid URL format";
        if (name.contains("UnknownHost"))
            return "Server not found: " + msg;
        if (name.contains("ConnectException"))
            return "Connection refused — server may be down";
        if (name.contains("SocketTimeout"))
            return "Connection timed out — server too slow or unreachable";
        if (name.contains("SSLException") || name.contains("CertificateException"))
            return "SSL/TLS error — check server certificate";
        if (name.contains("XMLStream") || name.contains("SAXParse"))
            return "Invalid capabilities document — not a WMS server?";

        return msg != null ? msg : "Unknown error: " + name;
    }
}
