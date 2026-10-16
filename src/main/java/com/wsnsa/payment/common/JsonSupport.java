package com.wsnsa.payment.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Entity доторх JSON текст баганыг уншиж/бичих туслах.
 */
@Component
public class JsonSupport {

    private final ObjectMapper mapper;

    public JsonSupport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON deserialization failed", e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
