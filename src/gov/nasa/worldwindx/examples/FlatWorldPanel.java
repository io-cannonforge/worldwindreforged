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

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.globes.FlatGlobe;
import gov.nasa.worldwind.globes.GeographicProjection;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.globes.projections.ProjectionEquirectangular;
import gov.nasa.worldwind.globes.projections.ProjectionMercator;
import gov.nasa.worldwind.globes.projections.ProjectionModifiedSinusoidal;
import gov.nasa.worldwind.globes.projections.ProjectionPolarEquidistant;
import gov.nasa.worldwind.globes.projections.ProjectionSinusoidal;
import gov.nasa.worldwind.globes.projections.ProjectionTransverseMercator;
import gov.nasa.worldwind.globes.projections.ProjectionUPS;
import gov.nasa.worldwind.terrain.ZeroElevationModel;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * Panel to control a flat or round world projection. The panel includes a radio button to switch between flat and round
 * globes, and a list box of map projections for the flat globe. The panel is attached to a WorldWindow, and changes the
 * WorldWindow to match the users globe selection.
 *
 * @author Patrick Murris
 * @version $Id: FlatWorldPanel.java 2419 2014-11-08 04:44:55Z tgaskins $
 */
public class FlatWorldPanel extends JPanel
{
    private static final long serialVersionUID = 1L;
    private WorldWindow wwd;
    private Globe roundGlobe;
    private FlatGlobe flatGlobe;
    // Modified by seaglassfoundry.com - added generic type parameters
    private JComboBox<String> projectionCombo;

    public FlatWorldPanel(WorldWindow wwd)
    {
        super(new GridLayout(0, 2, 0, 0));
        this.wwd = wwd;
        if (isFlatGlobe())
        {
            this.flatGlobe = (FlatGlobe) wwd.getModel().getGlobe();
            this.roundGlobe = new Earth();
        }
        else
        {
            this.flatGlobe = new EarthFlat();
            this.roundGlobe = wwd.getModel().getGlobe();
        }
        this.flatGlobe.setElevationModel(new ZeroElevationModel());
        this.makePanel();
    }

    private JPanel makePanel()
    {
        JPanel controlPanel = this;
        controlPanel.setBorder(WWStyle.sectionBorder("Globe"));
        controlPanel.setToolTipText("Set the current projection");

        // Flat vs round buttons
        JPanel radioButtonPanel = new JPanel(new GridLayout(0, 2, 0, 0));
        JRadioButton roundRadioButton = new JRadioButton("Round");
        roundRadioButton.setSelected(!isFlatGlobe());
        roundRadioButton.addActionListener(new ActionListener()
        {
            @Override
			public void actionPerformed(ActionEvent event)
            {
                projectionCombo.setEnabled(false);
                enableFlatGlobe(false);
            }
        });
        radioButtonPanel.add(roundRadioButton);
        JRadioButton flatRadioButton = new JRadioButton("Flat");
        flatRadioButton.setSelected(isFlatGlobe());
        flatRadioButton.addActionListener(new ActionListener()
        {
            @Override
			public void actionPerformed(ActionEvent event)
            {
                projectionCombo.setEnabled(true);
                enableFlatGlobe(true);
            }
        });
        radioButtonPanel.add(flatRadioButton);
        ButtonGroup group = new ButtonGroup();
        group.add(roundRadioButton);
        group.add(flatRadioButton);

        // Projection combo
        JPanel comboPanel = new JPanel(new GridLayout(0, 1, 0, 0));
        this.projectionCombo = new JComboBox<>(new String[]
            {"Lat-Lon", "Mercator", "Modified Sin.", "Sinusoidal",
                "Transverse Mercator",
                "North Polar",
                "South Polar",
                "UPS North",
                "UPS South"
            });
        this.projectionCombo.setEnabled(isFlatGlobe());
        this.projectionCombo.addActionListener(new ActionListener()
        {
            @Override
			public void actionPerformed(ActionEvent actionEvent)
            {
                updateProjection();
            }
        });
        comboPanel.add(this.projectionCombo);

        controlPanel.add(radioButtonPanel);
        controlPanel.add(comboPanel);
        return controlPanel;
    }

    // Update flat globe projection
    private void updateProjection()
    {
        if (!isFlatGlobe())
            return;

        this.flatGlobe.setProjection(this.getProjection());

        this.wwd.redraw();
    }

    private GeographicProjection getProjection()
    {
        String item = (String) projectionCombo.getSelectedItem();
        if (item.equals("Mercator"))
            return new ProjectionMercator();
        else if (item.equals("Sinusoidal"))
            return new ProjectionSinusoidal();
        else if (item.equals("Modified Sin."))
            return new ProjectionModifiedSinusoidal();
        else if (item.equals("Transverse Mercator"))
            return new ProjectionTransverseMercator(wwd.getView().getCurrentEyePosition().getLongitude());
        else if (item.equals("North Polar"))
            return new ProjectionPolarEquidistant(AVKey.NORTH);
        else if (item.equals("South Polar"))
            return new ProjectionPolarEquidistant(AVKey.SOUTH);
        else if (item.equals("UPS North"))
            return new ProjectionUPS(AVKey.NORTH);
        else if (item.equals("UPS South"))
            return new ProjectionUPS(AVKey.SOUTH);
        // Default to lat-lon
        return new ProjectionEquirectangular();
    }

    public boolean isFlatGlobe()
    {
        return wwd.getModel().getGlobe() instanceof FlatGlobe;
    }

    public void enableFlatGlobe(boolean flat)
    {
        if (isFlatGlobe() == flat)
            return;

        if (!flat)
        {
            // Switch to round globe
            wwd.getModel().setGlobe(roundGlobe);
            wwd.getView().stopMovement();
        }
        else
        {
            // Switch to flat globe
            wwd.getModel().setGlobe(flatGlobe);
            wwd.getView().stopMovement();
            this.updateProjection();
        }

        wwd.redraw();
    }
}
