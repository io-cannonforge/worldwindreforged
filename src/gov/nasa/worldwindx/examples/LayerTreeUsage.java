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

import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.layers.CompassLayer;
import gov.nasa.worldwind.layers.Earth.BMNGOneImage;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.layers.ScalebarLayer;
import gov.nasa.worldwind.layers.SkyGradientLayer;
import gov.nasa.worldwind.layers.StarsLayer;
import gov.nasa.worldwind.layers.TiledImageLayer;
import gov.nasa.worldwind.layers.WorldMapLayer;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwind.util.layertree.LayerTree;
import gov.nasa.worldwind.util.layertree.LayerTreeNode;
import gov.nasa.worldwind.util.tree.BasicTreeNode;
import gov.nasa.worldwindx.examples.util.HotSpotController;

/**
 * Example of using {@link gov.nasa.worldwind.util.tree.BasicTree} to display a hierarchical layer tree with
 * expandable groups. Layers are automatically categorised into Background, Imagery, Overlays, and Controls
 * groups, demonstrating how to build a real tree rather than a flat list.
 *
 * <p>Modified by seaglassfoundry.com &mdash; reorganised from flat list into grouped hierarchy.</p>
 *
 * @author pabercrombie
 * @version $Id: LayerTreeUsage.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class LayerTreeUsage extends ApplicationTemplate
{
    /** Group names used to organise layers in the tree. */
    protected static final String GROUP_BACKGROUND = "Background";
    protected static final String GROUP_IMAGERY    = "Imagery";
    protected static final String GROUP_OVERLAYS   = "Overlays";
    protected static final String GROUP_CONTROLS   = "Controls";

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        protected LayerTree layerTree;
        protected RenderableLayer hiddenLayer;

        protected HotSpotController controller;

        public AppFrame()
        {
            super(true, false, false); // Don't include the layer panel; we're using the on-screen layer tree.

            this.layerTree = new LayerTree();

            // Set up a layer to display the on-screen layer tree in the WorldWindow.
            this.hiddenLayer = new RenderableLayer();
            this.hiddenLayer.addRenderable(this.layerTree);
            this.getWwd().getModel().getLayers().add(this.hiddenLayer);

            // Mark the layer as hidden to prevent it being included in the layer tree's model.
            this.hiddenLayer.setValue(AVKey.HIDDEN, true);

            // Build a grouped tree instead of a flat list.
            this.buildGroupedTree();

            // Expand all group nodes so the full hierarchy is visible on startup.
            this.expandAllGroups();

            // Add a controller to handle input events on the layer tree.
            this.controller = new HotSpotController(this.getWwd());

            // Size the WorldWindow to take up the space typically used by the layer panel.
            Dimension size = new Dimension(1000, 600);
            this.setPreferredSize(size);
            this.pack();
            WWUtil.alignComponent(null, this, AVKey.CENTER);
        }

        /**
         * Classifies each layer into a named group and builds a tree with group nodes as parents
         * and layer nodes as children.
         */
        protected void buildGroupedTree()
        {
            // Use a linked map so groups appear in insertion order.
            Map<String, BasicTreeNode> groups = new LinkedHashMap<>();
            groups.put(GROUP_BACKGROUND, new BasicTreeNode(GROUP_BACKGROUND));
            groups.put(GROUP_IMAGERY,    new BasicTreeNode(GROUP_IMAGERY));
            groups.put(GROUP_OVERLAYS,   new BasicTreeNode(GROUP_OVERLAYS));
            groups.put(GROUP_CONTROLS,   new BasicTreeNode(GROUP_CONTROLS));

            for (Layer layer : this.getWwd().getModel().getLayers())
            {
                if (layer.getValue(AVKey.HIDDEN) == Boolean.TRUE)
                    continue;

                String group = classifyLayer(layer);
                BasicTreeNode groupNode = groups.get(group);
                groupNode.addChild(new LayerTreeNode(layer));
            }

            // Attach only non-empty groups to the tree root.
            this.layerTree.getModel().removeAllLayers();
            for (BasicTreeNode groupNode : groups.values())
            {
                if (groupNode.getChildren().iterator().hasNext())
                    this.layerTree.getModel().getRoot().addChild(groupNode);
            }
        }

        /** Expands every group path so the tree starts fully open. */
        protected void expandAllGroups()
        {
            for (var child : this.layerTree.getModel().getRoot().getChildren())
                this.layerTree.expandPath(child.getPath());
        }
    }

    /**
     * Determines which group a layer belongs to based on its type.
     *
     * @param layer the layer to classify.
     * @return one of the GROUP_* constants.
     */
    protected static String classifyLayer(Layer layer)
    {
        // Atmosphere and single-image background layers.
        if (layer instanceof StarsLayer || layer instanceof SkyGradientLayer
            || layer instanceof BMNGOneImage)
            return GROUP_BACKGROUND;

        // HUD / navigation controls.
        if (layer instanceof CompassLayer || layer instanceof ScalebarLayer
            || layer instanceof WorldMapLayer)
            return GROUP_CONTROLS;

        // Distinguish imagery base maps from thematic overlays by name.
        // Overlays are layers whose content is informational rather than base imagery.
        String name = layer.getName() != null ? layer.getName().toLowerCase() : "";
        if (name.contains("boundar") || name.contains("place") || name.contains("night"))
            return GROUP_OVERLAYS;

        // Remaining TiledImageLayers (BMNG, Landsat, NAIP, Bing, etc.) are imagery.
        if (layer instanceof TiledImageLayer)
            return GROUP_IMAGERY;

        // Anything else falls into overlays.
        return GROUP_OVERLAYS;
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Layer Tree", AppFrame.class);
    }
}
