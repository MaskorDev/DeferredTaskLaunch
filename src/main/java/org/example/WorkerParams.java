package org.example;

public class WorkerParams {
    private final String category;
    private final int threadCount;

    public WorkerParams(String category, int threadCount) {
        this.category = category;
        this.threadCount = threadCount;
    }

    public String getCategory() {
        return category;
    }

    public int getThreadCount() {
        return threadCount;
    }
}
