# DeferredTaskLaunch

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![JDBC](https://img.shields.io/badge/JDBC-MySQL-blue)
![HikariCP](https://img.shields.io/badge/Connection%20Pool-HikariCP-brightgreen)
![Multi-threading](https://img.shields.io/badge/Concurrent-Multi--threaded-orange)

Система для отложенного выполнения задач с поддержкой MySQL и расширенными возможностями управления.

## 🌟 Особенности системы

- **Полная интеграция с MySQL** через JDBC
- **Динамическое управление** воркерами и задачами
- **Гибкие политики повтора** с экспоненциальной задержкой
- **Интерактивный CLI** для управления системой
- **Автоматическое восстановление** воркеров при старте

## 🚀 Быстрый старт

### Требования
- Java 17+
- MySQL 8.0+
- Maven 3.6+

### Настройка базы данных
1. Создайте базу данных:
   ```sql
   CREATE DATABASE task_launcher;
Настройте подключение в DatabaseConnection.java:

java
config.setJdbcUrl("jdbc:mysql://localhost:3306/task_launcher");
config.setUsername("your_username");
config.setPassword("your_password");
Сборка и запуск
bash
mvn clean package
java -jar target/DeferredTaskLaunch.jar
🛠 Основные компоненты
Ключевые классы
Класс	Назначение
Main	Точка входа, CLI интерфейс
DatabaseConnection	Управление подключением к MySQL
TaskManagerImpl	Реализация управления задачами
WorkerManagerImpl	Управление пулами потоков
Пример создания задачи
java
// Создание задачи с экспоненциальным повтором
long taskId = taskManager.schedule(
    "notifications",
    EmailNotificationTask.class,
    new TaskParams("{\"email\":\"user@example.com\"}", 3, true, 2.0, 15000),
    LocalDateTime.now().plusMinutes(30)
);
📊 Управление через CLI
Основные команды
text
=== Главное меню ===
1. Управление задачами
   - Создать задачу
   - Массовое создание
   - Отмена задачи
2. Управление воркерами
   - Добавить воркер
   - Остановить воркер
   - Список активных
3. Просмотр задач
   - По категориям
   - Детали задачи
   - Статистика
Пример работы
bash
Выберите категорию:
1. notifications
2. payments
Введите ID задачи: 42

=== Детали задачи ===
ID:           42
Тип:          EmailNotificationTask
Статус:       PENDING
Создана:      2023-05-15 14:30
Запланировано: 2023-05-15 15:00
⚙️ Технические детали
Политики повтора
java
// Пример настройки:
new RetryPolicyParam(
    true,     // Экспоненциальная задержка
    5,        // Макс. попыток
    2.5,      // Базовый множитель
    30000     // Макс. задержка (мс)
)
Схема базы данных
sql
CREATE TABLE deferred_<category> (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_class VARCHAR(255) NOT NULL,
    params TEXT NOT NULL,
    status ENUM('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED'),
    scheduled_time TIMESTAMP NOT NULL,
    next_attempt_time TIMESTAMP,
    max_attempts INT DEFAULT 1,
    attempt_count INT DEFAULT 0
);
📌 Советы по использованию
Для задач с высокой нагрузкой увеличьте размер пула соединений:

java
config.setMaximumPoolSize(20);
Используйте категории для логического разделения задач

Для длительных задач реализуйте LongRunningTask интерфейс

📝 Лицензия
MIT License. Полный текст доступен в файле LICENSE.

