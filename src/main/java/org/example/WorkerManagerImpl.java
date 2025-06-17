package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerManagerImpl implements WorkerManager {
    private static final Logger logger = LoggerFactory.getLogger(WorkerManagerImpl.class);

    private final DataSource dataSource;
    private final TaskManager taskManager;
    private final ConcurrentMap<String, WorkerContext> workerContexts = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdownFlag = new AtomicBoolean(false);

    public WorkerManagerImpl(TaskManager taskManager, DataSource dataSource) {
        this.taskManager = taskManager;
        this.dataSource = dataSource;
        logger.info("WorkerManager initialized with dataSource: {}", dataSource);
    }

    @Override
    public void init(WorkerParams workerParams, RetryPolicyParam retryParams) {
        String category = workerParams.getCategory();
        if (workerContexts.containsKey(category)) {
            logger.warn("Worker for category {} already initialized", category);
            return;
        }

        logger.info("Initializing worker for category: {} with {} threads",
                category, workerParams.getThreadCount());

        validateTableStructure(category);

        WorkerContext context = new WorkerContext(workerParams, retryParams);
        workerContexts.put(category, context);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerParams.getThreadCount(),
                workerParams.getThreadCount(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new WorkerThreadFactory(category));

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new SchedulerThreadFactory(category));

        context.executor = executor;
        context.scheduler = scheduler;

        scheduler.scheduleWithFixedDelay(() -> processPendingTasks(category),
                100, 1000, TimeUnit.MILLISECONDS);
    }

    private void validateTableStructure(String category) {
        String tableName = "deferred_" + category;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet columns = meta.getColumns(null, null, tableName, "STATUS")) {
                if (!columns.next()) {
                    throw new IllegalStateException("Table " + tableName + " is missing required STATUS column");
                }
            }

            String[] requiredColumns = {"id", "category", "task_class", "params", "scheduled_time",
                    "max_attempts", "attempt_count", "status"};
            for (String column : requiredColumns) {
                try (ResultSet cols = meta.getColumns(null, null, tableName, column)) {
                    if (!cols.next()) {
                        throw new IllegalStateException("Table " + tableName + " is missing required column: " + column);
                    }
                }
            }

            logger.debug("Table {} structure validation passed", tableName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to validate table structure for " + tableName, e);
        }
    }

    private void processPendingTasks(String category) {
        if (shutdownFlag.get()) {
            return;
        }

        WorkerContext context = workerContexts.get(category);
        if (context == null) {
            return;
        }

        try {
            List<TaskData> tasks = fetchAndLockTasks(category);
            if (!tasks.isEmpty()) {
                logger.debug("Found {} tasks to process in category: {}", tasks.size(), category);
                for (TaskData task : tasks) {
                    context.executor.submit(() -> processTaskWithRetry(task));
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error in task scheduler for category: " + category, e);
        }
    }

    private List<TaskData> fetchAndLockTasks(String category) {
        List<TaskData> tasks = new ArrayList<>();
        String tableName = "deferred_" + category;
        String sql = String.format("""
            SELECT id, category, task_class, params, scheduled_time, 
                   max_attempts, exponential_backoff, backoff_base, max_backoff_ms, attempt_count, status
            FROM %s 
            WHERE status = 'PENDING' AND scheduled_time <= ? 
            ORDER BY scheduled_time LIMIT 100 FOR UPDATE SKIP LOCKED""", tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY,
                     ResultSet.CONCUR_UPDATABLE)) {

            conn.setAutoCommit(false);
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));

            ResultSet rs = stmt.executeQuery();
            int lockedCount = 0;

            while (rs.next()) {
                rs.updateString("status", "PROCESSING");
                rs.updateRow();

                TaskData task = new TaskData(
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
                );
                tasks.add(task);
                lockedCount++;
            }

            conn.commit();
            logger.trace("Locked {} tasks for processing in category: {}", lockedCount, category);
        } catch (SQLException ex) {
            logger.error("Failed to fetch and lock tasks for category: " + category, ex);
            throw new RuntimeException("Database error while fetching tasks", ex);
        }
        return tasks;
    }

    private void processTaskWithRetry(TaskData task) {
        if (shutdownFlag.get()) {
            return;
        }

        logger.info("Processing task {} [{}] in category: {}",
                task.getId(), task.getTaskClass(), task.getCategory());

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                executeTask(task);
                markTaskCompleted(conn, task);
                conn.commit();
                logger.info("Task {} completed successfully", task.getId());
            } catch (Exception ex) {
                conn.rollback();
                handleTaskFailure(conn, task, ex);
            }
        } catch (SQLException sqlEx) {
            logger.error("Database error processing task {}", task.getId(), sqlEx);
        }
    }

    private void executeTask(TaskData task) throws Exception {
        logger.debug("Executing task {} with class {}", task.getId(), task.getTaskClass());

        Class<?> clazz = Class.forName(task.getTaskClass());
        if (!Task.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + task.getTaskClass() +
                    " does not implement Task interface");
        }

        Task taskInstance = (Task) clazz.getDeclaredConstructor().newInstance();
        TaskParams params = new TaskParams(
                task.getParams(),
                task.getMaxAttempts(),
                task.isExponentialBackoff(),
                task.getBackoffBase(),
                task.getMaxBackoffMs()
        );

        taskInstance.execute(params);
    }

    private void markTaskCompleted(Connection conn, TaskData task) throws SQLException {
        String sql = "UPDATE deferred_" + task.getCategory() +
                " SET status = 'COMPLETED', attempt_count = attempt_count + 1, " +
                "completed_at = CURRENT_TIMESTAMP " +
                "WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, task.getId());
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                logger.warn("Expected to update 1 row for task {}, but updated {}",
                        task.getId(), updated);
            }
        }
    }

    private void handleTaskFailure(Connection conn, TaskData task, Exception ex) {
        int nextAttempt = task.getAttemptCount() + 1;
        logger.warn("Task {} failed (attempt {} of {}). Error: {}",
                task.getId(), nextAttempt, task.getMaxAttempts(), ex.getMessage());

        try {
            if (nextAttempt >= task.getMaxAttempts()) {
                markTaskFailed(conn, task);
            } else {
                scheduleRetry(conn, task, nextAttempt);
            }
            conn.commit();
        } catch (SQLException sqlEx) {
            logger.error("Failed to handle task failure for task {}", task.getId(), sqlEx);
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction", rollbackEx);
            }
        }
    }

    private void markTaskFailed(Connection conn, TaskData task) throws SQLException {
        String sql = "UPDATE deferred_" + task.getCategory() +
                " SET status = 'FAILED', attempt_count = attempt_count + 1 " +
                "WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, task.getId());
            stmt.executeUpdate();
            logger.error("Task {} marked as FAILED after maximum attempts", task.getId());
        }
    }

    private void scheduleRetry(Connection conn, TaskData task, int nextAttempt) throws SQLException {
        long delay = calculateRetryDelay(task, nextAttempt);
        LocalDateTime nextAttemptTime = LocalDateTime.now().plus(delay, ChronoUnit.MILLIS);

        String sql = "UPDATE deferred_" + task.getCategory() +
                " SET status = 'PENDING', attempt_count = ?, " +
                "next_attempt_time = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nextAttempt);
            stmt.setTimestamp(2, Timestamp.valueOf(nextAttemptTime));
            stmt.setLong(3, task.getId());
            stmt.executeUpdate();

            logger.info("Scheduled retry #{} for task {} at {}",
                    nextAttempt, task.getId(), nextAttemptTime);
        }
    }

    private long calculateRetryDelay(TaskData task, int attempt) {
        if (!task.isExponentialBackoff()) {
            return task.getMaxBackoffMs();
        }

        long delay = (long) (Math.pow(task.getBackoffBase(), attempt) * 1000);
        return Math.min(delay, task.getMaxBackoffMs());
    }

    @Override
    public void destroy(String category) {
        logger.info("Shutting down worker for category: {}", category);
        shutdownFlag.set(true);

        WorkerContext context = workerContexts.remove(category);
        if (context == null) {
            return;
        }

        shutdownExecutor(context.executor, "Worker-" + category);
        shutdownExecutor(context.scheduler, "Scheduler-" + category);

        logger.info("Worker for category {} shutdown complete", category);
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("{} did not terminate properly", name);
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class WorkerContext {
        final WorkerParams workerParams;
        final RetryPolicyParam retryParams;
        ThreadPoolExecutor executor;
        ScheduledExecutorService scheduler;

        WorkerContext(WorkerParams workerParams, RetryPolicyParam retryParams) {
            this.workerParams = workerParams;
            this.retryParams = retryParams;
        }
    }

    private static class WorkerThreadFactory implements ThreadFactory {
        private final String category;
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        WorkerThreadFactory(String category) {
            this.category = category;
            this.group = new ThreadGroup("WorkerGroup-" + category);
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    "worker-" + category + "-" + threadNumber.getAndIncrement(),
                    0);
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((t1, e) ->
                    logger.error("Uncaught exception in worker thread: " + t1.getName(), e));
            return t;
        }
    }

    private static class SchedulerThreadFactory implements ThreadFactory {
        private final String category;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        SchedulerThreadFactory(String category) {
            this.category = category;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "scheduler-" + category + "-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            t.setUncaughtExceptionHandler((t1, e) ->
                    logger.error("Uncaught exception in scheduler thread: " + t1.getName(), e));
            return t;
        }
    }
}