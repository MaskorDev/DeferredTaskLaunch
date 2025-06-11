package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import javax.xml.crypto.Data;
import java.sql.Connection;
import java.sql.DriverManager;
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

    public static void testConnection() {
        try(Connection conn = getConnection()) {
            System.out.println("Подключение к MySql прошло успешно !");
        } catch (SQLException ex) {
            System.out.println("Подключение не удалось: " + ex);
        }

        initializeDatabase();
    }

    public static void main(String[] args) {
        testConnection();
    }

    public static void initializeDatabase() {
        String[] createTables = {
                """
            CREATE TABLE IF NOT EXISTS scheduled_tasks (
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
            """,
                """
            CREATE TABLE IF NOT EXISTS task_locks (
                task_id BIGINT PRIMARY KEY,
                worker_id VARCHAR(100) NOT NULL,
                locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (task_id) REFERENCES scheduled_tasks(id) ON DELETE CASCADE
            )
            """
        };

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {

            for (String sql: createTables) {
                stmt.execute(sql);
            }

            System.out.println("Таблицы успешно созданы/проверены");

        } catch (SQLException ex) {
            System.out.println("Ошибка при создании таблиц:" + ex.getMessage());
        }

    }
}
