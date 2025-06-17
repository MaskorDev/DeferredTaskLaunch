package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/testdb");
        config.setUsername("appuser");
        config.setPassword("password");
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(60000);

        // Дополнительные настройки для улучшения производительности
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static void initializeDatabaseForCategory(String category) throws SQLException {
        String tableName = "deferred_" + category;
        String sql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                category VARCHAR(50) NOT NULL,
                task_class VARCHAR(255) NOT NULL,
                params TEXT NOT NULL,
                status ENUM('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED') DEFAULT 'PENDING',
                scheduled_time TIMESTAMP NOT NULL,
                next_attempt_time TIMESTAMP NULL,
                max_attempts INT NOT NULL DEFAULT 1,
                exponential_backoff BOOLEAN NOT NULL DEFAULT FALSE,
                backoff_base DOUBLE NOT NULL DEFAULT 2.0,
                max_backoff_ms BIGINT NOT NULL DEFAULT 10000,
                attempt_count INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP NULL,
                INDEX idx_status (status),
                INDEX idx_scheduled (scheduled_time),
                INDEX idx_next_attempt (next_attempt_time),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """, tableName);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);

            stmt.execute(String.format("ALTER TABLE %s COMMENT 'Таблица для отложенных задач категории %s'", tableName, category));
        }
    }

    /**
     * Проверяет существование таблицы для категории
     */
    public static boolean isTableExists(String category) throws SQLException {
        String tableName = "deferred_" + category;
        try (Connection conn = getConnection()) {
            try (var rs = conn.getMetaData().getTables(null, null, tableName, null)) {
                return rs.next();
            }
        }
    }

    /**
     * Проверяет наличие всех необходимых столбцов в таблице
     */
    public static void validateTableStructure(String category) throws SQLException {
        String tableName = "deferred_" + category;
        String[] requiredColumns = {
                "id", "category", "task_class", "params", "status",
                "scheduled_time", "next_attempt_time", "max_attempts",
                "exponential_backoff", "backoff_base", "max_backoff_ms",
                "attempt_count", "created_at", "completed_at"
        };

        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            for (String column : requiredColumns) {
                try (var rs = meta.getColumns(null, null, tableName, column)) {
                    if (!rs.next()) {
                        throw new SQLException(String.format(
                                "Столбец %s отсутствует в таблице %s", column, tableName));
                    }
                }
            }
        }
    }
}