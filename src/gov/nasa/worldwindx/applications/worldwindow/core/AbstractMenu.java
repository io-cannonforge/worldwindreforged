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

package gov.nasa.worldwindx.applications.worldwindow.core;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import gov.nasa.worldwindx.applications.worldwindow.features.Feature;

/**
 * @author tag
 * @version $Id: AbstractMenu.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class AbstractMenu extends JMenu implements Menu, MenuListener, WWMenu
{
    private static final long serialVersionUID = 1L;
    protected Controller controller;

    protected AbstractMenu(String title, String menuID, Registry registry)
    {
        super(title);

        if (menuID != null && registry != null)
            registry.registerObject(menuID, this);
    }

    @Override
	public void initialize(Controller controller)
    {
        this.controller = controller;

        this.addMenuListener(this);
    }

    @Override
	public boolean isInitialized()
    {
        return this.controller != null;
    }

    protected void addToMenuBar()
    {
        if (this.controller.getMenuBar() != null)
            this.controller.getMenuBar().addMenu(this);
    }

    @Override
	public void addMenu(String featureID)
    {
        this.addMenus(new String[] {featureID});
    }

    @Override
	public void addMenus(String[] featureIDs)
    {
        boolean radioGroup = false;

        for (String featureID : featureIDs)
        {
            if (featureID == null || featureID.length() == 0)
            {
                radioGroup = false;
                this.add(new JSeparator());
            }
            else if (featureID.equals(Constants.RADIO_GROUP))
            {
                radioGroup = true;
            }
            else if (featureID.startsWith("gov.nasa.worldwindx.applications.worldwindow.menu"))
            {
                Object o = this.controller.getRegisteredObject(featureID);
                if (o instanceof Menu)
                    this.add(((Menu) o).getJMenu());
            }
            else
            {
                Feature feature = (Feature) controller.getRegisteredObject(featureID);
                if (feature != null)
                {
                    if (feature.isTwoState())
                    {
                        if (radioGroup)
                        {
                            final JMenuItem menuItem = new RadioMenuItem(feature);

                            this.add(menuItem);
                            feature.addPropertyChangeListener(new PropertyChangeListener()
                            {
                                @Override
								public void propertyChange(PropertyChangeEvent event)
                                {
                                    if (event.getPropertyName().equals(Constants.ON_STATE))
                                    {
                                        menuItem.setSelected((Boolean) event.getNewValue());
                                        menuItem.repaint();
                                    }
                                }
                            });
                        }
                        else
                        {
                            this.add(new ToggleMenuItem(feature));
                        }
                    }
                    else
                    {
                        radioGroup = false;
                        this.add(new JMenuItem(feature));
                    }
                }
            }
        }
    }

    @Override
	public void menuSelected(MenuEvent menuEvent)
    {
        for (int i = 0; i < this.getItemCount(); i++)
        {
            JMenuItem mi = this.getItem(i);
            if (mi instanceof ToggleMenuItem tmi)
            {
                Feature feature = (Feature) tmi.getAction();
                tmi.setState(feature.isOn());
            }
        }
    }

    @Override
	public void menuDeselected(MenuEvent menuEvent)
    {
    }

    @Override
	public void menuCanceled(MenuEvent menuEvent)
    {
    }

    protected Controller getController()
    {
        return controller;
    }

    @Override
	public JMenu getJMenu()
    {
        return this;
    }

    @SuppressWarnings("serial")
    private static class ToggleMenuItem extends JCheckBoxMenuItem
    {
        public ToggleMenuItem(Action action)
        {
            super(action);
        }
    }

    @SuppressWarnings("serial")
    private static class RadioMenuItem extends JRadioButtonMenuItem
    {
        public RadioMenuItem(Action action)
        {
            super(action);
        }
    }

    protected List<Feature> getFeatures()
    {
        ArrayList<Feature> featureList = new ArrayList<>();

        for (int i = 0; i < this.getItemCount(); i++)
        {
            Object o = this.getItem(i) != null ? this.getItem(i).getAction() : null;
            if (o != null && o instanceof Feature)
                featureList.add((Feature) o);
        }

        return featureList;
    }
}
