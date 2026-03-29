/*
 * Copyright 2025-2026 seaglassfoundry.com. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Part of the WorldWind Reforged project — seaglassfoundry.com
 */

package gov.nasa.worldwind.formats.json;

import gov.nasa.worldwind.avlist.AVList;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class JSONDocTest
{
    @Test
    public void testParseSimpleObject() throws Exception
    {
        String json = "{\"name\": \"test\", \"value\": 42}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof AVList);

            AVList fields = (AVList) root;
            assertEquals("test", fields.getValue("name"));
            assertEquals(42.0, fields.getValue("value"));
        }
    }

    @Test
    public void testParseNestedObject() throws Exception
    {
        String json = "{\"outer\": {\"inner\": \"deep\"}}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof AVList);

            AVList outer = (AVList) ((AVList) root).getValue("outer");
            assertNotNull(outer);
            assertEquals("deep", outer.getValue("inner"));
        }
    }

    @Test
    public void testParseArray() throws Exception
    {
        String json = "[1, 2, 3]";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof Object[]);

            Object[] array = (Object[]) root;
            assertEquals(3, array.length);
        }
    }

    @Test
    public void testParseStringValue() throws Exception
    {
        String json = "{\"key\": \"hello world\"}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            AVList fields = (AVList) doc.getRootObject();
            assertEquals("hello world", fields.getValue("key"));
        }
    }

    @Test
    public void testParseNumericValues() throws Exception
    {
        String json = "{\"integer\": 99, \"decimal\": 3.14}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            AVList fields = (AVList) doc.getRootObject();
            assertNotNull(fields.getValue("integer"));
            assertNotNull(fields.getValue("decimal"));
        }
    }

    @Test
    public void testParseBooleanValues() throws Exception
    {
        String json = "{\"yes\": true, \"no\": false}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            AVList fields = (AVList) doc.getRootObject();
            assertEquals(true, fields.getValue("yes"));
            assertEquals(false, fields.getValue("no"));
        }
    }

    @Test
    public void testParseNullValue() throws Exception
    {
        String json = "{\"nothing\": null}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            AVList fields = (AVList) doc.getRootObject();
            assertNull(fields.getValue("nothing"));
        }
    }

    @Test
    public void testParseEmptyObject() throws Exception
    {
        String json = "{}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            // Empty objects resolve to null fields (no key-value pairs parsed)
            // This is expected behavior for the BasicJSONEventParser
        }
    }

    @Test
    public void testParseObjectWithArray() throws Exception
    {
        String json = "{\"items\": [\"a\", \"b\", \"c\"]}";
        try (JSONDoc doc = new JSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            AVList fields = (AVList) doc.getRootObject();
            Object items = fields.getValue("items");
            assertTrue(items instanceof Object[]);
            assertEquals(3, ((Object[]) items).length);
            assertEquals("a", ((Object[]) items)[0]);
        }
    }
}
