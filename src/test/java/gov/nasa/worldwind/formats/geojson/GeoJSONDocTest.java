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

package gov.nasa.worldwind.formats.geojson;

import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class GeoJSONDocTest
{
    @Test
    public void testParsePoint() throws Exception
    {
        String json = "{\"type\": \"Point\", \"coordinates\": [102.0, 0.5]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue("Expected GeoJSONPoint but got " + root.getClass().getName(),
                root instanceof GeoJSONPoint);

            GeoJSONPoint point = (GeoJSONPoint) root;
            assertNotNull(point.getPosition());
        }
    }

    @Test
    public void testParseLineString() throws Exception
    {
        String json = "{\"type\": \"LineString\", \"coordinates\": [[100.0, 0.0], [101.0, 1.0]]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONLineString);
        }
    }

    @Test
    public void testParsePolygon() throws Exception
    {
        String json = "{\"type\": \"Polygon\", \"coordinates\": [" +
            "[[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]]" +
            "]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONPolygon);
        }
    }

    @Test
    public void testParseFeature() throws Exception
    {
        String json = "{\"type\": \"Feature\", " +
            "\"geometry\": {\"type\": \"Point\", \"coordinates\": [102.0, 0.5]}, " +
            "\"properties\": {\"name\": \"test\"}}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONFeature);

            GeoJSONFeature feature = (GeoJSONFeature) root;
            assertNotNull(feature.getGeometry());
            assertTrue(feature.getGeometry() instanceof GeoJSONPoint);
        }
    }

    @Test
    public void testParseFeatureCollection() throws Exception
    {
        String json = "{\"type\": \"FeatureCollection\", \"features\": [" +
            "{\"type\": \"Feature\", " +
            " \"geometry\": {\"type\": \"Point\", \"coordinates\": [102.0, 0.5]}, " +
            " \"properties\": {\"name\": \"first\"}}," +
            "{\"type\": \"Feature\", " +
            " \"geometry\": {\"type\": \"Point\", \"coordinates\": [103.0, 1.5]}, " +
            " \"properties\": {\"name\": \"second\"}}" +
            "]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONFeatureCollection);

            GeoJSONFeatureCollection fc = (GeoJSONFeatureCollection) root;
            assertNotNull(fc.getFeatures());
            assertEquals(2, fc.getFeatures().length);
        }
    }

    @Test
    public void testParseMultiPoint() throws Exception
    {
        String json = "{\"type\": \"MultiPoint\", \"coordinates\": [[100.0, 0.0], [101.0, 1.0]]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONMultiPoint);
        }
    }

    @Test
    public void testParseMultiLineString() throws Exception
    {
        String json = "{\"type\": \"MultiLineString\", \"coordinates\": [" +
            "[[100.0, 0.0], [101.0, 1.0]], [[102.0, 2.0], [103.0, 3.0]]" +
            "]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONMultiLineString);
        }
    }

    @Test
    public void testParseMultiPolygon() throws Exception
    {
        String json = "{\"type\": \"MultiPolygon\", \"coordinates\": [" +
            "[[[102.0, 2.0], [103.0, 2.0], [103.0, 3.0], [102.0, 3.0], [102.0, 2.0]]]," +
            "[[[100.0, 0.0], [101.0, 0.0], [101.0, 1.0], [100.0, 1.0], [100.0, 0.0]]]" +
            "]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONMultiPolygon);
        }
    }

    @Test
    public void testParseGeometryCollection() throws Exception
    {
        String json = "{\"type\": \"GeometryCollection\", \"geometries\": [" +
            "{\"type\": \"Point\", \"coordinates\": [100.0, 0.0]}," +
            "{\"type\": \"LineString\", \"coordinates\": [[101.0, 0.0], [102.0, 1.0]]}" +
            "]}";
        try (GeoJSONDoc doc = new GeoJSONDoc(new ByteArrayInputStream(json.getBytes())))
        {
            doc.parse();
            Object root = doc.getRootObject();
            assertNotNull(root);
            assertTrue(root instanceof GeoJSONGeometryCollection);
        }
    }
}
