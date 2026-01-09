package assignment.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * All SQS message bodies will be JSON strings produced/consumed by this class.
 */
public class JsonUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> cl) {
        try {
            return mapper.readValue(json, cl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to " + cl.getSimpleName(), e);
        }
    }
}
