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

import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

/**
 * Determines whether a device supports the OpenGL features necessary for WorldWind.
 *
 * @author tag
 * @version $Id: OpenGLSupportTest.java 1675 2013-10-18 00:32:15Z tgaskins $
 */
public class OpenGLSupportTest implements GLEventListener
{
    private Frame parentFrame;

    @Override
    public void init(GLAutoDrawable glAutoDrawable)
    {
        List<String> issues = new ArrayList<>();

        for (String funcName : this.getRequiredOglFunctions())
        {
            if (!glAutoDrawable.getGL().isFunctionAvailable(funcName))
            {
                String msg = "OpenGL function " + funcName + " is not available.";
                System.out.println(msg);
                issues.add(msg);
            }
        }

        for (String extName : this.getRequiredOglExtensions())
        {
            if (!glAutoDrawable.getGL().isExtensionAvailable(extName))
            {
                String msg = "OpenGL extension " + extName + " is not available.";
                System.out.println(msg);
                issues.add(msg);
            }
        }

        GLCapabilitiesImmutable caps = glAutoDrawable.getChosenGLCapabilities();
        if (caps.getAlphaBits() != 8 || caps.getRedBits() != 8 || caps.getGreenBits() != 8 || caps.getBlueBits() != 8)
        {
            String msg = "Device canvas color depth is inadequate.";
            System.out.println(msg);
            issues.add(msg);
        }

        if (caps.getDepthBits() < 16)
        {
            String msg = "Device canvas depth buffer depth is inadequate.";
            System.out.println(msg);
            issues.add(msg);
        }

        if (!caps.getDoubleBuffered())
        {
            String msg = "Device canvas is not double buffered.";
            System.out.println(msg);
            issues.add(msg);
        }

        if (Boolean.getBoolean("gov.nasa.worldwind.examplesLauncher"))
        {
            String result = issues.isEmpty()
                ? "All OpenGL support tests passed."
                : "Issues found:\n" + String.join("\n", issues);
            java.awt.EventQueue.invokeLater(() -> {
                JOptionPane.showMessageDialog(parentFrame, result, "OpenGL Support Test",
                    issues.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                if (parentFrame != null)
                    parentFrame.dispose();
            });
        }
        else
        {
            System.exit(issues.isEmpty() ? 0 : 1);
        }
    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable)
    {
    }

    @Override
    public void display(GLAutoDrawable glAutoDrawable)
    {
    }

    @Override
    public void reshape(GLAutoDrawable glAutoDrawable, int i, int i1, int i2, int i3)
    {
    }

    protected String[] getRequiredOglFunctions()
    {
        return new String[] {"glActiveTexture", "glClientActiveTexture"};
    }

    protected String[] getRequiredOglExtensions()
    {
        return new String[] {"GL_EXT_texture_compression_s3tc"};
    }

    public static void main(String[] args)
    {
        Frame frame = new Frame("OpenGL Support Test");
        frame.setSize(200, 200);
        frame.setLayout(new java.awt.BorderLayout());

        GLCapabilities caps = new GLCapabilities(GLProfile.getMaxFixedFunc(true));

        caps.setAlphaBits(8);
        caps.setRedBits(8);
        caps.setGreenBits(8);
        caps.setBlueBits(8);
        caps.setDepthBits(24);
        caps.setDoubleBuffered(true);
        GLCanvas canvas = new GLCanvas(caps);

        OpenGLSupportTest testClass = new OpenGLSupportTest();
        testClass.parentFrame = frame;
        canvas.addGLEventListener(testClass);

        frame.add(canvas, java.awt.BorderLayout.CENTER);
        frame.validate();
        frame.setVisible(true);
    }
}
