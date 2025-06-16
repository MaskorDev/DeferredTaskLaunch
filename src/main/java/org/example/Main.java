package org.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static java.lang.Thread.sleep;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

public class Main {
    @SuppressWarnings("unchecked")
    private static Class<Task> asTaskClass(Class<? extends Task> clazz) {
        return (Class<Task>) clazz;
    }

    public static void printTaskStatus(String category, long taskId) {
        String sql = "SELECT id, status, attempt_count, scheduled_time, next_attempt_time " +
                "FROM deferred_" + category + " WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, taskId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.printf("Task %d: status=%s, attempts=%d, scheduled=%s, next_attempt=%s%n",
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getInt("attempt_count"),
                        rs.getTimestamp("scheduled_time"),
                        rs.getTimestamp("next_attempt_time"));
            }
        } catch (SQLException ex) {
            System.err.println("Error checking task status: " + ex.getMessage());
        }
    }

    public static void testSuccessfulTask(TaskManager taskManager) throws InterruptedException {
        TaskParams params = new TaskParams("{\"type\":\"success\"}");
        long taskId = taskManager.schedule(
                "test",
                asTaskClass(SuccessTask.class),
                params,
                LocalDateTime.now().plusSeconds(2)
        );

        System.out.println("=== Testing successful task ===");
        printTaskStatus("test", taskId); // Должен быть PENDING

        // Даем время на выполнение
        sleep(3000);

        printTaskStatus("test", taskId); // Должен быть COMPLETED
    }

    public static void testFailingTask(TaskManager taskManager) throws InterruptedException {
        TaskParams params = new TaskParams(
                "{\"type\":\"failing\"}",
                3,  // maxAttempts
                true,  // exponentialBackoff
                2.0,  // backoffBase
                10000  // maxBackoffMs (10 sec)
        );

        long taskId = taskManager.schedule(
                "test",
                asTaskClass(FailingTask.class),
                params,
                LocalDateTime.now().plusSeconds(1)
        );

        System.out.println("=== Testing failing task ===");
        for (int i = 0; i < 5; i++) {
            printTaskStatus("test", taskId);
            sleep(2000);
        }
    }

    public static class FailingTask implements Task {
        @Override
        public void execute(TaskParams params) throws Exception {
            throw new Exception("Simulated task failure");
        }
    }

    public static class SuccessTask implements Task {
        @Override
        public void execute(TaskParams params) {
            System.out.println("Successfully executed task!");
        }
    }

    public static void testCancellation(TaskManager taskManager) throws InterruptedException {
        TaskParams params = new TaskParams("{\"type\":\"cancellable\"}");
        long taskId = taskManager.schedule(
                "test",
                asTaskClass(LongRunningTask.class),
                params,
                LocalDateTime.now().plusSeconds(3)
        );

        System.out.println("=== Testing cancellation ===");
        printTaskStatus("test", taskId); // PENDING

        sleep(1000);
        boolean cancelled = taskManager.cancel("test", taskId);
        System.out.println("Cancellation result: " + cancelled);

        printTaskStatus("test", taskId); // Должен быть CANCELLED
    }

    public static class LongRunningTask implements Task {
        @Override
        public void execute(TaskParams params) throws InterruptedException {
            Thread.sleep(5000); // Долгая задача
        }
    }

    public static void main(String[] args) throws InterruptedException, SQLException {
        // Инициализация
        DatabaseConnection.testConnection();
        TaskManager taskManager = new TaskManagerImpl();
        WorkerManager workerManager = new WorkerManagerImpl(taskManager);
        workerManager.init(
                new WorkerParams("test", 3),
                new RetryPolicyParam(true, 3, 2.0, 10000)
        );
        DatabaseConnection.initializeDatabaseForCategory("test");

        // Запуск тестов
        testSuccessfulTask(taskManager);
        testFailingTask(taskManager);
        testCancellation(taskManager);

        // Завершение
        sleep(10000); // Даем время на выполнение всех задач
        workerManager.destroy("test");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}