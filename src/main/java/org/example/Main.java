package org.example;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static TaskManager taskManager;
    private static WorkerManager workerManager;
    private static Scanner scanner = new Scanner(System.in);

    static {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("org.example").setLevel(Level.OFF);
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
    }

    @SuppressWarnings("unchecked")
    private static Class<Task> asTaskClass(Class<? extends Task> clazz) {
        return (Class<Task>) clazz;
    }

    public static void main(String[] args) {
        try {
            initializeSystem();
            showMainMenu();
        } catch (Exception e) {
            System.out.println("⚠️ Критическая ошибка: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    private static void initializeSystem() throws SQLException {
        System.out.println("🔄 Инициализация системы...");

        createWorkersTableIfNotExists();

        taskManager = new TaskManagerImpl(DatabaseConnection.getDataSource());
        workerManager = new WorkerManagerImpl(taskManager, DatabaseConnection.getDataSource());

        startAllWorkersFromDatabase();

        System.out.println("✅ Система готова к работе\n");
    }

    private static void createWorkersTableIfNotExists() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS workers_config (
                category VARCHAR(50) PRIMARY KEY,
                thread_count INT NOT NULL,
                max_attempts INT NOT NULL,
                exponential_backoff BOOLEAN NOT NULL,
                backoff_base DOUBLE NOT NULL,
                max_backoff_ms BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void startAllWorkersFromDatabase() {
        String sql = "SELECT * FROM workers_config";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int started = 0;
            while (rs.next()) {
                String category = rs.getString("category");
                try {
                    workerManager.init(
                            new WorkerParams(
                                    category,
                                    rs.getInt("thread_count")
                            ),
                            new RetryPolicyParam(
                                    rs.getBoolean("exponential_backoff"),
                                    rs.getInt("max_attempts"),
                                    rs.getDouble("backoff_base"),
                                    rs.getLong("max_backoff_ms")
                            )
                    );
                    started++;
                    System.out.println("🔄 Воркер для категории '" + category + "' запущен");
                } catch (Exception e) {
                    System.out.println("⚠️ Не удалось запустить воркер для категории '" + category + "': " + e.getMessage());
                }
            }
            System.out.println("✅ Запущено воркеров: " + started);
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при запуске воркеров из БД: " + e.getMessage());
        }
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\n=== ГЛАВНОЕ МЕНЮ ===");
            System.out.println("1. Управление задачами");
            System.out.println("2. Управление воркерами");
            System.out.println("3. Просмотр задач");
            System.out.println("0. Выход");
            System.out.print("Выберите пункт: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine());
                switch (choice) {
                    case 1 -> manageTasksMenu();
                    case 2 -> manageWorkersMenu();
                    case 3 -> viewTasksMenu();
                    case 0 -> {
                        shutdownSystem();
                        return;
                    }
                    default -> System.out.println("⚠️ Неверный выбор, попробуйте еще раз");
                }
            } catch (NumberFormatException e) {
                System.out.println("⚠️ Введите число от 0 до 3");
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private static void manageTasksMenu() {
        List<String> categories = getActiveWorkerCategories();
        if (categories.isEmpty()) {
            System.out.println("\n⚠️ Нет активных воркеров. Сначала создайте воркер.");
            return;
        }

        while (true) {
            System.out.println("\n=== УПРАВЛЕНИЕ ЗАДАЧАМИ ===");
            System.out.println("1. Создать задачу");
            System.out.println("2. Создать несколько задач");
            System.out.println("3. Отменить задачу");
            System.out.println("0. Назад");
            System.out.print("Выберите пункт: ");

            int choice = readIntInput(0, 3);
            switch (choice) {
                case 1 -> createSingleTaskInteractive(categories);
                case 2 -> createMultipleTasksInteractive(categories);
                case 3 -> cancelTaskInteractive(categories);
                case 0 -> { return; }
            }
        }
    }

    private static void createSingleTaskInteractive(List<String> categories) {
        System.out.println("\n=== СОЗДАНИЕ ЗАДАЧИ ===");

        String category = selectCategoryFromList(categories);
        if (category == null) return;

        Class<? extends Task> taskClass = selectTaskType();
        if (taskClass == null) return;

        System.out.print("\nВведите параметры задачи (JSON): ");
        String params = scanner.nextLine();

        LocalDateTime scheduledTime = selectExecutionTime();

        try {
            long taskId = taskManager.schedule(
                    category,
                    asTaskClass(taskClass),
                    new TaskParams(params),
                    scheduledTime
            );

            System.out.println("\n✅ Задача успешно создана!");
            printTaskDetails(taskId, category, taskClass.getSimpleName(), scheduledTime);
        } catch (Exception e) {
            System.out.println("❌ Ошибка при создании задачи: " + e.getMessage());
        }
    }

    private static void createMultipleTasksInteractive(List<String> categories) {
        System.out.println("\n=== СОЗДАНИЕ НЕСКОЛЬКИХ ЗАДАЧ ===");

        String category = selectCategoryFromList(categories);
        if (category == null) return;

        System.out.print("\nСколько задач создать (1-100)? ");
        int count = readIntInput(1, 100);

        System.out.print("Интервал между задачами в секундах (0-60)? ");
        int interval = readIntInput(0, 60);

        TaskTypeSelection taskType = selectTaskTypeForBatch();
        if (taskType == null) return;

        System.out.println("\nПараметры задач будут автоматически генерироваться");
        System.out.print("Начать создание? (y/n): ");
        if (!readYesNoInput()) return;

        int created = 0;
        for (int i = 0; i < count; i++) {
            try {
                Class<? extends Task> taskClass = taskType.getTaskClass(i);
                String params = String.format("{\"taskNum\":%d,\"totalTasks\":%d}", i+1, count);

                long taskId = taskManager.schedule(
                        category,
                        asTaskClass(taskClass),
                        new TaskParams(params),
                        LocalDateTime.now().plusSeconds(i * interval)
                );

                created++;
                System.out.printf("Создана задача #%d (ID: %d, тип: %s)%n",
                        i+1, taskId, taskClass.getSimpleName());

                if (interval > 0 && i < count - 1) {
                    Thread.sleep(interval * 1000L);
                }
            } catch (Exception e) {
                System.out.println("Ошибка при создании задачи #" + (i+1) + ": " + e.getMessage());
            }
        }

        System.out.printf("\n✅ Итог: создано %d из %d задач%n", created, count);
    }

    private static void cancelTaskInteractive(List<String> categories) {
        System.out.println("\n=== ОТМЕНА ЗАДАЧИ ===");

        String category = selectCategoryFromList(categories);
        if (category == null) return;

        System.out.print("Введите ID задачи для отмены: ");
        long taskId = readLongInput(1, Long.MAX_VALUE);

        try {
            boolean result = taskManager.cancel(category, taskId);
            if (result) {
                System.out.println("✅ Задача успешно отменена");
            } else {
                System.out.println("⚠️ Задача не найдена или уже выполняется");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка при отмене задачи: " + e.getMessage());
        }
    }

    private static void manageWorkersMenu() {
        while (true) {
            System.out.println("\n=== УПРАВЛЕНИЕ ВОРКЕРАМИ ===");
            System.out.println("1. Добавить воркер");
            System.out.println("2. Остановить воркер");
            System.out.println("3. Список воркеров");
            System.out.println("0. Назад");
            System.out.print("Выберите пункт: ");

            int choice = readIntInput(0, 3);
            switch (choice) {
                case 1 -> addWorkerInteractive();
                case 2 -> stopWorkerInteractive();
                case 3 -> listWorkersInteractive();
                case 0 -> { return; }
            }
        }
    }

    private static void addWorkerInteractive() {
        System.out.println("\n=== ДОБАВЛЕНИЕ ВОРКЕРА ===");

        System.out.print("Введите название категории для воркера: ");
        String category = scanner.nextLine().trim();
        if (category.isEmpty()) {
            System.out.println("⚠️ Название категории не может быть пустым");
            return;
        }

        System.out.print("Количество потоков (1-10): ");
        int threads = readIntInput(1, 10);

        System.out.println("\nНастройки обработки ошибок:");
        System.out.print("Максимальное количество попыток (1-10): ");
        int maxAttempts = readIntInput(1, 10);

        System.out.print("Использовать экспоненциальную задержку (y/n)? ");
        boolean expBackoff = readYesNoInput();

        double backoffBase = 2.0;
        long maxBackoff = 10000;

        if (expBackoff) {
            System.out.print("Базовый множитель задержки (1.0-5.0): ");
            backoffBase = readDoubleInput(1.0, 5.0);

            System.out.print("Максимальная задержка (мс, 1000-60000): ");
            maxBackoff = readLongInput(1000, 60000);
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                INSERT INTO workers_config 
                (category, thread_count, max_attempts, exponential_backoff, backoff_base, max_backoff_ms) 
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, category);
                stmt.setInt(2, threads);
                stmt.setInt(3, maxAttempts);
                stmt.setBoolean(4, expBackoff);
                stmt.setDouble(5, backoffBase);
                stmt.setLong(6, maxBackoff);
                stmt.executeUpdate();
            }

            DatabaseConnection.initializeDatabaseForCategory(category);

            workerManager.init(
                    new WorkerParams(category, threads),
                    new RetryPolicyParam(expBackoff, maxAttempts, backoffBase, maxBackoff)
            );

            System.out.println("✅ Воркер для категории '" + category + "' успешно создан и запущен");
        } catch (Exception e) {
            System.out.println("❌ Ошибка при создании воркера: " + e.getMessage());
        }
    }

    private static void stopWorkerInteractive() {
        List<String> activeCategories = getActiveWorkerCategories();
        if (activeCategories.isEmpty()) {
            System.out.println("⚠️ Нет активных воркеров для остановки");
            return;
        }

        System.out.println("\n=== ОСТАНОВКА ВОРКЕРА ===");
        String category = selectCategoryFromList(activeCategories);
        if (category == null) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "DELETE FROM workers_config WHERE category = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, category);
                stmt.executeUpdate();
            }

            workerManager.destroy(category);
            System.out.println("✅ Воркер для категории '" + category + "' остановлен");
        } catch (Exception e) {
            System.out.println("❌ Ошибка при остановке воркера: " + e.getMessage());
        }
    }

    private static void listWorkersInteractive() {
        System.out.println("\n=== СПИСОК ВОРКЕРОВ ===");

        String sql = "SELECT * FROM workers_config ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("┌───────────────────────┬──────────┬────────────┬──────────────┬─────────────────────┐");
            System.out.println("│ Категория             │ Потоки   │ Макс попыт.│ Задержка     │ Создан             │");
            System.out.println("├───────────────────────┼──────────┼────────────┼──────────────┼─────────────────────┤");

            while (rs.next()) {
                System.out.printf("│ %-21s │ %-8d │ %-10d │ %-12s │ %-19s │%n",
                        rs.getString("category"),
                        rs.getInt("thread_count"),
                        rs.getInt("max_attempts"),
                        rs.getBoolean("exponential_backoff") ? "экспоненц." : "фиксир.",
                        rs.getTimestamp("created_at").toLocalDateTime().toLocalTime());
            }
            System.out.println("└───────────────────────┴──────────┴────────────┴──────────────┴─────────────────────┘");
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при получении списка воркеров: " + e.getMessage());
        }
    }

    private static List<String> getActiveWorkerCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT category FROM workers_config";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            System.out.println("⚠️ Ошибка при получении списка категорий: " + e.getMessage());
        }
        return categories;
    }

    private static void viewTasksMenu() {
        List<String> categories = getActiveWorkerCategories();
        if (categories.isEmpty()) {
            System.out.println("\n⚠️ Нет активных воркеров. Нет задач для отображения.");
            return;
        }

        while (true) {
            System.out.println("\n=== ПРОСМОТР ЗАДАЧ ===");
            System.out.println("1. Список всех задач");
            System.out.println("2. Статус конкретной задачи");
            System.out.println("3. Статистика по категории");
            System.out.println("0. Назад");
            System.out.print("Выберите пункт: ");

            int choice = readIntInput(0, 3);
            switch (choice) {
                case 1 -> listAllTasksInteractive(categories);
                case 2 -> showTaskStatusInteractive(categories);
                case 3 -> showCategoryStatsInteractive(categories);
                case 0 -> { return; }
            }
        }
    }

    private static void listAllTasksInteractive(List<String> categories) {
        String category = selectCategoryFromList(categories);
        if (category == null) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT id, task_class, status, scheduled_time, attempt_count, max_attempts " +
                    "FROM deferred_" + category + " ORDER BY scheduled_time DESC LIMIT 50";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                System.out.printf("\nПоследние 50 задач (%s):%n", category);
                System.out.println("┌───────┬──────────────────────────────┬────────────┬─────────────────────┬──────────┐");
                System.out.println("│ ID    │ Тип                          │ Статус     │ Время выполнения    │ Попытки  │");
                System.out.println("├───────┼──────────────────────────────┼────────────┼─────────────────────┼──────────┤");

                while (rs.next()) {
                    System.out.printf("│ %-5d │ %-28s │ %-10s │ %-19s │ %2d/%-5d │%n",
                            rs.getLong("id"),
                            shortenClassName(rs.getString("task_class")),
                            rs.getString("status"),
                            rs.getTimestamp("scheduled_time").toLocalDateTime().toLocalTime(),
                            rs.getInt("attempt_count"),
                            rs.getInt("max_attempts"));
                }
                System.out.println("└───────┴──────────────────────────────┴────────────┴─────────────────────┴──────────┘");
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при получении списка задач: " + e.getMessage());
        }
    }

    private static void showTaskStatusInteractive(List<String> categories) {
        String category = selectCategoryFromList(categories);
        if (category == null) return;

        System.out.print("Введите ID задачи: ");
        long taskId = readLongInput(1, Long.MAX_VALUE);

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM deferred_" + category + " WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, taskId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    System.out.println("\n=== ДЕТАЛИ ЗАДАЧИ ===");
                    System.out.printf("ID:           %d%n", rs.getLong("id"));
                    System.out.printf("Тип:          %s%n", rs.getString("task_class"));
                    System.out.printf("Статус:       %s%n", rs.getString("status"));
                    System.out.printf("Попытки:      %d/%d%n", rs.getInt("attempt_count"), rs.getInt("max_attempts"));
                    System.out.printf("Создана:      %s%n", rs.getTimestamp("created_at"));
                    System.out.printf("Запланировано: %s%n", rs.getTimestamp("scheduled_time"));
                    System.out.printf("Следующая попытка: %s%n", rs.getTimestamp("next_attempt_time"));
                    System.out.printf("Параметры:    %s%n", rs.getString("params"));
                } else {
                    System.out.println("⚠️ Задача не найдена");
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при получении статуса задачи: " + e.getMessage());
        }
    }

    private static void showCategoryStatsInteractive(List<String> categories) {
        String category = selectCategoryFromList(categories);
        if (category == null) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT " +
                    "COUNT(*) as total, " +
                    "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                    "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
                    "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
                    "SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END) as processing " +
                    "FROM deferred_" + category;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                if (rs.next()) {
                    System.out.println("\n=== СТАТИСТИКА КАТЕГОРИИ " + category.toUpperCase() + " ===");
                    System.out.printf("Всего задач:    %d%n", rs.getInt("total"));
                    System.out.printf("Успешно:        %d%n", rs.getInt("completed"));
                    System.out.printf("С ошибкой:      %d%n", rs.getInt("failed"));
                    System.out.printf("Ожидают:        %d%n", rs.getInt("pending"));
                    System.out.printf("В процессе:     %d%n", rs.getInt("processing"));
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка при получении статистики: " + e.getMessage());
        }
    }

    private static void shutdownSystem() {
        System.out.println("\nЗавершение работы системы...");
        try {
            List<String> categories = getActiveWorkerCategories();
            for (String category : categories) {
                workerManager.destroy(category);
            }
            System.out.println("✅ Все воркеры остановлены");
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка при остановке воркеров: " + e.getMessage());
        }
        System.out.println("✅ Система успешно остановлена");
    }

    private static String selectCategoryFromList(List<String> categories) {
        System.out.println("\nДоступные категории:");
        for (int i = 0; i < categories.size(); i++) {
            System.out.printf("%d. %s%n", i+1, categories.get(i));
        }
        System.out.print("Выберите категорию (1-" + categories.size() + "): ");

        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice >= 1 && choice <= categories.size()) {
                return categories.get(choice-1);
            }
        } catch (NumberFormatException e) {
            System.out.println("⚠️ Неверный ввод");
        }
        return null;
    }

    private static Class<? extends Task> selectTaskType() {
        System.out.println("\nТипы задач:");
        System.out.println("1. SuccessTask (успешное выполнение)");
        System.out.println("2. FailingTask (имитация ошибки)");
        System.out.println("3. LongRunningTask (долгая задача - 5 сек)");
        System.out.print("Выберите тип задачи (1-3): ");

        int choice = readIntInput(1, 3);
        return switch (choice) {
            case 1 -> SuccessTask.class;
            case 2 -> FailingTask.class;
            case 3 -> LongRunningTask.class;
            default -> null;
        };
    }

    private static TaskTypeSelection selectTaskTypeForBatch() {
        System.out.println("\nТип задач для пакетного создания:");
        System.out.println("1. Только успешные");
        System.out.println("2. Только с ошибками");
        System.out.println("3. Чередовать успешные и с ошибками");
        System.out.print("Выберите вариант (1-3): ");

        int choice = readIntInput(1, 3);
        return switch (choice) {
            case 1 -> i -> SuccessTask.class;
            case 2 -> i -> FailingTask.class;
            case 3 -> i -> i % 2 == 0 ? SuccessTask.class : FailingTask.class;
            default -> null;
        };
    }

    private static LocalDateTime selectExecutionTime() {
        System.out.println("\nКогда выполнить задачу:");
        System.out.println("1. Немедленно");
        System.out.println("2. Через указанное время");
        System.out.print("Выберите вариант (1-2): ");

        int choice = readIntInput(1, 2);
        switch (choice) {
            case 1: return LocalDateTime.now();
            case 2: {
                System.out.print("Через сколько секунд (1-3600)? ");
                int seconds = readIntInput(1, 3600);
                return LocalDateTime.now().plusSeconds(seconds);
            }
            default: return LocalDateTime.now();
        }
    }

    private static void printTaskDetails(long taskId, String category, String taskType, LocalDateTime scheduledTime) {
        System.out.println("┌───────────────────────┐");
        System.out.printf("│ ID: %-17d │%n", taskId);
        System.out.println("├───────────────────────┤");
        System.out.printf("│ Категория: %-10s │%n", category);
        System.out.printf("│ Тип: %-16s │%n", taskType);
        System.out.printf("│ Время выполнения:     │%n");
        System.out.printf("│ %-21s │%n", scheduledTime.toLocalTime());
        System.out.println("└───────────────────────┘");
    }

    private static String shortenClassName(String fullName) {
        return fullName.substring(fullName.lastIndexOf('.') + 1);
    }

    private static int readIntInput(int min, int max) {
        while (true) {
            try {
                int input = Integer.parseInt(scanner.nextLine());
                if (input >= min && input <= max) {
                    return input;
                }
                System.out.printf("Введите число от %d до %d: ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("Неверный ввод. Введите число: ");
            }
        }
    }

    private static long readLongInput(long min, long max) {
        while (true) {
            try {
                long input = Long.parseLong(scanner.nextLine());
                if (input >= min && input <= max) {
                    return input;
                }
                System.out.printf("Введите число от %d до %d: ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("Неверный ввод. Введите число: ");
            }
        }
    }

    private static double readDoubleInput(double min, double max) {
        while (true) {
            try {
                double input = Double.parseDouble(scanner.nextLine());
                if (input >= min && input <= max) {
                    return input;
                }
                System.out.printf("Введите число от %.1f до %.1f: ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("Неверный ввод. Введите число: ");
            }
        }
    }

    private static boolean readYesNoInput() {
        while (true) {
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("д") || input.equals("yes") || input.equals("да")) {
                return true;
            }
            if (input.equals("n") || input.equals("н") || input.equals("no") || input.equals("нет")) {
                return false;
            }
            System.out.print("Введите 'y' (да) или 'n' (нет): ");
        }
    }

    @FunctionalInterface
    private interface TaskTypeSelection {
        Class<? extends Task> getTaskClass(int index);
    }

    public static class SuccessTask implements Task {
        @Override
        public void execute(TaskParams params) {
            System.out.println("✅ Задача успешно выполнена: " + params.toJson());
        }
    }

    public static class FailingTask implements Task {
        @Override
        public void execute(TaskParams params) throws Exception {
            throw new Exception("❌ Имитация ошибки выполнения: " + params.toJson());
        }
    }

    public static class LongRunningTask implements Task {
        @Override
        public void execute(TaskParams params) throws InterruptedException {
            System.out.println("⏳ Долгая задача начата...");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("✅ Долгая задача завершена");
        }
    }
}