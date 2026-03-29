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

// Created by seaglassfoundry.com - WMS server list persistence using XML file storage

package gov.nasa.worldwindx.applications.worldwindow.features;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWXML;

/**
 * Manages persistence of WMS server URLs to an XML file in the WorldWind data directory.
 * Servers are saved as name/URL pairs and restored when the WMS dialog opens.
 */
public class WMSServerPersistence
{
    private static final String FILE_NAME = "wms-servers.xml";
    private static final String ROOT_ELEMENT = "WMSServers";
    private static final String SERVER_ELEMENT = "Server";
    private static final String NAME_ATTR = "name";
    private static final String URL_ATTR = "url";

    /**
     * Represents a saved WMS server entry.
     */
    public static class ServerEntry
    {
        public final String name;
        public final String url;

        public ServerEntry(String name, String url)
        {
            this.name = name;
            this.url = url;
        }
    }

    /**
     * Returns the persistence file path.
     */
    private static File getFile()
    {
        File writeDir = WorldWind.getDataFileStore().getWriteLocation();
        return new File(writeDir, FILE_NAME);
    }

    /**
     * Loads the saved server list from the XML file.
     *
     * @return list of saved server entries, empty list if file doesn't exist or is corrupt
     */
    public static List<ServerEntry> load()
    {
        List<ServerEntry> servers = new ArrayList<>();

        File file = getFile();
        if (!file.exists())
            return servers;

        try
        {
            Document doc = WWXML.openDocumentFile(file.getAbsolutePath(), null);
            if (doc == null)
                return servers;

            NodeList serverNodes = doc.getElementsByTagName(SERVER_ELEMENT);
            for (int i = 0; i < serverNodes.getLength(); i++)
            {
                Element elem = (Element) serverNodes.item(i);
                String name = elem.getAttribute(NAME_ATTR);
                String url = elem.getAttribute(URL_ATTR);
                if (url != null && !url.isEmpty())
                    servers.add(new ServerEntry(name, url));
            }
        }
        catch (Exception e)
        {
            Logging.logger().log(Level.WARNING, "Failed to load WMS server list, starting fresh", e);
            // Delete corrupt file
            try { file.delete(); } catch (Exception ignored) {}
        }

        return servers;
    }

    /**
     * Saves the server list to the XML file.
     *
     * @param servers list of server entries to save
     */
    public static void save(List<ServerEntry> servers)
    {
        if (servers == null || servers.isEmpty())
        {
            // Delete file if no servers
            File file = getFile();
            if (file.exists())
                file.delete();
            return;
        }

        try
        {
            DocumentBuilder builder = WWXML.createDocumentBuilder(false);
            Document doc = builder.newDocument();

            Element root = doc.createElement(ROOT_ELEMENT);
            doc.appendChild(root);

            for (ServerEntry entry : servers)
            {
                Element serverElem = doc.createElement(SERVER_ELEMENT);
                serverElem.setAttribute(NAME_ATTR, entry.name != null ? entry.name : "");
                serverElem.setAttribute(URL_ATTR, entry.url != null ? entry.url : "");
                root.appendChild(serverElem);
            }

            WWXML.saveDocumentToFile(doc, getFile().getAbsolutePath());
        }
        catch (Exception e)
        {
            Logging.logger().log(Level.WARNING, "Failed to save WMS server list", e);
        }
    }
}
