package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/testdb";
    private static final String USER = "appuser";
    private static final String PASSWORD = "password";
    private static final DataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void testConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            System.out.println("Подключение к MySQL успешно!");
        }
    }

    public static void initializeDatabaseForCategory(String category) {
        String tableName = "deferred_" + category;
        String[] createTables = {
                String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    category VARCHAR(50) NOT NULL,
                    task_class VARCHAR(255) NOT NULL,
                    params JSON NOT NULL,
                    scheduled_time TIMESTAMP NOT NULL,
                    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED') DEFAULT 'PENDING',
                    max_attempts INT DEFAULT 1,
                    exponential_backoff BOOLEAN DEFAULT FALSE,
                    backoff_base DOUBLE DEFAULT 0,
                    max_backoff_ms BIGINT DEFAULT 0,
                    attempt_count INT DEFAULT 0,
                    next_attempt_time TIMESTAMP NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX (category, status, scheduled_time)
                )
                """, tableName),
                String.format("""
                CREATE TABLE IF NOT EXISTS %s_locks (
                    task_id BIGINT PRIMARY KEY,
                    worker_id VARCHAR(100) NOT NULL,
                    locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (task_id) REFERENCES %s(id) ON DELETE CASCADE
                )
                """, tableName, tableName)
        };

        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {

            for (String sql : createTables) {
                stmt.execute(sql);
            }
            System.out.printf("Таблицы для категории '%s' успешно созданы/проверены%n", category);

        } catch (SQLException ex) {
            System.err.printf("Ошибка при создании таблиц для категории '%s': %s%n",
                    category, ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}