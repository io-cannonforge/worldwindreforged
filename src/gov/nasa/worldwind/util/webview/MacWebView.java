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
package gov.nasa.worldwind.util.webview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;

import com.jogamp.opengl.util.texture.Texture;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.render.BasicWWTexture;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.WWTexture;
import gov.nasa.worldwind.util.Logging;

/**
 * @author dcollins
 * @version $Id: MacWebView.java 1171 2013-02-11 21:45:02Z dcollins $
 * @deprecated
 */
@Deprecated
public class MacWebView extends AbstractWebView
{
    /** The address of the native WebViewWindow object. Initialized during construction. */
    protected long webViewWindowPtr;

    public MacWebView(Dimension frameSize)
    {
        if (frameSize == null)
        {
            String message = Logging.getMessage("nullValue.SizeIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (!Configuration.isMacOS())
        {
            String message = Logging.getMessage("NativeLib.UnsupportedOperatingSystem", "Mac WebView",
                System.getProperty("os.name"));
            Logging.logger().severe(message);
            throw new UnsupportedOperationException(message);
        }

        this.frameSize = frameSize;
        this.webViewWindowPtr = MacWebViewJNI.allocWebViewWindow(this.frameSize);

        MacWebViewJNI.setPropertyChangeListener(this.webViewWindowPtr, this);
    }

    @Override
	public void dispose()
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.releaseWebViewWindow(this.webViewWindowPtr);
            this.webViewWindowPtr = 0;
        }
    }

    /** {@inheritDoc} */
    @Override
	public void setHTMLString(String htmlString)
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.setHTMLString(this.webViewWindowPtr, htmlString);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void setHTMLString(String htmlString, URL baseURL)
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.setHTMLStringWithBaseURL(this.webViewWindowPtr, htmlString, baseURL);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void setHTMLString(String htmlString, WebResourceResolver resourceResolver)
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.setHTMLStringWithResourceResolver(this.webViewWindowPtr, htmlString, resourceResolver);
        }
    }

    @Override
	protected void doSetFrameSize(Dimension size)
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.setFrameSize(this.webViewWindowPtr, size);
        }
    }

    /** {@inheritDoc} */
    @Override
	public Dimension getContentSize()
    {
        if (this.webViewWindowPtr != 0)
        {
            return MacWebViewJNI.getContentSize(this.webViewWindowPtr);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
	public Dimension getMinContentSize()
    {
        if (this.webViewWindowPtr != 0)
        {
            return MacWebViewJNI.getMinContentSize(this.webViewWindowPtr);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
	public void setMinContentSize(Dimension size)
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.setMinContentSize(this.webViewWindowPtr, size);
        }
    }

    /** {@inheritDoc} */
    @Override
	public URL getContentURL()
    {
        if (this.webViewWindowPtr != 0)
        {
            return MacWebViewJNI.getContentURL(this.webViewWindowPtr);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
	public Iterable<AVList> getLinks()
    {
        if (this.webViewWindowPtr != 0)
        {
            AVList[] linkParams = MacWebViewJNI.getLinks(this.webViewWindowPtr);
            if (linkParams != null)
                return Arrays.asList(linkParams);
        }

        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
	public void sendEvent(InputEvent event)
    {
        if (this.webViewWindowPtr != 0 && event != null)
        {
            MacWebViewJNI.sendEvent(this.webViewWindowPtr, event);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void goBack()
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.goBack(this.webViewWindowPtr);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void goForward()
    {
        if (this.webViewWindowPtr != 0)
        {
            MacWebViewJNI.goForward(this.webViewWindowPtr);
        }
    }

    /**
     * Not implemented. MacWebView generates transparent WebView textures, so setting a background color is not
     * necessary. The texture can be drawn over the desired background color.
     */
    @Override
	public void setBackgroundColor(Color color)
    {
        // Do nothing
    }

    /**
     * Not implemented. MacWebView generates transparent WebView textures, so setting a background color is not
     * necessary. The texture can be drawn over the desired background color.
     */
    @Override
	public Color getBackgroundColor()
    {
        return null;
    }

    //**********************************************************************//
    //********************  Texture Representation  ************************//
    //**********************************************************************//

    @Override
    protected WWTexture createTextureRepresentation(DrawContext dc)
    {
        BasicWWTexture texture = new MacWebViewTexture(this.getFrameSize(), false);
        texture.setUseAnisotropy(false); // Do not use anisotropic texture filtering.

        return texture;
    }

    protected class MacWebViewTexture extends WebViewTexture
    {
        /**
         * Indicates whether updating this <code>WebViewTexture's</code> OpenGL texture has failed. When
         * <code>true</code>, this <code>WebViewTexture's</code> stops attempting to update its texture. Initially
         * <code>false</code>.
         */
        protected boolean textureUpdateFailed;

        public MacWebViewTexture(Dimension frameSize, boolean useMipMaps)
        {
            super(frameSize, useMipMaps, true);
        }

        @Override
        protected void updateIfNeeded(DrawContext dc)
        {
            if (this.textureUpdateFailed)
                return;

            // Return immediately if the texture isn't in the texture cache, and wait to update until the texture is
            // initialized and placed in the cache. This method is called after the texture is bound, so we'll get
            // another chance to update as long as the WebView generates repaint events when it changes.
            Texture texture = this.getTextureFromCache(dc);
            if (texture == null)
                return;

            try
            {
                this.displayInTexture(dc, texture);
            }
            catch (Exception e)
            {
                // Log an exception indicating that updating the texture failed, but do not re-throw it. This is called
                // from within the rendering loop, and we want to avoid causing any other rendering code to fail.
                Logging.logger().log(Level.SEVERE, Logging.getMessage("WebView.ExceptionUpdatingTexture"), e);
                // Indicate that updating this WebViewTexture's OpenGL texture failed to avoid subsequent attempts.
                this.textureUpdateFailed = true;
            }
        }

        @SuppressWarnings("unused")
        protected void displayInTexture(DrawContext dc, Texture texture)
        {
            // Return immediately if the native WebViewWindow has been released. This indicates the MacWebView has been
            // disposed, so there's nothing to do.
            long webViewWindowPtr = MacWebView.this.webViewWindowPtr;
            if (webViewWindowPtr == 0)
                return;

            // Load the WebViewWindow's current display pixels into the currently bound OGL texture if the native
            // WebView indicates that the display has changed since our last call to displayInTexture.
            if (MacWebViewJNI.mustDisplayInTexture(webViewWindowPtr))
            {
                MacWebViewJNI.displayInTexture(webViewWindowPtr, texture.getTarget());
            }
        }
    }
}
