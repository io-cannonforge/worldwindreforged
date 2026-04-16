/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Loads a GeoJSON file and renders its features as paths, polygons, and markers.
 */
package gov.nasa.worldwindx.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nasa.worldwind.formats.geojson.*;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwindx.mcp.GlobeState;
import gov.nasa.worldwindx.mcp.Json;
import gov.nasa.worldwindx.mcp.Tool;
import gov.nasa.worldwindx.mcp.ToolResult;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a GeoJSON file and renders its features on a new RenderableLayer.
 * Supports Point, LineString, Polygon, and their Multi variants.
 */
public final class AddVectorLayerTool implements Tool {

    @Override
    public String name() { return "add_vector_layer"; }

    @Override
    public String description() {
        return "Load a GeoJSON file and display its features (points, lines, polygons) on the globe.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = Json.objectSchema();
        Json.addProp(schema, "path", "string",
            "Absolute path to a GeoJSON file.");
        Json.addProp(schema, "color", "string",
            "Feature color as hex (e.g. '#FF0000') or name ('red','blue','green','yellow','white'). Default 'cyan'.");
        Json.addProp(schema, "name", "string",
            "Display name for this layer (default: filename).");
        Json.requireProps(schema, "path");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments, GlobeState state) throws Exception {
        String path = arguments.path("path").asText(null);
        if (path == null || path.isBlank()) {
            return ToolResult.error("'path' is required.");
        }

        File file = new File(path);
        if (!file.isFile()) {
            return ToolResult.error("File not found: " + path);
        }

        Color color = parseColor(arguments.path("color").asText("cyan"));
        String displayName = arguments.path("name").asText(file.getName());

        // Parse GeoJSON.
        Object root;
        try (GeoJSONDoc doc = new GeoJSONDoc(file)) {
            doc.parse();
            root = doc.getRootObject();
        }

        if (!(root instanceof GeoJSONObject geoRoot)) {
            return ToolResult.error("Failed to parse GeoJSON from: " + file.getName());
        }

        // Create a dedicated layer for this vector data.
        RenderableLayer layer = new RenderableLayer();
        layer.setName(displayName);
        layer.setPickEnabled(false);

        Material material = new Material(color);
        int[] counts = {0, 0, 0}; // points, lines, polygons

        // Extract features.
        List<GeoJSONGeometry> geometries = new ArrayList<>();
        collectGeometries(geoRoot, geometries);

        for (GeoJSONGeometry geom : geometries) {
            addGeometry(geom, layer, material, counts);
        }

        state.runOnEdt(() ->
            state.wwd().getModel().getLayers().add(layer));
        state.wwd().redraw();

        String handle = state.newHandle("vector");
        state.registerLayerHandle(handle, layer);

        return ToolResult.ok(String.format(
                "Loaded **%s**: %d points, %d lines, %d polygons.",
                displayName, counts[0], counts[1], counts[2]))
                .handle("vector_layer", handle)
                .build();
    }

    private void collectGeometries(GeoJSONObject obj, List<GeoJSONGeometry> out) {
        if (obj.isFeatureCollection()) {
            GeoJSONFeature[] features = obj.asFeatureCollection().getFeatures();
            if (features != null) {
                for (GeoJSONFeature f : features) {
                    collectGeometries(f, out);
                }
            }
        } else if (obj.isFeature()) {
            GeoJSONGeometry geom = obj.asFeature().getGeometry();
            if (geom != null) {
                out.add(geom);
            }
        } else if (obj.isGeometry()) {
            out.add(obj.asGeometry());
        }
    }

    private void addGeometry(GeoJSONGeometry geom, RenderableLayer layer,
                             Material material, int[] counts) {
        if (geom.isPoint()) {
            addPoint(geom.asPoint(), layer, material);
            counts[0]++;
        } else if (geom.isMultiPoint()) {
            GeoJSONMultiPoint mp = (GeoJSONMultiPoint) geom;
            GeoJSONPositionArray coords = mp.getCoordinates();
            if (coords != null) {
                for (Position pos : coords) {
                    addPointAt(pos, layer, material);
                    counts[0]++;
                }
            }
        } else if (geom.isLineString()) {
            addLineString(geom.asLineString(), layer, material);
            counts[1]++;
        } else if (geom.isMultiLineString()) {
            GeoJSONMultiLineString mls = (GeoJSONMultiLineString) geom;
            GeoJSONPositionArray[] lines = mls.getCoordinates();
            if (lines != null) {
                for (GeoJSONPositionArray line : lines) {
                    addPath(line, layer, material);
                    counts[1]++;
                }
            }
        } else if (geom.isPolygon()) {
            addPolygon(geom.asPolygon(), layer, material);
            counts[2]++;
        } else if (geom.isMultiPolygon()) {
            GeoJSONMultiPolygon mpoly = (GeoJSONMultiPolygon) geom;
            GeoJSONPositionArray[][] polygons = mpoly.getCoordinates();
            if (polygons != null) {
                for (GeoJSONPositionArray[] rings : polygons) {
                    if (rings != null && rings.length > 0) {
                        addSurfacePolygon(rings[0], layer, material);
                        counts[2]++;
                    }
                }
            }
        } else if (geom.isGeometryCollection()) {
            GeoJSONGeometry[] children = geom.asGeometryCollection().getGeometries();
            if (children != null) {
                for (GeoJSONGeometry child : children) {
                    addGeometry(child, layer, material, counts);
                }
            }
        }
    }

    private void addPoint(GeoJSONPoint point, RenderableLayer layer, Material material) {
        Position pos = point.getPosition();
        if (pos != null) {
            addPointAt(pos, layer, material);
        }
    }

    private void addPointAt(Position pos, RenderableLayer layer, Material material) {
        PointPlacemark pm = new PointPlacemark(pos);
        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        attrs.setLineMaterial(material);
        attrs.setUsePointAsDefaultImage(true);
        pm.setAttributes(attrs);
        layer.addRenderable(pm);
    }

    private void addLineString(GeoJSONLineString ls, RenderableLayer layer, Material material) {
        GeoJSONPositionArray coords = ls.getCoordinates();
        if (coords != null) {
            addPath(coords, layer, material);
        }
    }

    private void addPath(Iterable<? extends Position> positions,
                         RenderableLayer layer, Material material) {
        SurfacePolyline line = new SurfacePolyline(positions);
        BasicShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(material);
        attrs.setOutlineWidth(3.0);
        attrs.setDrawInterior(false);
        line.setAttributes(attrs);
        layer.addRenderable(line);
    }

    private void addPolygon(GeoJSONPolygon polygon, RenderableLayer layer, Material material) {
        GeoJSONPositionArray[] rings = polygon.getCoordinates();
        if (rings != null && rings.length > 0) {
            addSurfacePolygon(rings[0], layer, material);
        }
    }

    private void addSurfacePolygon(GeoJSONPositionArray outerRing,
                                    RenderableLayer layer, Material material) {
        SurfacePolygon sp = new SurfacePolygon(outerRing);
        BasicShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(material);
        attrs.setInteriorMaterial(material);
        attrs.setOutlineWidth(2.0);
        attrs.setInteriorOpacity(0.25);
        sp.setAttributes(attrs);
        layer.addRenderable(sp);
    }

    private Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) return Color.CYAN;
        return switch (colorStr.toLowerCase().trim()) {
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            case "white" -> Color.WHITE;
            case "orange" -> Color.ORANGE;
            case "magenta" -> Color.MAGENTA;
            case "cyan" -> Color.CYAN;
            default -> {
                try {
                    yield Color.decode(colorStr);
                } catch (NumberFormatException e) {
                    yield Color.CYAN;
                }
            }
        };
    }
}
