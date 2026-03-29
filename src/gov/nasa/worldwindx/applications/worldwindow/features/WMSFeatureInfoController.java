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

// Created by seaglassfoundry.com - WMS GetFeatureInfo controller for right-click queries
// on active WMS layers, displaying results in a floating popup window

package gov.nasa.worldwindx.applications.worldwindow.features;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.wms.WMSTiledImageLayer;
import gov.nasa.worldwindx.applications.worldwindow.core.Controller;
import gov.nasa.worldwindx.applications.worldwindow.core.WMSLayerInfo;

/**
 * Handles WMS GetFeatureInfo queries when the user right-clicks on the globe.
 * Queries all active WMS layers and displays the results in a floating popup.
 */
public class WMSFeatureInfoController
{
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 10000;
    private static final int MAX_RESULT_LINES = 20;
    private static final int AUTO_DISMISS_MS = 30000;
    private static final Color BG_COLOR = new Color(245, 245, 248);
    private static final Color BORDER_COLOR = new Color(160, 170, 185);

    private final Controller controller;
    private JWindow popup;
    private Timer dismissTimer;

    // Tracks active WMS layers for querying
    private static final List<ActiveWMSEntry> activeEntries = new ArrayList<>();

    public static class ActiveWMSEntry
    {
        public final WMSCapabilities caps;
        public final String layerName;
        public final String displayName;
        public final Layer layer;

        public ActiveWMSEntry(WMSCapabilities caps, String layerName, String displayName, Layer layer)
        {
            this.caps = caps;
            this.layerName = layerName;
            this.displayName = displayName;
            this.layer = layer;
        }
    }

    public WMSFeatureInfoController(Controller controller)
    {
        this.controller = controller;
        installMouseListener();
    }

    public static void registerActiveLayer(WMSCapabilities caps, WMSLayerInfo info, Layer layer)
    {
        if (caps == null || info == null || layer == null)
            return;

        String layerName = info.getParams().getStringValue(AVKey.LAYER_NAMES);
        String displayName = info.getTitle();
        if (layerName == null)
            return;

        // Avoid duplicates
        for (ActiveWMSEntry entry : activeEntries)
        {
            if (entry.layer == layer)
                return;
        }
        activeEntries.add(new ActiveWMSEntry(caps, layerName, displayName, layer));
    }

    public static void unregisterActiveLayer(Layer layer)
    {
        if (layer == null)
            return;
        java.util.Iterator<ActiveWMSEntry> it = activeEntries.iterator();
        while (it.hasNext())
        {
            if (it.next().layer == layer)
                it.remove();
        }
    }

    private void installMouseListener()
    {
        WorldWindow wwd = controller.getWWd();
        if (wwd == null)
            return;

        ((Component) wwd).addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    Position pos = wwd.getCurrentPosition();
                    if (pos != null)
                        queryFeatureInfo(pos, e.getPoint());
                }
            }
        });
    }

    private void queryFeatureInfo(final Position pos, final Point screenPt)
    {
        if (activeEntries.isEmpty())
            return;

        // Run queries on a background thread
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                double halfDeg = 0.5;
                double minLon = pos.getLongitude().degrees - halfDeg;
                double maxLon = pos.getLongitude().degrees + halfDeg;
                double minLat = pos.getLatitude().degrees - halfDeg;
                double maxLat = pos.getLatitude().degrees + halfDeg;
                int W = 101, H = 101;

                StringBuilder html = new StringBuilder();
                html.append("<html><body style='font-family:Segoe UI,sans-serif;font-size:11px;padding:6px'>");
                html.append("<b>Position:</b> ").append(String.format("%.4f\u00B0, %.4f\u00B0",
                    pos.getLatitude().degrees, pos.getLongitude().degrees)).append("<br><br>");

                boolean hasResults = false;

                for (ActiveWMSEntry entry : activeEntries)
                {
                    if (!(entry.layer instanceof WMSTiledImageLayer))
                        continue;
                    if (!entry.layer.isEnabled())
                        continue;

                    String serviceUrl = entry.caps.getRequestURL("GetFeatureInfo", "HTTP", "get");
                    if (serviceUrl == null)
                        serviceUrl = entry.caps.getRequestURL("GetMap", "HTTP", "get");
                    if (serviceUrl == null)
                        continue;

                    String bbox = String.format("%f,%f,%f,%f", minLon, minLat, maxLon, maxLat);
                    String query = serviceUrl
                        + (serviceUrl.contains("?") ? "&" : "?")
                        + "SERVICE=WMS&REQUEST=GetFeatureInfo&VERSION=1.1.1"
                        + "&LAYERS=" + entry.layerName + "&QUERY_LAYERS=" + entry.layerName
                        + "&STYLES=&SRS=EPSG:4326"
                        + "&BBOX=" + bbox
                        + "&WIDTH=" + W + "&HEIGHT=" + H
                        + "&X=50&Y=50&INFO_FORMAT=text/html";

                    String displayName = entry.displayName != null ? entry.displayName : entry.layerName;
                    html.append("<b>").append(truncate(displayName, 45)).append(":</b><br>");

                    try
                    {
                        URL url = new URL(query);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(CONNECT_TIMEOUT);
                        conn.setReadTimeout(READ_TIMEOUT);
                        conn.connect();
                        if (conn.getResponseCode() == 200)
                        {
                            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            try
                            {
                                String line;
                                int lines = 0;
                                while ((line = br.readLine()) != null && lines < MAX_RESULT_LINES)
                                {
                                    String stripped = line.replaceAll("<[^>]+>", "").trim();
                                    if (!stripped.isEmpty())
                                    {
                                        html.append(stripped).append("<br>");
                                        lines++;
                                        hasResults = true;
                                    }
                                }
                            }
                            finally
                            {
                                br.close();
                            }
                        }
                        else
                        {
                            html.append("(no data - HTTP ").append(conn.getResponseCode()).append(")<br>");
                        }
                    }
                    catch (Exception ex)
                    {
                        html.append("(error: ").append(ex.getMessage()).append(")<br>");
                        Logging.logger().log(Level.FINE, "GetFeatureInfo error for " + entry.layerName, ex);
                    }
                    html.append("<br>");
                }

                html.append("</body></html>");

                if (!hasResults)
                    return;

                final String content = html.toString();
                SwingUtilities.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        showPopup(content, screenPt);
                    }
                });
            }
        }).start();
    }

    private void showPopup(String html, Point screenPt)
    {
        // Dismiss any existing popup
        if (popup != null)
            popup.dispose();
        if (dismissTimer != null)
            dismissTimer.stop();

        // Find parent frame
        WorldWindow wwd = controller.getWWd();
        Component wwdComponent = (Component) wwd;
        JFrame parentFrame = null;
        Component parent = wwdComponent;
        while (parent != null)
        {
            if (parent instanceof JFrame)
            {
                parentFrame = (JFrame) parent;
                break;
            }
            parent = parent.getParent();
        }

        popup = new JWindow(parentFrame);
        popup.setBackground(BG_COLOR);

        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setBackground(BG_COLOR);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(340, 240));
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JButton closeBtn = new JButton("\u2715");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.PLAIN, 11f));
        closeBtn.setOpaque(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setBackground(new Color(0, 0, 0, 0));
        closeBtn.setPreferredSize(new Dimension(24, 20));
        closeBtn.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e) { popup.dispose(); }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG_COLOR);
        top.setBorder(new EmptyBorder(2, 6, 2, 2));
        JLabel title = new JLabel("Feature Info");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        top.add(title, BorderLayout.CENTER);
        top.add(closeBtn, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_COLOR);
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        popup.add(root);
        popup.pack();

        // Position near the click point
        Point globePos = wwdComponent.getLocationOnScreen();
        popup.setLocation(globePos.x + screenPt.x + 12, globePos.y + screenPt.y + 12);
        popup.setVisible(true);

        // Auto-dismiss after 30 seconds
        dismissTimer = new Timer(AUTO_DISMISS_MS, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e) { popup.dispose(); }
        });
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }

    private static String truncate(String s, int max)
    {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
