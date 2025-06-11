package org.example;

public class TaskParams {
    private final String jsonData;
    private final int maxAttempt;
    private final boolean exponentialBackoff;
    private final double backoffBase;
    private final long maxBackoffMs;

    public TaskParams(String jsonData) {
        this.jsonData = jsonData;
        this.maxAttempt = 1;
        this.exponentialBackoff = false;
        this.backoffBase = 0;
        this.maxBackoffMs = 0;
    }

    public TaskParams(String jsonData, int maxAttempt, boolean exponentialBackoff, double backoffBase,
                      long maxBackoffMs) {
        this.jsonData = jsonData;
        this.maxAttempt = maxAttempt;
        this.exponentialBackoff = exponentialBackoff;
        this.backoffBase = backoffBase;
        this.maxBackoffMs = maxBackoffMs;
    }

    public String getJsonData() {
        return jsonData;
    }

    public int getMaxAttempt() {
        return maxAttempt;
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
