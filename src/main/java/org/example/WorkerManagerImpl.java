package org.example;

import com.mysql.cj.x.protobuf.MysqlxCrud;

import javax.crypto.CipherInputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class WorkerManagerImpl implements WorkerManager {
    private final ConcurrentMap<String, ExecutorService> workers = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    public WorkerManagerImpl(TaskManager taskManager) {
        this.taskManager = new TaskManagerImpl();
    }

    @Override
    public void init(WorkerParams workerParams, RetryPolicyParam retryParams) {
        ExecutorService executor = Executors.newFixedThreadPool(workerParams.getThreadCount());
        workers.put(workerParams.getCategory(), executor);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(() ->
                processTasks(workerParams.getCategory()), 0, 1, TimeUnit.SECONDS);
    }

    private List<TaskData> fetchPendingTasks(String category) {
        List<TaskData> tasks = new ArrayList<>();
        String sql = "SELECT * FROM scheduled_tasks " +
                "WHERE category = ? AND STATUS = 'PENDING' " +
                "AND (scheduled_time <= NOW() OR next_attempt_time <= NOW()) " +
                "ORDER BY scheduled_time LIMIT 100";

        try (Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
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
            System.out.println(ex);
        }
        return tasks;
    }


    private void processTasks(String category) {
        List<TaskData> tasks = fetchPendingTasks(category);
        ExecutorService executor = workers.get(category);

        if (executor != null) {
            for (TaskData task: tasks) {
                executor.submit(() -> {
                        try {
                            executeTaskWithRetry(task);
                        } catch (SQLException ex) {
                            System.err.println("Error processing task: " + task.getId() + " " + ex.getMessage());
                        }
                });
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

    private void updateTaskStatus(long taskId, String status) {
        String sql = "UPDATE scheduled_tasks SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, taskId);

            stmt.executeUpdate();
        } catch (SQLException ex) {
            System.out.println(ex);
        }
    }

    @Override
    public void destroy(String category) {
        ExecutorService executor = workers.get(category);
        if (executor != null) {
            executor.shutdown();
            workers.remove(category);
        }
    }

    private void executeTaskWithRetry(TaskData task) throws SQLException {
        int attempt = 0;
        boolean taskCompleted = false;

        while (attempt < task.getMaxAttempts() && !taskCompleted) {
            attempt++;

            try {
                if (lockTaskInDatabase(task.getId())) {
                    executeTask(task);
                    updateTaskStatus(task.getId(), "COMPLETED");
                    taskCompleted = true;
                }
            } catch (Exception ex) {
                if (attempt < task.getMaxAttempts()) {
                    long delay = calculateRetryDelay(task, attempt);
                    scheduleNextAttemp(task, delay);
                } else {
                    updateTaskStatus(task.getId(), "FAILED");
                }
                System.out.println(ex);
            } finally {
                unlockTaskInDatabase(task.getId());
            }
        }
    }

    private boolean lockTaskInDatabase(long taskId) throws SQLException {
        String sql = "UPDATE scheduled_tasks SET status = 'PROCESSING' " +
                "WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, taskId);

            statement.executeUpdate();
        }
        return false;
    }

    private boolean unlockTaskInDatabase(long taskId) throws SQLException {
        String sql = "UPDATE scheduled_tasks SET status = 'PENDING' " +
                "WHERE id = ? AND status = 'PROCESSING'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setLong(1, taskId);

            statement.executeUpdate();
        }
        return false;
    }

    private void executeTask(TaskData taskData) throws Exception {
        Class<?> clazz = Class.forName(taskData.getTaskClass());
        Task taskInstance = (Task) clazz.getDeclaredConstructor().newInstance();
        taskInstance.execute(new TaskParams(taskData.getParams()));
    }

    private void scheduleNextAttemp(TaskData task, long delayMs) {
        LocalDateTime nextAttempt = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);

        String sql = "UPDATE schedule_tasks " +
                "SET status = 'PENDING', " +
                "attempt_count = attempt_count + 1, " +
                "next_attempt_time = ? " +
                "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(nextAttempt));
            stmt.setLong(2, task.getId());
        } catch (SQLException ex) {
            System.out.println(ex);
        }
    }
}
