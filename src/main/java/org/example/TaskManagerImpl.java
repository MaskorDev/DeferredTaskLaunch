package org.example;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

public class TaskManagerImpl implements TaskManager {
    private final DataSource dataSource;

    public TaskManagerImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long schedule(String category, Class<Task> clazz, TaskParams params, LocalDateTime time) {
        String sql = "INSERT INTO deferred_" + category +
                " (category, task_class, params, scheduled_time, status, " +
                "max_attempts, exponential_backoff, backoff_base, max_backoff_ms) " +
                "VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, category);
            stmt.setString(2, clazz.getName());
            stmt.setString(3, params.toJson());
            stmt.setTimestamp(4, Timestamp.valueOf(time));
            stmt.setInt(5, params.getMaxAttempts());
            stmt.setBoolean(6, params.isExponentialBackoff());
            stmt.setDouble(7, params.getBackoffBase());
            stmt.setLong(8, params.getMaxBackoffMs());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to schedule task", ex);
        }
        throw new RuntimeException("Failed to get task ID");
    }

    @Override
    public boolean cancel(String category, long taskId) {
        String sql = "UPDATE deferred_" + category +
                " SET status = 'CANCELLED' " +
                "WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, taskId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to cancel task", ex);
        }
    }
}