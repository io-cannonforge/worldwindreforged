/*
 * WorldWind Reforged MCP Server
 * seaglassfoundry.com
 *
 * Shared Jackson mapper and JSON Schema building helpers for the MCP protocol layer.
 */
package gov.nasa.worldwindx.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared Jackson mapper and JSON Schema building helpers. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() { }

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    /** Build an object-type JSON Schema draft-07 node with an empty properties map. */
    public static ObjectNode objectSchema() {
        ObjectNode schema = obj();
        schema.put("type", "object");
        schema.set("properties", obj());
        return schema;
    }

    /** Add a typed property to a schema's properties map. Returns the property node. */
    public static ObjectNode addProp(ObjectNode schema, String name, String type, String description) {
        ObjectNode prop = obj();
        prop.put("type", type);
        if (description != null) {
            prop.put("description", description);
        }
        ((ObjectNode) schema.get("properties")).set(name, prop);
        return prop;
    }

    /** Mark properties as required in a schema. */
    public static void requireProps(ObjectNode schema, String... names) {
        ArrayNode req = arr();
        for (String n : names) {
            req.add(n);
        }
        schema.set("required", req);
    }
}
