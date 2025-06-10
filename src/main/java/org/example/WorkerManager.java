package org.example;

public interface WorkerManager {
    void init(WorkerParams workerParams, RetryPolicyParam retryParams);
    void destroy(String category);
}
