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

// Modified by seaglassfoundry.com - Added server persistence (save/restore between sessions),
// delete confirmation, and improved error handling for URI validation

package gov.nasa.worldwindx.applications.worldwindow.features;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.applications.worldwindow.core.Constants;
import gov.nasa.worldwindx.applications.worldwindow.core.Controller;
import gov.nasa.worldwindx.applications.worldwindow.core.ImageLibrary;
import gov.nasa.worldwindx.applications.worldwindow.core.Registry;
import gov.nasa.worldwindx.applications.worldwindow.core.WWMenu;

/**
 * @author tag
 * @version $Id: WMSDialog.java 1171 2013-02-11 21:45:02Z dcollins $
 */
@SuppressWarnings("serial")
public class WMSDialog extends AbstractFeatureDialog
{
    protected static final String FEATURE_TITLE = "WMS Servers...";
    protected static final String ICON_PATH = "gov/nasa/worldwindx/applications/worldwindow/images/wms-64x64.png";

    protected JTabbedPane tabbedPane = new JTabbedPane();
    @SuppressWarnings("unused") // constructed for side effects (registers mouse listener on WorldWindow)
    private WMSFeatureInfoController featureInfoController;

    public WMSDialog(Registry registry)
    {
        super(FEATURE_TITLE, Constants.FEATURE_WMS_DIALOG, ICON_PATH, registry);
    }

    @Override
    public boolean isTwoState()
    {
        return true;
    }

    @Override
    public boolean isOn()
    {
        return this.dialog != null && this.dialog.isVisible();
    }

    @Override
    public void initialize(final Controller controller)
    {
        super.initialize(controller);

        WWMenu fileMenu = (WWMenu) this.getController().getRegisteredObject(Constants.FILE_MENU);
        if (fileMenu != null)
            fileMenu.addMenu(this.getFeatureID());

        this.tabbedPane = new JTabbedPane();
        this.tabbedPane.setOpaque(false);

        this.tabbedPane.add(new JPanel());
        this.tabbedPane.setTitleAt(0, "+");
        this.tabbedPane.setToolTipTextAt(0, "Connect to WMS Server");

        this.tabbedPane.addChangeListener(new ChangeListener()
        {
            @Override
            public void stateChanged(ChangeEvent changeEvent)
            {
                if (tabbedPane.getSelectedIndex() == 0)
                {
                    addNewPanel(tabbedPane);
                }
            }
        });

        // Restore persisted servers
        List<WMSServerPersistence.ServerEntry> savedServers = WMSServerPersistence.load();
        if (savedServers.isEmpty())
        {
            // Add a blank panel if no saved servers
            this.addNewPanel(this.tabbedPane);
            tabbedPane.setSelectedIndex(1);
        }
        else
        {
            for (final WMSServerPersistence.ServerEntry entry : savedServers)
            {
                final WMSPanel wmsPanel = this.addNewPanel(this.tabbedPane);
                if (entry.name != null && !entry.name.isEmpty())
                    tabbedPane.setTitleAt(tabbedPane.getTabCount() - 1, entry.name);

                // Auto-connect to saved servers on a background thread
                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            wmsPanel.contactWMSServer(entry.url);
                        }
                        catch (URISyntaxException e)
                        {
                            Logging.logger().log(Level.WARNING, "Invalid saved server URL: " + entry.url, e);
                        }
                    }
                });
            }
            tabbedPane.setSelectedIndex(1);
        }

        this.setTaskComponent(this.tabbedPane);
        this.setLocation(SwingConstants.CENTER, SwingConstants.CENTER);
        this.getJDialog().setResizable(true);

        JButton deleteButton = new JButton(
            ImageLibrary.getIcon("gov/nasa/worldwindx/applications/worldwindow/images/delete-20x20.png"));
        deleteButton.setToolTipText("Remove Server");
        deleteButton.setOpaque(false);
        deleteButton.setBackground(new Color(0, 0, 0, 0));
        deleteButton.setBorderPainted(false);
        deleteButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                deleteCurrentPanel();
            }
        });
        deleteButton.setEnabled(true);
        this.insertLeftDialogComponent(deleteButton);

        // Initialize GetFeatureInfo controller for right-click queries
        this.featureInfoController = new WMSFeatureInfoController(controller);

        this.setTitle("WMS Servers");
        this.dialog.validate();
        this.dialog.pack();

        // Save servers when dialog closes
        this.dialog.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                saveServers();
            }

            @Override
            public void windowDeactivated(WindowEvent e)
            {
                saveServers();
            }
        });
    }

    protected void deleteCurrentPanel()
    {
        JComponent tabPane = (JComponent) tabbedPane.getSelectedComponent();
        if (tabPane == null)
            return;

        // Get the panel name for confirmation
        int selectedIndex = tabbedPane.getSelectedIndex();
        String tabTitle = tabbedPane.getTitleAt(selectedIndex);

        int confirm = JOptionPane.showConfirmDialog(
            this.dialog,
            "Remove server \"" + tabTitle + "\"?",
            "Remove Server",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION)
            return;

        WMSPanel wmsPanel = (WMSPanel) tabPane.getClientProperty(Constants.FEATURE_OWNER_PROPERTY);

        if (tabbedPane.getTabCount() > 2)
            tabbedPane.remove(tabPane);
        else
            tabbedPane.setTitleAt(1, "New Server");

        if (wmsPanel != null)
            wmsPanel.clearPanel();

        saveServers();
    }

    protected WMSPanel addNewPanel(final JTabbedPane tabPane)
    {
        final WMSPanel wmsPanel = new WMSPanel(null);
        wmsPanel.initialize(this.controller);
        wmsPanel.getJPanel().putClientProperty("WMS_PANEL", wmsPanel);
        tabPane.putClientProperty(wmsPanel.getURLString(), wmsPanel);

        tabPane.addTab("New Server", wmsPanel.getJPanel());
        tabPane.setSelectedIndex(tabPane.getTabCount() - 1);
        tabPane.setToolTipTextAt(tabbedPane.getSelectedIndex(), "Server WMS Contents");

        wmsPanel.addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (evt.getPropertyName().equals("NewServer"))
                {
                    String serverLocation = (String) evt.getNewValue();

                    if (WWUtil.isEmpty(serverLocation))
                        return;

                    try
                    {
                        addNewPanel(tabPane).contactWMSServer(serverLocation);
                    }
                    catch (URISyntaxException e)
                    {
                        Logging.logger().log(Level.WARNING, "Invalid server URL: " + serverLocation, e);
                    }
                }
            }
        });

        return wmsPanel;
    }

    /**
     * Saves the current set of connected servers to persistent storage.
     */
    protected void saveServers()
    {
        List<WMSServerPersistence.ServerEntry> servers = new ArrayList<>();

        for (int i = 1; i < tabbedPane.getTabCount(); i++)
        {
            JComponent tabComponent = (JComponent) tabbedPane.getComponentAt(i);
            if (tabComponent == null)
                continue;

            WMSPanel wmsPanel = (WMSPanel) tabComponent.getClientProperty("WMS_PANEL");
            if (wmsPanel == null)
                continue;

            String url = wmsPanel.getURLString();
            if (url != null && !url.trim().isEmpty())
            {
                String name = tabbedPane.getTitleAt(i);
                if ("New Server".equals(name))
                    name = "";
                servers.add(new WMSServerPersistence.ServerEntry(name, url));
            }
        }

        WMSServerPersistence.save(servers);
    }
}
