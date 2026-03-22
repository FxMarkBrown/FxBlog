package top.fxmarkbrown.blog.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.util.StringUtils;

public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonUtil() {
    }

    public static String toJsonString(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    public static JsonNode readTree(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 解析失败", ex);
        }
    }

    public static <T> T readValue(String content, Class<T> valueType) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(content, valueType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 解析失败", ex);
        }
    }

    public static <T> T readValue(String content, TypeReference<T> valueTypeRef) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(content, valueTypeRef);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 解析失败", ex);
        }
    }

    public static <T> T convertValue(Object value, Class<T> valueType) {
        if (value == null) {
            return null;
        }
        if (value instanceof String content) {
            return readValue(content, valueType);
        }
        return OBJECT_MAPPER.convertValue(value, valueType);
    }

    public static <T> T convertValue(Object value, TypeReference<T> valueTypeRef) {
        if (value == null) {
            return null;
        }
        if (value instanceof String content) {
            return readValue(content, valueTypeRef);
        }
        return OBJECT_MAPPER.convertValue(value, valueTypeRef);
    }
}
