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

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.ScreenImage;

/**
 * This example demonstrates the use of the {@link gov.nasa.worldwind.render.ScreenImage} class, and shows how to use it
 * to create an image that can be dragged around the screen using the mouse.
 *
 * @author tag
 * @version $Id: ScreenImageDragging.java 2109 2014-06-30 16:52:38Z tgaskins $
 */
public class ScreenImageDragging extends ApplicationTemplate
{
    private static class AppFrame extends ApplicationTemplate.AppFrame
    {
        private static final long serialVersionUID = 1L;
        // Modified by seaglassfoundry.com - suppressed unused warning (constructor used by ApplicationTemplate.start())
        @SuppressWarnings("unused")
        public AppFrame() throws IOException, ParserConfigurationException, SAXException
        {
            super(true, true, false);

            // Create a screen image and containing layer for the image/icon
            final ScreenImage screenImage = new ScreenImage();
            screenImage.setImageSource(ImageIO.read(new File("src/images/32x32-icon-nasa.png")));

            RenderableLayer layer = new RenderableLayer();
            layer.setName("Screen Image");
            layer.addRenderable(screenImage);

            this.getWwd().getModel().getLayers().add(layer);

            // Tell the input handler to pass mouse events here
            this.getWwd().getInputHandler().addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
				public void mouseDragged(MouseEvent event)
                {
                    // Update the layer's image location
                    screenImage.setScreenLocation(event.getPoint());
                    event.consume(); // tell the input handler that we've handled the event
                }
            });
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("Screen Image Dragging", AppFrame.class);
    }
}
