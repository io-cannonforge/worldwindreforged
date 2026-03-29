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
package gov.nasa.worldwind.retrieve;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.logging.Level;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.util.Logging;

/**
 * @author Tom Gaskins
 * @version $Id: HTTPRetriever.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class HTTPRetriever extends URLRetriever
{
    private int responseCode;
    private String responseMessage;

    public HTTPRetriever(URL url, RetrievalPostProcessor postProcessor)
    {
        super(url, postProcessor);
    }

    @Override
    protected URLConnection openConnection() throws IOException
    {
        // Configure JVM-level HTTP connection pooling to match the retrieval thread pool size.
        // These are no-ops if already set by the application before first HTTP request.
        if (System.getProperty("http.keepAlive") == null)
            System.setProperty("http.keepAlive", "true");
        if (System.getProperty("http.maxConnections") == null)
        {
            int poolSize = Configuration.getIntegerValue(AVKey.RETRIEVAL_POOL_SIZE, 10);
            System.setProperty("http.maxConnections", String.valueOf(poolSize));
        }

        URLConnection conn = super.openConnection();
        // Request connection persistence; servers that support it will reuse the TCP socket.
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Keep-Alive", "timeout=30, max=100");
        return conn;
    }

    public int getResponseCode()
    {
        return this.responseCode;
    }

    public String getResponseMessage()
    {
        return this.responseMessage;
    }

    @Override
	protected ByteBuffer doRead(URLConnection connection) throws Exception
    {
        if (connection == null)
        {
            String msg = Logging.getMessage("nullValue.ConnectionIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        HttpURLConnection htpc = (HttpURLConnection) connection;
        this.responseCode = htpc.getResponseCode();
        this.responseMessage = htpc.getResponseMessage();
        String contentType = connection.getContentType();

        Logging.logger().log(Level.FINE, "HTTPRetriever.ResponseInfo", new Object[] {this.responseCode,
            connection.getContentLength(), contentType != null ? contentType : "content type not returned",
            connection.getURL()});

        if (this.responseCode == HttpURLConnection.HTTP_OK)
            return super.doRead(connection);

        return null;
    }
}
