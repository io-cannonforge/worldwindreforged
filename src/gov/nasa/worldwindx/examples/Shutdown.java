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
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;

/**
 * Shows how to shut down a {@link WorldWindow} and how to shut down all of WorldWind.
 *
 * @author tag
 * @version $Id: Shutdown.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class Shutdown
{
    private static class AppFrame extends javax.swing.JFrame
    {
        private static final long serialVersionUID = 1L;
        private WorldWindow wwd;
        private ShutdownWindowAction shutdownAction;
        private CreateWindowAction createWindowAction;

        public AppFrame()
        {
            this.getContentPane().setLayout(new BorderLayout(10, 10));

            JPanel controlPanel = new JPanel(new BorderLayout(10, 10));
            this.getContentPane().add(controlPanel, BorderLayout.WEST);

            JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 10, 10));
            controlPanel.add(buttonPanel, BorderLayout.NORTH);

            this.shutdownAction = new ShutdownWindowAction();
            buttonPanel.add(new JButton(this.shutdownAction));
            this.shutdownAction.setEnabled(true);

            this.createWindowAction = new CreateWindowAction();
            buttonPanel.add(new JButton(this.createWindowAction));

            buttonPanel.add(new JButton(new ShutdownWorldWindAction()));

            this.createWindow();

            this.pack();
        }

        private void createWindow()
        {
            WorldWindowGLCanvas wwc = new WorldWindowGLCanvas();
            wwc.setPreferredSize(new java.awt.Dimension(800, 600));
            this.getContentPane().add(wwc, java.awt.BorderLayout.CENTER);
            wwc.setModel(new BasicModel());
            this.wwd = wwc;
        }

        private void destroyCurrentWindow()
        {
            if (this.wwd != null)
            {
                getContentPane().remove((Component) wwd);
                wwd = null;
            }
        }

        @SuppressWarnings("serial")
        private class ShutdownWindowAction extends AbstractAction
        {
            public ShutdownWindowAction()
            {
                super("Shutdown Window");
            }

            @Override
			public void actionPerformed(ActionEvent e)
            {
                if (wwd != null)
                {
                    wwd.shutdown();
                    destroyCurrentWindow();
                    this.setEnabled(false);
                    createWindowAction.setEnabled(true);
                }
            }
        }

        private class CreateWindowAction extends AbstractAction
        {
            private static final long serialVersionUID = 1L;
            public CreateWindowAction()
            {
                super("Create Window");
                this.setEnabled(false);
            }

            @Override
			public void actionPerformed(ActionEvent e)
            {
                createWindow();
                pack();
                this.setEnabled(false);
                shutdownAction.setEnabled(true);
            }
        }

        @SuppressWarnings("serial")
        private class ShutdownWorldWindAction extends AbstractAction
        {
            public ShutdownWorldWindAction()
            {
                super("Shutdown WorldWind");
            }

            @Override
			public void actionPerformed(ActionEvent e)
            {
                WorldWind.shutDown();
                destroyCurrentWindow();
                createWindowAction.setEnabled(true);
            }
        }
    }

    public static void main(String[] args)
    {
        if (Configuration.isMacOS())
        {
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Shutdown WorldWind");
        }

        java.awt.EventQueue.invokeLater(new Runnable()
        {
            @Override
			public void run()
            {
                // Create an AppFrame and immediately make it visible. As per Swing convention, this
                // is done within an invokeLater call so that it executes on an AWT thread.
                AppFrame appFrame = new AppFrame();
                appFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                appFrame.setVisible(true);
            }
        });
    }
}
