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

// Modified by seaglassfoundry.com - Complete WMS panel transformation: added search/filter,
// CardLayout loading/error/welcome states, inline error display with retry, quick connect
// preset servers, split pane with layer detail metadata panel

package gov.nasa.worldwindx.applications.worldwindow.features;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.xml.stream.XMLStreamException;

import gov.nasa.worldwind.Factory;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.ogc.wms.WMSCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwind.wms.CapabilitiesRequest;
import gov.nasa.worldwindx.applications.worldwindow.core.Constants;
import gov.nasa.worldwindx.applications.worldwindow.core.Controller;
import gov.nasa.worldwindx.applications.worldwindow.core.ImageLibrary;
import gov.nasa.worldwindx.applications.worldwindow.core.Registry;
import gov.nasa.worldwindx.applications.worldwindow.core.WMSLayerInfo;
import gov.nasa.worldwindx.applications.worldwindow.core.layermanager.LayerManager;
import gov.nasa.worldwindx.applications.worldwindow.core.layermanager.LayerPath;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerNode;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerTree;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerTreeGroupNode;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerTreeModel;
import gov.nasa.worldwindx.applications.worldwindow.features.swinglayermanager.LayerTreeNode;
import gov.nasa.worldwindx.applications.worldwindow.util.GB;
import gov.nasa.worldwindx.applications.worldwindow.util.ShadedPanel;
import gov.nasa.worldwindx.applications.worldwindow.util.Util;

/**
 * @author tag
 * @version $Id: WMSPanel.java 1171 2013-02-11 21:45:02Z dcollins $
 */
@SuppressWarnings("serial")
public class WMSPanel extends AbstractFeaturePanel implements TreeModelListener, NetworkActivitySignal.NetworkUser
{
    protected static final String FEATURE_TITLE = "WMS Server Panel";
    protected static final String ICON_PATH = "gov/nasa/worldwindx/applications/worldwindow/images/wms-64x64.png";

    private static final String CARD_WELCOME = "welcome";
    private static final String CARD_LOADING = "loading";
    private static final String CARD_TREE = "tree";
    private static final String CARD_ERROR = "error";

    private static final String[][] PRESET_SERVERS = {
        {"NASA GIBS", "Global imagery from NASA Earth science satellites",
            "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"},
        {"NASA NEO", "NASA Earth Observations - climate and environment",
            "https://neo.gsfc.nasa.gov/wms/wms"},
        {"NOAA Weather", "NOAA National Weather Service forecast data",
            "https://opengeo.ncep.noaa.gov/geoserver/ows"},
        {"USGS Topo", "USGS National Map topographic basemap",
            "https://basemap.nationalmap.gov/arcgis/services/USGSTopo/MapServer/WMSServer"},
    };

    protected LayerTree layerTree;
    protected JTextField nameField;
    protected JTextField urlField;
    protected JButton infoButton;
    protected Thread loadingThread;
    protected URI serverURI;

    // Card layout components
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JProgressBar progressBar;
    private JLabel loadingStatusLabel;
    private JLabel errorIconLabel;
    private JLabel errorMessageLabel;
    private JButton retryButton;
    private JButton viewRawButton;

    // Filter components
    private JTextField filterField;
    private JButton clearFilterButton;
    private LayerTreeModel fullModel;

    // Detail panel
    private WMSLayerDetailPanel detailPanel;
    private WMSCapabilities currentCaps;

    public WMSPanel(Registry registry)
    {
        super(FEATURE_TITLE, Constants.FEATURE_WMS_PANEL, new ShadedPanel(new BorderLayout()), registry);
    }

    public String getURLString()
    {
        return this.urlField.getText();
    }

    @Override
    public boolean hasNetworkActivity()
    {
        return this.loadingThread != null && this.loadingThread.isAlive();
    }

    @Override
    public void initialize(final Controller controller)
    {
        super.initialize(controller);

        LayerTreeModel model = new LayerTreeModel();
        this.layerTree = new LayerTree(model);
        this.layerTree.getModel().addTreeModelListener(this);

        JPanel np = new JPanel(new BorderLayout(5, 5));
        np.setOpaque(false);
        createComponents(np);

        JPanel np2 = new JPanel(new BorderLayout());
        np2.setOpaque(false);
        np2.setBorder(new EmptyBorder(10, 10, 10, 10));
        np2.add(np, BorderLayout.CENTER);

        this.panel.setOpaque(false);
        this.panel.add(np2, BorderLayout.CENTER);
        this.panel.setToolTipText("");

        this.urlField.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                try
                {
                    String serverURLString = urlField.getText();
                    if (!WWUtil.isEmpty(serverURLString))
                    {
                        if (serverURI == null || !serverURI.toString().contains(serverURLString))
                        {
                            if (getTopGroup() != null)
                                firePropertyChange("NewServer", null, serverURLString);
                            else
                                contactWMSServer(serverURLString);
                        }
                    }
                }
                catch (URISyntaxException e)
                {
                    showInlineError("Invalid URL format. Please check the URL and try again.", false);
                    urlField.setBackground(new Color(255, 230, 230));
                    Util.getLogger().log(Level.SEVERE, "Invalid URL", e);
                }
            }
        });

        // Clear URL error highlighting on edit
        this.urlField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { urlField.setBackground(null); }
            @Override
            public void removeUpdate(DocumentEvent e) { urlField.setBackground(null); }
            @Override
            public void changedUpdate(DocumentEvent e) { urlField.setBackground(null); }
        });

        this.infoButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent actionEvent)
            {
                String urlString = (String) infoButton.getClientProperty("CapsURL");
                if (!WWUtil.isEmpty(urlString))
                {
                    controller.openLink(urlString);
                }
            }
        });
    }

    @Override
    public void treeNodesChanged(TreeModelEvent event)
    {
        Object[] changedNodes = event.getChildren();
        if (changedNodes == null || changedNodes.length <= 0)
            return;

        for (Object o : changedNodes)
        {
            if (o == null)
                continue;

            if (o instanceof LayerTreeGroupNode)
                this.handleGroupSelection((LayerTreeGroupNode) o);
            else if (o instanceof LayerNode)
                this.handleLayerSelection((LayerNode) o);
        }
    }

    protected void handleLayerSelection(LayerNode layerNode)
    {
        if (layerNode.getWmsLayerInfo() == null)
            return;

        LayerManager layerManager = controller.getLayerManager();

        if (layerNode.isSelected())
        {
            if (layerNode.getLayer() == null)
                try
                {
                    this.createLayer(layerNode);
                }
                catch (Exception e)
                {
                    String msg = "Error creating WMS layer " + layerNode.toString();
                    Util.getLogger().log(Level.SEVERE, msg, e);
                    this.controller.showErrorDialog(e, "WMS Error", msg);
                }

            if (layerNode.getLayer() != null)
            {
                layerManager.addLayer(layerNode.getLayer(), new LayerPath(this.nameField.getText()));
                layerManager.selectLayer(layerNode.getLayer(), true);

                // Register for GetFeatureInfo queries
                if (this.currentCaps != null)
                    WMSFeatureInfoController.registerActiveLayer(
                        this.currentCaps, layerNode.getWmsLayerInfo(), layerNode.getLayer());
            }
        }
        else
        {
            WMSFeatureInfoController.unregisterActiveLayer(layerNode.getLayer());
            layerManager.removeLayer(layerNode.getLayer());
        }

        // Refresh the detail panel to show/hide opacity control
        if (this.detailPanel != null)
            this.detailPanel.refreshOpacityVisibility();
    }

    protected void handleGroupSelection(LayerTreeGroupNode groupNode)
    {
        Enumeration<TreeNode> iter = groupNode.breadthFirstEnumeration();
        while (iter.hasMoreElements())
        {
            Object o = iter.nextElement();
            if (!(o instanceof LayerNode) || (o instanceof LayerTreeGroupNode))
                continue;

            LayerNode layerNode = (LayerNode) o;
            layerNode.setSelected(groupNode.isSelected());
            this.handleLayerSelection(layerNode);
        }

        LayerNode topNode = this.getLayerManagerGroupNode();
        if (topNode != null)
        {
            topNode.setSelected(groupNode.isSelected());
            this.controller.getLayerManager().expandGroup(topNode.getTitle());
        }
        this.layerTree.repaint();
    }

    protected void createLayer(LayerNode layerNode)
    {
        if (layerNode == null)
        {
            String msg = "LayerNode is null";
            Util.getLogger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        if (layerNode.getWmsLayerInfo() != null)
        {
            WMSLayerInfo wmsInfo = layerNode.getWmsLayerInfo();
            AVList configParams = wmsInfo.getParams().copy();

            configParams.setValue(AVKey.URL_CONNECT_TIMEOUT, 30000);
            configParams.setValue(AVKey.URL_READ_TIMEOUT, 30000);
            configParams.setValue(AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT, 60000);

            Factory factory = (Factory) WorldWind.createConfigurationComponent(AVKey.LAYER_FACTORY);
            Layer layer = (Layer) factory.createFromConfigSource(wmsInfo.getCaps(), configParams);
            layerNode.setLayer(layer);
        }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent treeModelEvent)
    {
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent treeModelEvent)
    {
    }

    @Override
    public void treeStructureChanged(TreeModelEvent treeModelEvent)
    {
    }

    public void cancel()
    {
        if (this.loadingThread != null && this.loadingThread.isAlive())
            this.loadingThread.interrupt();
    }

    public void clearPanel()
    {
        if (this.loadingThread != null && this.loadingThread.isAlive())
        {
            this.loadingThread.interrupt();
            return;
        }

        if (this.nameField != null)
            this.nameField.setText("");

        if (this.urlField != null)
            this.urlField.setText("");

        if (this.layerTree != null)
            this.layerTree.clearTree();

        if (this.filterField != null)
            this.filterField.setText("");

        if (this.detailPanel != null)
            this.detailPanel.clearInfo();

        this.currentCaps = null;
        this.fullModel = null;

        showCard(CARD_WELCOME);
    }

    public void contactWMSServer(String URLString) throws URISyntaxException
    {
        this.serverURI = new URI(URLString.trim());

        showCard(CARD_LOADING);
        updateLoadingStatus("Connecting to server...");

        this.loadingThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                controller.getNetworkActivitySignal().addNetworkUser(WMSPanel.this);
                try
                {
                    EventQueue.invokeLater(new Runnable()
                    {
                        @Override
                        public void run() { updateLoadingStatus("Fetching capabilities..."); }
                    });

                    CapabilitiesRequest request = new CapabilitiesRequest(serverURI);
                    WMSCapabilities caps = new WMSCapabilities(request);

                    EventQueue.invokeLater(new Runnable()
                    {
                        @Override
                        public void run() { updateLoadingStatus("Parsing capabilities document..."); }
                    });

                    caps.parse();

                    if (!Thread.currentThread().isInterrupted())
                    {
                        EventQueue.invokeLater(new Runnable()
                        {
                            @Override
                            public void run() { updateLoadingStatus("Loading layer list..."); }
                        });
                        createLayerList(caps);
                    }
                }
                catch (XMLStreamException e)
                {
                    final String msg = "Server returned an invalid capabilities document.";
                    Util.getLogger().log(Level.SEVERE, msg + " " + serverURI, e);
                    EventQueue.invokeLater(new Runnable()
                    {
                        @Override
                        public void run() { showInlineError(msg, true); }
                    });
                }
                catch (Exception e)
                {
                    if (e.getClass().getName().toLowerCase().contains("timeout"))
                    {
                        final String msg = "Connection to server timed out.";
                        Util.getLogger().log(Level.SEVERE, msg + " " + serverURI, e);
                        EventQueue.invokeLater(new Runnable()
                        {
                            @Override
                            public void run() { showInlineError(msg, true); }
                        });
                    }
                    else
                    {
                        final String msg = "Unable to connect to server.";
                        Util.getLogger().log(Level.SEVERE, msg + " " + serverURI, e);
                        EventQueue.invokeLater(new Runnable()
                        {
                            @Override
                            public void run() { showInlineError(msg, true); }
                        });
                    }
                }
                finally
                {
                    EventQueue.invokeLater(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            controller.getNetworkActivitySignal().removeNetworkUser(WMSPanel.this);
                            panel.setCursor(Cursor.getDefaultCursor());
                        }
                    });
                }
            }
        });

        this.loadingThread.setPriority(Thread.MIN_PRIORITY);
        this.loadingThread.start();
    }

    protected void createLayerList(final WMSCapabilities caps)
    {
        java.util.List<WMSLayerCapabilities> layers = caps.getCapabilityInformation().getLayerCapabilities();
        if (layers.size() == 0)
        {
            EventQueue.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    showInlineError("Server returned no layers.", false);
                }
            });
            return;
        }

        this.currentCaps = caps;

        // Build the tree model
        WMSLayerCapabilities layer = layers.get(0);
        addLayer(caps, layer, null, (LayerTreeModel) this.layerTree.getModel());

        LayerTreeGroupNode topGroupNode = this.getTopGroup();
        if (topGroupNode != null)
            topGroupNode.setEnableSelectionBox(false);

        // Store the full model for filtering
        this.fullModel = (LayerTreeModel) this.layerTree.getModel();

        String docAbstract = caps.getServiceInformation().getServiceAbstract();
        if (docAbstract != null)
            this.infoButton.setToolTipText(Util.makeMultiLineToolTip(docAbstract));
        String infoUrl = caps.getServiceInformation().getOnlineResource().getHref();
        this.infoButton.putClientProperty("CapsURL", infoUrl != null ? infoUrl
            : caps.getRequestURL("GetCapabilities", "HTTP", "Get"));

        EventQueue.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                if (nameField.getText() == null || nameField.getText().length() == 0)
                    nameField.setText(getServerDisplayString(caps));

                urlField.setText(serverURI.toString());

                // Initialize detail panel with capabilities
                if (detailPanel != null)
                    detailPanel.setCapabilities(caps);

                showCard(CARD_TREE);
                layerTree.expandRow(0);

                if (filterField != null)
                    filterField.setText("");
            }
        });
    }

    protected LayerTreeGroupNode getTopGroup()
    {
        Object root = this.layerTree.getModel().getRoot();
        return root != null && root instanceof LayerTreeGroupNode
            && ((LayerTreeGroupNode) root).getChildCount() > 0 ?
            (LayerTreeGroupNode) ((LayerTreeGroupNode) root).getFirstChild() : null;
    }

    protected LayerNode getLayerManagerGroupNode()
    {
        LayerTreeGroupNode localTopNode = this.getTopGroup();
        if (localTopNode == null)
            return null;

        LayerPath path = new LayerPath(this.getTabTitle());

        return this.controller.getLayerManager().getNode(path);
    }

    public void addItemToComboBox(JComboBox<Object> cmb, Object item)
    {
        if (cmb == null || item == null)
            return;

        for (int i = 0; i < cmb.getItemCount(); i++)
        {
            Object oi = cmb.getItemAt(i);
            if (oi != null && oi.toString().trim().equals(item.toString().trim()))
                return;
        }

        cmb.insertItemAt(item, 1);
    }

    protected String getServerDisplayString(WMSCapabilities caps)
    {
        String title = caps.getServiceInformation().getServiceTitle();
        return title != null ? title : this.serverURI.getHost();
    }

    protected void addLayer(WMSCapabilities caps, WMSLayerCapabilities layerCaps, LayerTreeGroupNode groupNode,
        LayerTreeModel model)
    {
        java.util.List<WMSLayerCapabilities> subLayers = layerCaps.getLayers();
        if (subLayers != null && subLayers.size() > 0)
        {
            LayerTreeGroupNode subGroupNode = new LayerTreeGroupNode(new WMSLayerInfo(caps, layerCaps, null));

            if (groupNode == null)
                model.insertNodeInto(subGroupNode, (LayerTreeGroupNode) model.getRoot(),
                    ((LayerTreeGroupNode) model.getRoot()).getChildCount());
            else
                model.insertNodeInto(subGroupNode, groupNode, groupNode.getChildCount());

            for (WMSLayerCapabilities subLayerCaps : subLayers)
            {
                addLayer(caps, subLayerCaps, subGroupNode, model);
            }

            String toolTipText = layerCaps.getLayerAbstract();
            if (!WWUtil.isEmpty(toolTipText))
                subGroupNode.setToolTipText(Util.makeMultiLineToolTip(toolTipText));
        }
        else
        {
            java.util.List<WMSLayerInfo> layerInfos = WMSLayerInfo.createLayerInfos(caps, layerCaps);
            for (WMSLayerInfo layerInfo : layerInfos)
            {
                LayerTreeNode layerNode = new LayerTreeNode(layerInfo);
                layerNode.setSelected(false);
                layerNode.setAllowsChildren(false);
                if (groupNode == null)
                    model.insertNodeInto(layerNode, (LayerTreeNode) model.getRoot(),
                        ((DefaultMutableTreeNode) model.getRoot()).getChildCount());
                else
                    model.insertNodeInto(layerNode, groupNode, groupNode.getChildCount());

                String toolTipText = layerCaps.getLayerAbstract();
                if (!WWUtil.isEmpty(toolTipText))
                    layerNode.setToolTipText(Util.makeMultiLineToolTip(toolTipText));
            }
        }
    }

    // ---- UI Construction ----

    protected void createComponents(JPanel panel)
    {
        panel.add(makeTopPanel(), BorderLayout.NORTH);
        panel.add(makeCardPanel(), BorderLayout.CENTER);
    }

    protected JPanel makeTopPanel()
    {
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setOpaque(false);

        final JLabel nameLabel = new JLabel("Name");
        nameLabel.setOpaque(false);

        this.nameField = new JTextField(20);
        this.nameField.setOpaque(false);
        this.nameField.setToolTipText("Enter a display name for the server");

        this.infoButton = new JButton(
            ImageLibrary.getIcon("gov/nasa/worldwindx/applications/worldwindow/images/info-20x20.png"));
        this.infoButton.setOpaque(false);
        this.infoButton.setBackground(new Color(0, 0, 0, 0));
        this.infoButton.setBorderPainted(false);

        JLabel urlLabel = new JLabel("URL");
        urlLabel.setOpaque(false);

        this.urlField = new JTextField();
        this.urlField.setOpaque(false);
        this.urlField.setEditable(true);
        this.urlField.setToolTipText("Enter a WMS server URL and press Enter to connect");

        int t = 5, l = 5, b = 5, r = 5;
        topPanel.add(nameLabel, new GB(0, 0).setWeight(0, 0).setAnchor(GridBagConstraints.WEST).setInsets(t, l, b, r));
        topPanel.add(this.nameField, new GB(1, 0).setWeight(100, 100).setAnchor(GridBagConstraints.WEST).setInsets(t, l, b, r).setFill(
            GridBagConstraints.HORIZONTAL));
        topPanel.add(this.infoButton, new GB(2, 0).setWeight(0, 0).setAnchor(GridBagConstraints.WEST).setInsets(t, l, b, r));
        topPanel.add(urlLabel, new GB(0, 1).setWeight(0, 0).setAnchor(GridBagConstraints.WEST).setInsets(t, l, b, r));
        topPanel.add(this.urlField,
            new GB(1, 1, 2, 1).setWeight(100, 100).setAnchor(GridBagConstraints.WEST).setInsets(t, l, b, r).setFill(
                GridBagConstraints.HORIZONTAL));

        this.nameField.getDocument().addUndoableEditListener(new UndoableEditListener()
        {
            @Override
            public void undoableEditHappened(UndoableEditEvent event)
            {
                if (nameField.getText().trim().length() <= 0)
                    return;

                LayerNode lmGroupNode = getLayerManagerGroupNode();
                if (lmGroupNode != null)
                {
                    lmGroupNode.setTitle(nameField.getText());
                    controller.getLayerManager().redraw();
                }

                setTabTitle(nameField.getText());
            }
        });

        return topPanel;
    }

    /**
     * Creates the center panel with CardLayout to switch between welcome, loading, tree, and error states.
     */
    protected JPanel makeCardPanel()
    {
        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(this.cardLayout);
        this.cardPanel.setOpaque(false);

        this.cardPanel.add(makeQuickConnectPanel(), CARD_WELCOME);
        this.cardPanel.add(makeLoadingPanel(), CARD_LOADING);
        this.cardPanel.add(makeTreeWithDetailPanel(), CARD_TREE);
        this.cardPanel.add(makeErrorPanel(), CARD_ERROR);

        this.cardLayout.show(this.cardPanel, CARD_WELCOME);
        return this.cardPanel;
    }

    /**
     * Creates the Quick Connect panel shown on first launch with preset server buttons.
     */
    protected JPanel makeQuickConnectPanel()
    {
        JPanel welcomePanel = new JPanel(new BorderLayout(10, 10));
        welcomePanel.setOpaque(false);
        welcomePanel.setBorder(new EmptyBorder(20, 15, 20, 15));

        JLabel titleLabel = new JLabel("Quick Connect", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setOpaque(false);
        welcomePanel.add(titleLabel, BorderLayout.NORTH);

        JPanel serversGrid = new JPanel(new GridLayout(0, 1, 8, 8));
        serversGrid.setOpaque(false);

        for (final String[] server : PRESET_SERVERS)
        {
            JPanel serverCard = new JPanel(new BorderLayout(8, 2));
            serverCard.setOpaque(false);
            serverCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 190, 205), 1),
                new EmptyBorder(8, 12, 8, 12)));

            JLabel nameLabel = new JLabel(server[0]);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
            nameLabel.setOpaque(false);

            JLabel descLabel = new JLabel(server[1]);
            descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 10f));
            descLabel.setForeground(new Color(100, 100, 100));
            descLabel.setOpaque(false);

            serverCard.add(nameLabel, BorderLayout.NORTH);
            serverCard.add(descLabel, BorderLayout.CENTER);

            // Make the whole card clickable
            serverCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            serverCard.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    connectToPresetServer(server[0], server[2]);
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e)
                {
                    serverCard.setBackground(new Color(230, 238, 250));
                    serverCard.setOpaque(true);
                    serverCard.repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e)
                {
                    serverCard.setOpaque(false);
                    serverCard.repaint();
                }
            });

            serversGrid.add(serverCard);
        }

        JScrollPane scrollPane = new JScrollPane(serversGrid);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        welcomePanel.add(scrollPane, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel("Or enter a WMS server URL above and press Enter", SwingConstants.CENTER);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 10f));
        hintLabel.setForeground(new Color(120, 120, 120));
        hintLabel.setOpaque(false);
        welcomePanel.add(hintLabel, BorderLayout.SOUTH);

        return welcomePanel;
    }

    protected void connectToPresetServer(String name, String url)
    {
        this.nameField.setText(name);
        this.urlField.setText(url);
        try
        {
            if (getTopGroup() != null)
                firePropertyChange("NewServer", null, url);
            else
                contactWMSServer(url);
        }
        catch (URISyntaxException e)
        {
            showInlineError("Invalid preset server URL.", false);
            Util.getLogger().log(Level.SEVERE, "Invalid preset URL: " + url, e);
        }
    }

    /**
     * Creates the loading panel with progress bar and status text.
     */
    protected JPanel makeLoadingPanel()
    {
        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setOpaque(false);

        JPanel centerBox = new JPanel();
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
        centerBox.setOpaque(false);

        this.progressBar = new JProgressBar();
        this.progressBar.setIndeterminate(true);
        this.progressBar.setPreferredSize(new Dimension(250, 20));
        this.progressBar.setMaximumSize(new Dimension(300, 20));
        this.progressBar.setAlignmentX(0.5f);

        this.loadingStatusLabel = new JLabel("Connecting...", SwingConstants.CENTER);
        this.loadingStatusLabel.setAlignmentX(0.5f);
        this.loadingStatusLabel.setFont(loadingStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        this.loadingStatusLabel.setForeground(new Color(80, 80, 80));

        centerBox.add(Box.createVerticalGlue());
        centerBox.add(this.progressBar);
        centerBox.add(Box.createVerticalStrut(10));
        centerBox.add(this.loadingStatusLabel);
        centerBox.add(Box.createVerticalGlue());

        loadingPanel.add(centerBox);
        return loadingPanel;
    }

    /**
     * Creates the error panel with icon, message, and retry/view raw buttons.
     */
    protected JPanel makeErrorPanel()
    {
        JPanel errorPanel = new JPanel(new GridBagLayout());
        errorPanel.setOpaque(false);

        JPanel centerBox = new JPanel();
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
        centerBox.setOpaque(false);
        centerBox.setBorder(new EmptyBorder(20, 30, 20, 30));

        this.errorIconLabel = new JLabel(
            ImageLibrary.getIcon("gov/nasa/worldwindx/applications/worldwindow/images/warning32.png"),
            SwingConstants.CENTER);
        this.errorIconLabel.setAlignmentX(0.5f);

        this.errorMessageLabel = new JLabel("", SwingConstants.CENTER);
        this.errorMessageLabel.setAlignmentX(0.5f);
        this.errorMessageLabel.setFont(errorMessageLabel.getFont().deriveFont(Font.PLAIN, 12f));
        this.errorMessageLabel.setForeground(new Color(180, 50, 50));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonPanel.setOpaque(false);

        this.retryButton = new JButton("Retry");
        this.retryButton.setToolTipText("Retry connecting to the server");
        this.retryButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                String url = urlField.getText();
                if (!WWUtil.isEmpty(url))
                {
                    try
                    {
                        layerTree.clearTree();
                        fullModel = null;
                        contactWMSServer(url);
                    }
                    catch (URISyntaxException ex)
                    {
                        showInlineError("Invalid URL format.", false);
                    }
                }
            }
        });

        this.viewRawButton = new JButton("View Raw");
        this.viewRawButton.setToolTipText("Open the capabilities URL in your browser");
        this.viewRawButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (serverURI != null)
                    controller.openLink(serverURI.toString());
            }
        });

        buttonPanel.add(this.retryButton);
        buttonPanel.add(this.viewRawButton);

        centerBox.add(Box.createVerticalGlue());
        centerBox.add(this.errorIconLabel);
        centerBox.add(Box.createVerticalStrut(12));
        centerBox.add(this.errorMessageLabel);
        centerBox.add(Box.createVerticalStrut(15));
        centerBox.add(buttonPanel);
        centerBox.add(Box.createVerticalGlue());

        errorPanel.add(centerBox);
        return errorPanel;
    }

    /**
     * Creates the tree panel with filter bar and split pane for the detail panel.
     */
    protected JPanel makeTreeWithDetailPanel()
    {
        JPanel treeContainer = new JPanel(new BorderLayout(0, 5));
        treeContainer.setOpaque(false);

        // Filter bar
        treeContainer.add(makeFilterPanel(), BorderLayout.NORTH);

        // Tree in scroll pane
        this.layerTree.setBorder(new EmptyBorder(5, 10, 5, 10));
        this.layerTree.setVisibleRowCount(15);

        // Tree selection listener for detail panel
        this.layerTree.addTreeSelectionListener(new TreeSelectionListener()
        {
            @Override
            public void valueChanged(TreeSelectionEvent e)
            {
                TreePath path = e.getNewLeadSelectionPath();
                if (path != null && path.getLastPathComponent() instanceof LayerNode)
                {
                    LayerNode node = (LayerNode) path.getLastPathComponent();
                    if (detailPanel != null && node.getWmsLayerInfo() != null)
                        detailPanel.setLayerInfo(node.getWmsLayerInfo(), node);
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(layerTree);
        treeScroll.setOpaque(false);
        treeScroll.getViewport().setOpaque(false);

        // Detail panel
        this.detailPanel = new WMSLayerDetailPanel(this);
        JScrollPane detailScroll = new JScrollPane(detailPanel);
        detailScroll.setOpaque(false);
        detailScroll.getViewport().setOpaque(false);
        detailScroll.setPreferredSize(new Dimension(0, 200));
        detailScroll.setMinimumSize(new Dimension(0, 100));

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailScroll);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.6);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(null);

        treeContainer.add(splitPane, BorderLayout.CENTER);
        return treeContainer;
    }

    /**
     * Creates the search/filter bar panel.
     */
    protected JPanel makeFilterPanel()
    {
        JPanel filterPanel = new JPanel(new BorderLayout(4, 0));
        filterPanel.setOpaque(false);
        filterPanel.setBorder(new EmptyBorder(5, 10, 0, 10));

        JLabel searchLabel = new JLabel("\u2315"); // Unicode search/magnifying glass character
        searchLabel.setFont(searchLabel.getFont().deriveFont(16f));
        searchLabel.setForeground(new Color(130, 130, 130));
        searchLabel.setToolTipText("Filter layers by name");
        filterPanel.add(searchLabel, BorderLayout.WEST);

        this.filterField = new JTextField();
        this.filterField.setToolTipText("Type to filter layers (Esc to clear)");
        this.filterField.putClientProperty("JTextField.placeholderText", "Filter layers...");

        this.filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        this.filterField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    filterField.setText("");
                }
            }
        });

        filterPanel.add(this.filterField, BorderLayout.CENTER);

        this.clearFilterButton = new JButton("\u2715"); // Unicode X character
        this.clearFilterButton.setFont(clearFilterButton.getFont().deriveFont(12f));
        this.clearFilterButton.setOpaque(false);
        this.clearFilterButton.setBackground(new Color(0, 0, 0, 0));
        this.clearFilterButton.setBorderPainted(false);
        this.clearFilterButton.setFocusable(false);
        this.clearFilterButton.setToolTipText("Clear filter");
        this.clearFilterButton.setVisible(false);
        this.clearFilterButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                filterField.setText("");
            }
        });

        filterPanel.add(this.clearFilterButton, BorderLayout.EAST);
        return filterPanel;
    }

    // ---- Filter Logic ----

    protected void applyFilter()
    {
        String filterText = this.filterField.getText().trim().toLowerCase();
        this.clearFilterButton.setVisible(!filterText.isEmpty());

        if (this.fullModel == null)
            return;

        LayerTreeGroupNode fullRoot = (LayerTreeGroupNode) this.fullModel.getRoot();
        if (fullRoot == null || fullRoot.getChildCount() == 0)
            return;

        if (filterText.isEmpty())
        {
            // Restore original model
            this.layerTree.setModel(this.fullModel);
            this.layerTree.getModel().addTreeModelListener(this);
            this.layerTree.expandRow(0);
            return;
        }

        // Build a filtered model
        LayerTreeModel filteredModel = new LayerTreeModel();
        LayerTreeGroupNode filteredRoot = (LayerTreeGroupNode) filteredModel.getRoot();

        for (int i = 0; i < fullRoot.getChildCount(); i++)
        {
            Object child = fullRoot.getChildAt(i);
            if (child instanceof LayerTreeGroupNode)
            {
                LayerTreeGroupNode filteredGroup = filterGroup((LayerTreeGroupNode) child, filterText);
                if (filteredGroup != null)
                    filteredModel.insertNodeInto(filteredGroup, filteredRoot, filteredRoot.getChildCount());
            }
            else if (child instanceof LayerTreeNode)
            {
                LayerTreeNode node = (LayerTreeNode) child;
                if (matchesFilter(node, filterText))
                    filteredModel.insertNodeInto(new LayerTreeNode(node), filteredRoot, filteredRoot.getChildCount());
            }
        }

        this.layerTree.setModel(filteredModel);
        filteredModel.addTreeModelListener(this);

        // Expand all rows in filtered view
        for (int i = 0; i < this.layerTree.getRowCount(); i++)
            this.layerTree.expandRow(i);
    }

    private LayerTreeGroupNode filterGroup(LayerTreeGroupNode group, String filterText)
    {
        // Check if group title matches
        boolean groupMatches = group.getTitle() != null &&
            group.getTitle().toLowerCase().contains(filterText);

        List<Object> matchingChildren = new ArrayList<>();

        for (int i = 0; i < group.getChildCount(); i++)
        {
            Object child = group.getChildAt(i);
            if (child instanceof LayerTreeGroupNode)
            {
                LayerTreeGroupNode childGroup = filterGroup((LayerTreeGroupNode) child, filterText);
                if (childGroup != null)
                    matchingChildren.add(childGroup);
            }
            else if (child instanceof LayerTreeNode)
            {
                LayerTreeNode node = (LayerTreeNode) child;
                if (groupMatches || matchesFilter(node, filterText))
                    matchingChildren.add(new LayerTreeNode(node));
            }
        }

        if (groupMatches || !matchingChildren.isEmpty())
        {
            LayerTreeGroupNode filteredGroup = new LayerTreeGroupNode(group);
            filteredGroup.setEnableSelectionBox(group.isEnableSelectionBox());

            for (Object child : matchingChildren)
            {
                if (child instanceof LayerTreeGroupNode)
                    filteredGroup.add((LayerTreeGroupNode) child);
                else if (child instanceof LayerTreeNode)
                {
                    LayerTreeNode node = (LayerTreeNode) child;
                    node.setAllowsChildren(false);
                    filteredGroup.add(node);
                }
            }

            return filteredGroup;
        }

        return null;
    }

    private boolean matchesFilter(LayerTreeNode node, String filterText)
    {
        if (node.getTitle() != null && node.getTitle().toLowerCase().contains(filterText))
            return true;

        // Also check keywords from layer capabilities
        WMSLayerInfo info = node.getWmsLayerInfo();
        if (info != null && info.getLayerCaps() != null)
        {
            WMSLayerCapabilities caps = info.getLayerCaps();
            if (caps.getLayerAbstract() != null && caps.getLayerAbstract().toLowerCase().contains(filterText))
                return true;
            if (caps.getKeywords() != null)
            {
                for (String keyword : caps.getKeywords())
                {
                    if (keyword.toLowerCase().contains(filterText))
                        return true;
                }
            }
        }
        return false;
    }

    // ---- Card/State Management ----

    protected void showCard(String cardName)
    {
        if (this.cardLayout != null && this.cardPanel != null)
            this.cardLayout.show(this.cardPanel, cardName);
    }

    protected void updateLoadingStatus(String message)
    {
        if (this.loadingStatusLabel != null)
            this.loadingStatusLabel.setText(message);
    }

    protected void showInlineError(String message, boolean showViewRaw)
    {
        if (this.errorMessageLabel != null)
            this.errorMessageLabel.setText("<html><center>" + message + "</center></html>");
        if (this.viewRawButton != null)
            this.viewRawButton.setVisible(showViewRaw);
        showCard(CARD_ERROR);
    }

    // ---- Tab Title Management ----

    protected void setTabTitle(String title)
    {
        Container parent = panel.getParent();
        if (parent instanceof JTabbedPane)
        {
            int index = ((JTabbedPane) panel.getParent()).indexOfComponent(panel);
            ((JTabbedPane) panel.getParent()).setTitleAt(index, title != null ? title : "");
        }
    }

    protected String getTabTitle()
    {
        Container parent = panel.getParent();
        if (parent instanceof JTabbedPane)
        {
            int index = ((JTabbedPane) panel.getParent()).indexOfComponent(panel);
            return ((JTabbedPane) panel.getParent()).getTitleAt(index);
        }

        return null;
    }

    // ---- Accessors for sub-panels ----

    // Modified by seaglassfoundry.com — visibility must match AbstractFeature.getController()
    public Controller getController()
    {
        return this.controller;
    }

    protected WMSCapabilities getCurrentCaps()
    {
        return this.currentCaps;
    }
}
