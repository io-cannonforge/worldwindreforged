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
package gov.nasa.worldwind.formats.json;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import gov.nasa.worldwind.util.Logging;

/**
 * @author dcollins
 * @version $Id: BasicJSONEventParserContext.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class BasicJSONEventParserContext implements JSONEventParserContext
{
    protected JsonParser parser;
    protected boolean hasNext;
    protected JSONEvent nextEvent;
    protected Deque<String> fieldNameStack = new ArrayDeque<>();
    protected Map<String, JSONEventParser> parsers = new HashMap<>();

    public BasicJSONEventParserContext(JsonParser parser) throws IOException
    {
        if (parser == null)
        {
            String message = Logging.getMessage("nullValue.ParserIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.parser = parser;
        this.advance(); // Initializes hasNext and nextEvent.
    }

    @Override
	public boolean hasNext()
    {
        return this.hasNext;
    }

    @Override
	public JSONEvent nextEvent() throws IOException
    {
        JSONEvent e = this.nextEvent;
        this.advance();
        return e;
    }

    @Override
	public JSONEvent peek()
    {
        return this.nextEvent;
    }

    @Override
	public String getCurrentFieldName()
    {
        return this.fieldNameStack.peek();
    }

    @Override
	public void pushFieldName(String name)
    {
        if (name == null)
        {
            String message = Logging.getMessage("nullValue.NameIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.fieldNameStack.push(name);
    }

    @Override
	public void popFieldName()
    {
        this.fieldNameStack.pop();
    }

    @Override
	public JSONEventParser allocate(JSONEvent event)
    {
        return this.parsers.get(this.getCurrentFieldName()); // HashMap accepts the null key.
    }

    @Override
	public JSONEventParser getUnrecognizedParser()
    {
        return new BasicJSONEventParser();
    }

    @Override
	public void registerParser(String fieldName, BasicJSONEventParser parser)
    {
        this.parsers.put(fieldName, parser);
    }

    protected void advance() throws IOException
    {
        this.parser.nextToken();

        if (!this.parser.hasCurrentToken())
        {
            this.hasNext = false;
            this.nextEvent = null;
        }
        else
        {
            this.hasNext = true;
            this.nextEvent = this.createEvent(this.parser.currentToken());
        }
    }

    protected JSONEvent createEvent(JsonToken token) throws IOException
    {
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT)
        {
            return new NumericValueJSONEvent(this.parser.currentName(), this.parser.getDoubleValue());
        }
        else
        {
            return new BasicJSONEvent(this.parser, token, this.parser.currentName());
        }
    }
}
