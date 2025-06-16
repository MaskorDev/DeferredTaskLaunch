package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class WorkerManagerImpl implements WorkerManager {
    private static final Logger logger = LoggerFactory.getLogger(WorkerManagerImpl.class);
    private final ConcurrentMap<String, ExecutorService> workers = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    public WorkerManagerImpl(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public void init(WorkerParams workerParams, RetryPolicyParam retryParams) {
        ExecutorService executor = Executors.newFixedThreadPool(workerParams.getThreadCount());
        workers.put(workerParams.getCategory(), executor);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processTasks(workerParams.getCategory());
            } catch (Exception e) {
                logger.error("Error in task scheduler", e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        logger.info("Worker initialized for category: {} with {} threads",
                workerParams.getCategory(), workerParams.getThreadCount());
    }

    private List<TaskData> fetchPendingTasks(String category) {
        List<TaskData> tasks = new ArrayList<>();
        String sql = "SELECT * FROM deferred_" + category +
                " WHERE status = 'PENDING' " +
                "AND (scheduled_time <= NOW() OR next_attempt_time <= NOW()) " +
                "ORDER BY scheduled_time LIMIT 100";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                tasks.add(new TaskData(
                        rs.getLong("id"),
                        rs.getString("category"),
                        rs.getString("task_class"),
                        rs.getString("params"),
                        rs.getTimestamp("scheduled_time").toLocalDateTime(),
                        rs.getInt("max_attempts"),
                        rs.getBoolean("exponential_backoff"),
                        rs.getDouble("backoff_base"),
                        rs.getLong("max_backoff_ms"),
                        rs.getInt("attempt_count")
                ));
            }
        } catch (SQLException ex) {
            logger.error("Error fetching tasks for category: {}", category, ex);
        }
        return tasks;
    }

    private void processTasks(String category) {
        List<TaskData> tasks = fetchPendingTasks(category);
        logger.debug("Found {} pending tasks for category: {}", tasks.size(), category);

        ExecutorService executor = workers.get(category);
        if (executor != null) {
            for (TaskData task : tasks) {
                executor.submit(() -> {
                    try {
                        executeTaskWithRetry(task);
                    } catch (SQLException ex) {
                        logger.error("Database error processing task {}", task.getId(), ex);
                    } catch (Exception ex) {
                        logger.error("Unexpected error processing task {}", task.getId(), ex);
                    }
                });
            }
        }
    }

    private void executeTaskWithRetry(TaskData task) throws SQLException {
        int attempt = 0;
        boolean taskCompleted = false;
        String category = task.getCategory();

        while (attempt < task.getMaxAttempts() && !taskCompleted) {
            attempt++;

            try {
                if (lockTaskInDatabase(category, task.getId())) {
                    logger.info("Starting task {} (attempt {}/{})",
                            task.getId(), attempt, task.getMaxAttempts());

                    executeTask(task);

                    updateTaskStatus(category, task.getId(), "COMPLETED");
                    taskCompleted = true;
                    logger.info("Task {} completed successfully", task.getId());
                }
            } catch (Exception ex) {
                logger.error("Error executing task {} (attempt {})", task.getId(), attempt, ex);

                if (attempt < task.getMaxAttempts()) {
                    long delay = calculateRetryDelay(task, attempt);
                    logger.info("Scheduling retry for task {} in {} ms", task.getId(), delay);
                    scheduleNextAttempt(task, delay);
                } else {
                    updateTaskStatus(category, task.getId(), "FAILED");
                    logger.error("Task {} failed after {} attempts", task.getId(), task.getMaxAttempts());
                }
            } finally {
                unlockTaskInDatabase(category, task.getId());
            }
        }
    }

    private long calculateRetryDelay(TaskData task, int attempt) {
        if (!task.isExponentialBackoff()) {
            return task.getMaxBackoffMs();
        }

        double delay = Math.pow(task.getBackoffBase(), attempt) * 1000;
        return Math.min((long) delay, task.getMaxBackoffMs());
    }

    private boolean lockTaskInDatabase(String category, long taskId) throws SQLException {
        String sql = "UPDATE deferred_" + category +
                " SET status = 'PROCESSING' " +
                "WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, taskId);
            int updated = stmt.executeUpdate();
            return updated > 0;
        }
    }

    private void unlockTaskInDatabase(String category, long taskId) throws SQLException {
        String sql = "UPDATE deferred_" + category +
                " SET status = 'PENDING' " +
                "WHERE id = ? AND status = 'PROCESSING'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, taskId);
            stmt.executeUpdate();
        }
    }

    private void updateTaskStatus(String category, long taskId, String status) {
        String sql = "UPDATE deferred_" + category + " SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, taskId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            logger.error("Failed to update status for task {}", taskId, ex);
        }
    }

    private void scheduleNextAttempt(TaskData task, long delayMs) {
        LocalDateTime nextAttempt = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);
        String category = task.getCategory();

        String sql = "UPDATE deferred_" + category +
                " SET status = 'PENDING', " +
                "attempt_count = attempt_count + 1, " +
                "next_attempt_time = ? " +
                "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(nextAttempt));
            stmt.setLong(2, task.getId());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            logger.error("Failed to reschedule task {}", task.getId(), ex);
        }
    }

    private void executeTask(TaskData taskData) throws Exception {
        Class<?> clazz = Class.forName(taskData.getTaskClass());
        Task taskInstance = (Task) clazz.getDeclaredConstructor().newInstance();
        taskInstance.execute(new TaskParams(taskData.getParams()));
    }

    @Override
    public void destroy(String category) {
        ExecutorService executor = workers.get(category);
        if (executor != null) {
            executor.shutdown();
            workers.remove(category);
            logger.info("Worker destroyed for category: {}", category);
        }
    }
}