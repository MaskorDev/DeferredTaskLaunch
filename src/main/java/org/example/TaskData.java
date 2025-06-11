package org.example;

import java.time.LocalDateTime;

public class TaskData {
    private final long id;
    private final String category;
    private final String taskClass;
    private final String params;
    private final LocalDateTime scheduledTime;
    private final int maxAttempts;
    private final boolean exponentialBackoff;
    private final double backoffBase;
    private final long maxBackoffMs;
    private final int attemptCount;


    public TaskData(long id, String category, String taskClass, String params, LocalDateTime scheduledTime,
                    int maxAttempts, boolean exponentialBackoff, double backoffBase, long maxBackoffMs,
                    int attemptCount) {
        this.id = id;
        this.category = category;
        this.taskClass = taskClass;
        this.params = params;
        this.scheduledTime = scheduledTime;
        this.maxAttempts = maxAttempts;
        this.exponentialBackoff = exponentialBackoff;
        this.backoffBase = backoffBase;
        this.maxBackoffMs = maxBackoffMs;
        this.attemptCount = attemptCount;

    }

    public long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public String getParams() {
        return params;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
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

    public int getAttemptCount() {
        return attemptCount;
    }
}
