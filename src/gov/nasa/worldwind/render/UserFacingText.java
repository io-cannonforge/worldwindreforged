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
package gov.nasa.worldwind.render;

import java.awt.Color;
import java.awt.Font;

import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.util.Logging;

/**
 * @author dcollins
 * @version $Id: UserFacingText.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class UserFacingText implements GeographicText
{
    private CharSequence text;
    private Position textPosition;
    private Font textFont; // Can be null to indicate the default font.
    private Color textColor; // Can be null to indicate the default text color.
    private Color textBackgroundColor; // Can be null to indicate no background color.
    private boolean isVisible = true;
    double priority;  //used for label culling

    public UserFacingText(CharSequence text, Position textPosition)
    {
        if (text == null)
        {
            String message = Logging.getMessage("nullValue.CharSequenceIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.text = text;
        this.textPosition = textPosition;
    }

    @Override
	public CharSequence getText()
    {
        return this.text;
    }

    @Override
	public void setText(CharSequence text)
    {
        if (text == null)
        {
            String message = Logging.getMessage("nullValue.CharSequenceIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.text = text;
    }

    @Override
	public double getPriority()
    {
        return priority;
    }

    @Override
	public void setPriority(double priority)
    {
        this.priority = priority;
    }

    @Override
	public Position getPosition()
    {
        return this.textPosition;
    }

    @Override
	public void setPosition(Position position)
    {
        this.textPosition = position;
    }

    @Override
	public Font getFont()
    {
        return this.textFont;
    }

    @Override
	public void setFont(Font font)
    {
        this.textFont = font;
    }

    @Override
	public Color getColor()
    {
        return this.textColor;
    }

    @Override
	public void setColor(Color color)
    {
        this.textColor = color;
    }

    @Override
	public Color getBackgroundColor()
    {
        return this.textBackgroundColor;
    }

    @Override
	public void setBackgroundColor(Color background)
    {
        this.textBackgroundColor = background;
    }

    @Override
	public boolean isVisible()
    {
        return this.isVisible;
    }

    @Override
	public void setVisible(boolean visible)
    {
        this.isVisible = visible;
    }
}
