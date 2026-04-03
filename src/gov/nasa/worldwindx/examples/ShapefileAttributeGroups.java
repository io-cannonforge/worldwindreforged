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
package gov.nasa.worldwindx.examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.formats.shapefile.ShapefileLayerFactory;
import gov.nasa.worldwind.formats.shapefile.ShapefileRecord;
import gov.nasa.worldwind.formats.shapefile.ShapefileRenderable;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Illustrates how to display related geometry in an ESRI Shapefile as groups with shared attributes. This example loads
 * a Shapefile defining Earth's political boundaries, then groups those boundaries by continent: Africa, Europe, Asia,
 * Americas, Oceania and Antarctica. The outline color for each continent group can be set to either the default color
 * or the group's color by toggling a check box.
 *
 * @author dcollins
 * @version $Id: ShapefileAttributeGroups.java 2348 2014-09-25 23:35:46Z dcollins $
 */
public class ShapefileAttributeGroups extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
        implements ActionListener, ShapefileRenderable.AttributeDelegate
    {
        private static final long serialVersionUID = 1L;
        protected static String SHAPEFILE_PATH = "gov/nasa/worldwindx/examples/data/ShapefileAttributeGroups.xml";
        protected Map<Integer, AttributeGroup> groups = new LinkedHashMap<>();
        private JPanel titlePanel;

        public AppFrame()
        {
            this.setupGroups();
            this.loadShapefile();

            // Modified by seaglassfoundry.com - put the layers panel and controls panel in a
            // tabbed pane so they don't overlap. Each tab gets a scroll pane for small windows.
            // Use a split pane between the map and the side panel so it can be resized.
            if (this.controlPanel != null)
            {
                this.getContentPane().remove(this.controlPanel);
                this.getContentPane().remove(this.wwjPanel);

                JTabbedPane tabs = new JTabbedPane();
                tabs.setBackground(new Color(45, 45, 48));

                JScrollPane layerScroll = new JScrollPane(this.layerPanel);
                layerScroll.setBorder(null);
                tabs.addTab("Layers", layerScroll);

                JScrollPane controlScroll = new JScrollPane(this.titlePanel);
                controlScroll.setBorder(null);
                tabs.addTab("Continents", controlScroll);

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
        }

        protected void setupGroups()
        {
            // Create the mapping from region key to group name and color.
            // Continent codes are documented at http://unstats.un.org/unsd/methods/m49/m49regin.htm
            // Continent names and colors are based on https://en.wikipedia.org/wiki/Continent

            this.groups.put(2, new AttributeGroup("Africa", new Color(255, 198, 0)));
            this.groups.put(150, new AttributeGroup("Europe", new Color(255, 9, 84)));
            this.groups.put(142, new AttributeGroup("Asia", new Color(255, 133, 0)));
            this.groups.put(19, new AttributeGroup("Americas", new Color(79, 213, 33)));
            this.groups.put(9, new AttributeGroup("Oceania", new Color(193, 83, 220)));
            this.groups.put(0, new AttributeGroup("Antarctica", new Color(7, 152, 249)));

            // Setup the group control panel.

            this.titlePanel = new JPanel(new GridLayout(0, 1, 0, 10));
            this.titlePanel.setBorder(WWStyle.sectionBorder("Continents"));
            this.titlePanel.setToolTipText("Continents to highlight");

            JPanel groupPanel = new JPanel(new GridLayout(0, 1, 0, 5));
            groupPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            this.titlePanel.add(groupPanel);

            for (AttributeGroup group : this.groups.values())
            {
                JCheckBox jcb = new JCheckBox(group.getDisplayName(), group.isUseGroupColor());
                jcb.putClientProperty("group", group);
                jcb.addActionListener(this); // call actionPerformed when the check box selection state changes
                groupPanel.add(jcb);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            JCheckBox jcb = (JCheckBox) e.getSource();
            AttributeGroup group = (AttributeGroup) jcb.getClientProperty("group");
            group.setUseGroupColor(jcb.isSelected());
            this.getWwd().redraw();
        }

        protected void loadShapefile()
        {
            ShapefileLayerFactory factory = (ShapefileLayerFactory) WorldWind.createConfigurationComponent(
                AVKey.SHAPEFILE_LAYER_FACTORY);
            factory.setAttributeDelegate(this); // call assignAttributes for each shapefile record

            Layer layer = (Layer) factory.createFromConfigSource(SHAPEFILE_PATH, null);
            this.getWwd().getModel().getLayers().add(layer);
        }

        @Override
        public void assignAttributes(ShapefileRecord shapefileRecord, ShapefileRenderable.Record renderableRecord)
        {
            Number region = (Number) shapefileRecord.getAttributes().getValue("REGION");
            AttributeGroup group = this.groups.get(region.intValue());
            if (group != null)
            {
                group.addRecord(renderableRecord);
            }
        }
    }

    public static class AttributeGroup
    {
        protected String displayName;
        protected Material groupMaterial;
        protected Material defaultMaterial;
        protected boolean useGroupColor;
        protected ShapeAttributes attributes;

        public AttributeGroup(String displayName, Color color)
        {
            this.displayName = displayName;
            this.groupMaterial = new Material(color); // specifies the diffuse color
            this.useGroupColor = true;
        }

        public String getDisplayName()
        {
            return this.displayName;
        }

        public Color getColor()
        {
            return this.groupMaterial.getDiffuse();
        }

        public boolean isUseGroupColor()
        {
            return this.useGroupColor;
        }

        public void setUseGroupColor(boolean useGroupColor)
        {
            this.useGroupColor = useGroupColor;
            this.updateAttributes();
        }

        public void addRecord(ShapefileRenderable.Record record)
        {
            if (this.attributes == null) // use the first record to access the default attributes
            {
                this.attributes = record.getAttributes().copy(); // use default attrs as a template
                this.defaultMaterial = record.getAttributes().getOutlineMaterial(); // save the default material
                this.updateAttributes();
            }

            record.setAttributes(this.attributes); // use the group's attributes as the record's normal attributes
        }

        protected void updateAttributes()
        {
            if (this.attributes != null)
            {
                this.attributes.setOutlineMaterial(this.useGroupColor ? this.groupMaterial : this.defaultMaterial);
            }
        }
    }

    public static void main(String[] args)
    {
        Configuration.setValue(AVKey.INITIAL_LATITUDE, 30);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, 30);
        start("WorldWind Shapefile Attribute Groups", AppFrame.class);
    }
}
