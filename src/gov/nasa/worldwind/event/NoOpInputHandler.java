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

package gov.nasa.worldwind.event;

import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

import gov.nasa.worldwind.WWObjectImpl;
import gov.nasa.worldwind.WorldWindow;

/**
 * Provides an input handler that does nothing. Meant to serve as a NULL assignment that can be invoked.
 *
 * @author tag
 * @version $Id: NoOpInputHandler.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class NoOpInputHandler extends WWObjectImpl implements InputHandler
{
    @Override
	public void setEventSource(WorldWindow newWorldWindow)
    {
    }

    @Override
	public WorldWindow getEventSource()
    {
        return null;
    }

    @Override
	public void setHoverDelay(int delay)
    {
    }

    @Override
	public int getHoverDelay()
    {
        return 0;
    }

    @Override
	public void addSelectListener(SelectListener listener)
    {
    }

    @Override
	public void removeSelectListener(SelectListener listener)
    {
    }

    @Override
	public void addKeyListener(KeyListener listener)
    {
    }

    @Override
	public void removeKeyListener(KeyListener listener)
    {
    }

    @Override
	public void addMouseListener(MouseListener listener)
    {
    }

    @Override
	public void removeMouseListener(MouseListener listener)
    {
    }

    @Override
	public void addMouseMotionListener(MouseMotionListener listener)
    {
    }

    @Override
	public void removeMouseMotionListener(MouseMotionListener listener)
    {
    }

    @Override
	public void addMouseWheelListener(MouseWheelListener listener)
    {
    }

    @Override
	public void removeMouseWheelListener(MouseWheelListener listener)
    {
    }

    @Override
	public void dispose()
    {
    }

    @Override
	public boolean isForceRedrawOnMousePressed()
    {
        return false;
    }

    @Override
	public void setForceRedrawOnMousePressed(boolean forceRedrawOnMousePressed)
    {
    }
}
