package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskParams {
    private final String jsonData;
    private final int maxAttempts;
    private final boolean exponentialBackoff;
    private final double backoffBase;
    private final long maxBackoffMs;

    public TaskParams(String jsonData) {
        this(jsonData, 1, false, 0, 0);
    }

    public TaskParams(String jsonData, int maxAttempts,
                      boolean exponentialBackoff,
                      double backoffBase, long maxBackoffMs) {
        this.jsonData = validateJson(jsonData);
        this.maxAttempts = maxAttempts;
        this.exponentialBackoff = exponentialBackoff;
        this.backoffBase = backoffBase;
        this.maxBackoffMs = maxBackoffMs;
    }

    private String validateJson(String json) {
        try {
            new ObjectMapper().readTree(json);
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON provided", e);
        }
    }

    public String toJson() {
        return jsonData; // Уже валидирован в конструкторе
    }

    public <T> T fromJson(Class<T> valueType) {
        try {
            return new ObjectMapper().readValue(jsonData, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize task params", e);
        }
    }

    public String getJsonData() {
        return jsonData;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    public double getBackoffBase() {
        return backoffBase;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }
}