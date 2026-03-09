package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.social.domain.message.dto.MessageResponse;

import java.util.Map;

public class MessageJsonMapperUtils {
    private static final ObjectMapper M = new ObjectMapper();

    public static MessageResponse fromMap(Map<String, Object> map) {
        return M.convertValue(map, MessageResponse.class);
    }

    public static Map toMap(MessageResponse doc) {
        return M.convertValue(doc, Map.class);
    }
}
