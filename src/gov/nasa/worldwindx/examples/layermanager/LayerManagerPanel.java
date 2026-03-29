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

package gov.nasa.worldwindx.examples.layermanager;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.TiledImageLayer;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Displays the available layers. Provides an interface to enable and disable them. Provides an interface to change
 * their relative order. Indicates when any portion of an image layer is actually rendered.
 *
 * @author tag
 * @version $Id: LayerManagerPanel.java 2147 2014-07-11 23:29:45Z tgaskins $
 */
@SuppressWarnings("serial")
public class LayerManagerPanel extends JPanel
{
    protected JPanel layerNamesPanel;
    protected List<LayerPanel> layerPanels = new ArrayList<>();
    protected Font plainFont;
    protected Font boldFont;

    public LayerManagerPanel(final WorldWindow wwd)
    {
        super(new BorderLayout(0, 0));
        this.setBackground(WWStyle.BG_DARK);

        this.layerNamesPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        this.layerNamesPanel.setBackground(WWStyle.BG_DARK);
        this.layerNamesPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Must put the layer grid in a container to prevent the scroll pane from stretching vertical spacing.
        JPanel dummyPanel = new JPanel(new BorderLayout());
        dummyPanel.setBackground(WWStyle.BG_DARK);
        dummyPanel.add(this.layerNamesPanel, BorderLayout.NORTH);

        // Styled scroll pane — thin scrollbar, no visible border.
        JScrollPane scrollPane = WWStyle.scrollPane(dummyPanel);

        // Titled panel using the unified section border.
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBorder(WWStyle.sectionBorder("Layers"));
        titlePanel.setBackground(WWStyle.BG_DARK);
        titlePanel.setToolTipText("Layers to Show");
        titlePanel.add(scrollPane, BorderLayout.CENTER);
        this.add(titlePanel, BorderLayout.CENTER);

        this.fill(wwd);

        this.plainFont = WWStyle.FONT_BASE;
        this.boldFont = WWStyle.FONT_BOLD;

        // Register a rendering listener that updates the was-rendered state of each image layer.
        wwd.addRenderingListener(new RenderingListener()
        {
            @Override
            public void stageChanged(RenderingEvent event)
            {
                updateLayerActivity(wwd);
            }
        });

        // Add a property change listener that causes this layer panel to be updated whenever the layer list changes.
        wwd.getModel().getLayers().addPropertyChangeListener(new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent propertyChangeEvent)
            {
                if (propertyChangeEvent.getPropertyName().equals(AVKey.LAYERS))
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        @Override
						public void run()
                        {
                            update(wwd);
                        }
                    });
            }
        });
    }

    public void update(WorldWindow wwd)
    {
        // Repopulate this layer manager.

        this.fill(wwd);
    }

    protected void fill(WorldWindow wwd)
    {
        // Populate this layer manager with an entry for each layer in the WorldWindow's layer list.

        if (this.isUpToDate(wwd))
            return;

        // First remove all the existing entries.
        this.layerPanels.clear();
        this.layerNamesPanel.removeAll();

        // Fill the layers panel with the titles of all layers in the WorldWindow's current model.
        for (Layer layer : wwd.getModel().getLayers())
        {
            if (layer.getValue(AVKey.IGNORE) != null)
                continue;

            LayerPanel layerPanel = new LayerPanel(wwd, layer);
            this.layerNamesPanel.add(layerPanel);
            this.layerPanels.add(layerPanel);
        }

        this.updateLayerActivity(wwd);
    }

    protected boolean isUpToDate(WorldWindow wwd)
    {
        // Determines whether this layer manager's layer list is consistent with the specified WorldWindow's. Knowing
        // this prevents redundant updates.

        LayerList layerList = wwd.getModel().getLayers();

        if (this.layerPanels.size() != layerList.size())
            return false;

        for (int i = 0; i < layerList.size(); i++)
        {
            if (layerList.get(i) != this.layerPanels.get(i).getLayer())
                return false;
        }

        return true;
    }

    /**
     * Loops through this layer panel's layer/checkbox list and updates the checkbox font to indicate whether the
     * corresponding layer was just rendered. This method is called by a rendering listener -- see comment below.
     *
     * @param wwd the WorldWindow.
     */
    protected void updateLayerActivity(WorldWindow wwd)
    {
        for (LayerPanel layerPanel : this.layerPanels)
        {
            // The frame timestamp from the layer indicates the last frame in which it rendered something. If that
            // timestamp matches the current timestamp of the scene controller, then the layer rendered something
            // during the most recent frame. Note that this frame timestamp protocol is only in place by default
            // for TiledImageLayer and its subclasses. Applications could, however, implement it for the layers
            // they design.

            Long layerTimeStamp = (Long) layerPanel.getLayer().getValue(AVKey.FRAME_TIMESTAMP);
            Long frameTimeStamp = (Long) wwd.getSceneController().getValue(AVKey.FRAME_TIMESTAMP);

            if (layerTimeStamp != null && frameTimeStamp != null
                && layerTimeStamp.longValue() == frameTimeStamp.longValue())
            {
                // Set the font to bold if the layer was just rendered.
                layerPanel.setLayerNameFont(this.boldFont);
            }
            else if (layerPanel.getLayer() instanceof TiledImageLayer)
            {
                // Set the font to plain if the layer was not just rendered.
                layerPanel.setLayerNameFont(this.plainFont);
            }
            else if (layerPanel.getLayer().isEnabled())
            {
                // Set enabled layer types other than TiledImageLayer to bold.
                layerPanel.setLayerNameFont(this.boldFont);
            }
            else if (!layerPanel.getLayer().isEnabled())
            {
                // Set disabled layer types other than TiledImageLayer to plain.
                layerPanel.setLayerNameFont(this.plainFont);
            }
        }
    }
}