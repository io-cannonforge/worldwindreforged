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

import java.util.ArrayList;
import java.util.Set;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.ogc.OGCCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerCapabilities;
import gov.nasa.worldwind.ogc.wms.WMSLayerStyle;

/**
 * @author tag
 * @version $Id: WMSLayerInfo.java 1171 2013-02-11 21:45:02Z dcollins $
 */
// Modified by seaglassfoundry.com - added WMSLayerCapabilities retention for metadata display
public class WMSLayerInfo
{
    private OGCCapabilities caps;
    private WMSLayerCapabilities layerCaps;
    private AVListImpl params = new AVListImpl();

    public WMSLayerInfo(OGCCapabilities caps, WMSLayerCapabilities layerCaps, WMSLayerStyle style)
    {
        this.caps = caps;
        this.layerCaps = layerCaps;
        this.params = new AVListImpl();
        this.params.setValue(AVKey.LAYER_NAMES, layerCaps.getName());
        if (style != null)
            this.params.setValue(AVKey.STYLE_NAMES, style.getName());

        String layerTitle = layerCaps.getTitle();
        this.params.setValue(AVKey.DISPLAY_NAME, layerTitle);
    }

    public String getTitle()
    {
        return params.getStringValue(AVKey.DISPLAY_NAME);
    }

    public OGCCapabilities getCaps()
    {
        return caps;
    }

    public WMSLayerCapabilities getLayerCaps()
    {
        return layerCaps;
    }

    public AVListImpl getParams()
    {
        return params;
    }

    public static java.util.List<WMSLayerInfo> createLayerInfos(OGCCapabilities caps, WMSLayerCapabilities layerCaps)
    {
        // Create the layer info specified by the layer's capabilities entry and the selected style.
        ArrayList<WMSLayerInfo> layerInfos = new ArrayList<>();

        // An individual layer may have independent styles, and each layer/style combination is effectively one
        // visual layer. So here the individual layer/style combinations are formed.
        Set<WMSLayerStyle> styles = layerCaps.getStyles();
        if (styles == null || styles.size() == 0)
        {
            layerInfos.add(new WMSLayerInfo(caps, layerCaps, null));
        }
        else
        {
            for (WMSLayerStyle style : styles)
            {
                WMSLayerInfo layerInfo = new WMSLayerInfo(caps, layerCaps, style);
                layerInfos.add(layerInfo);
            }
        }

        return layerInfos;
    }
}
