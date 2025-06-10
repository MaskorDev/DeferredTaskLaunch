package org.example;

import java.util.concurrent.*;

public class WorkerManagerImpl implements WorkerManager{
    private final ConcurrentMap<String, ExecutorService> workers = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    public WorkerManagerImpl(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public void init(WorkerParams workerParams, RetryPolicyParam retryParams) {
        workers.computeIfAbsent(workerParams.getCategory(), cat ->
                Executors.newFixedThreadPool(workerParams.getThreadCount()));

        startTaskScheduler(workerParams.getCategory(), retryParams);
    }

    private void startTaskScheduler(String category, RetryPolicyParam retryParam) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> processTasks(category, retryParam), 0, 1, TimeUnit.SECONDS);
    }

    private void processTasks(String category, RetryPolicyParam retryParam) {
        ///
    }

    @Override
    public void destroy(String category) {
        ExecutorService executor = workers.get(category);
        if (executor != null) {
            executor.shutdown();
            workers.remove(category);
        }
    }
}
