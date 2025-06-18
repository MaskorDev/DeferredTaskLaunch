Подсистема запуска отложенных задач
https://img.shields.io/badge/Java-17%252B-blue.svg
https://img.shields.io/badge/License-MIT-green.svg

Система для управления и выполнения отложенных задач с поддержкой повторных попыток, категоризацией задач и многопоточной обработкой.

Ключевые возможности
📅 Планирование задач на определенное время

🔁 Автоматические повторы с экспоненциальной или фиксированной задержкой

🧩 Разделение задач по категориям с изолированными пулами потоков

⚡️ Многопоточная обработка задач

❌ Отмена запланированных задач

🛡 Гарантия однократной обработки задачи

📊 Мониторинг состояния задач и воркеров

💾 Хранение состояния в базе данных (MySQL)

Технологический стек
Язык: Java 17+

База данных: MySQL

Зависимости:

HikariCP - пул соединений с БД

SLF4J - логирование

Jackson - работа с JSON

Быстрый старт
Требования
Java 17+

MySQL 5.7+

Maven 3.6+

Настройка базы данных
Создайте базу данных:

sql
CREATE DATABASE task_system;
Настройте доступ в DatabaseConnection.java:

java
config.setJdbcUrl("jdbc:mysql://localhost:3306/task_system");
config.setUsername("ваш_пользователь");
config.setPassword("ваш_пароль");
Сборка и запуск
bash
mvn clean package
java -jar target/task-system.jar
Использование системы
Инициализация воркера
java
workerManager.init(
    new WorkerParams("email", 5), // Категория "email", 5 потоков
    new RetryPolicyParam(
        true,    // Экспоненциальная задержка
        3,       // Макс. 3 попытки
        2.0,     // Базовый множитель
        10000    // Макс. задержка 10 секунд
    )
);
Создание задачи
java
long taskId = taskManager.schedule(
    "email",                      // Категория
    EmailTask.class,              // Класс задачи
    new TaskParams("{\"to\":\"user@example.com\"}"), // Параметры
    LocalDateTime.now().plusMinutes(30) // Время выполнения
);
Отмена задачи
java
boolean cancelled = taskManager.cancel("email", taskId);
if (cancelled) {
    System.out.println("Задача успешно отменена");
}
Примеры задач
java
public class EmailTask implements Task {
    @Override
    public void execute(TaskParams params) {
        EmailData data = params.fromJson(EmailData.class);
        // Логика отправки email
    }
}

public class PaymentTask implements Task {
    @Override
    public void execute(TaskParams params) throws PaymentException {
        PaymentData data = params.fromJson(PaymentData.class);
        // Логика обработки платежа
    }
}
Архитектура системы
Основные компоненты
TaskManager - управление задачами:

schedule() - планирование задач

cancel() - отмена задач

WorkerManager - управление воркерами:

init() - инициализация воркера для категории

destroy() - остановка воркера

Task - интерфейс для реализации бизнес-логики задач

База данных - хранение:

Конфигурации воркеров (workers_config)

Задач (deferred_<категория>)

Поток обработки задач
Планировщик проверяет задачи для выполнения

Задачи со статусом PENDING блокируются (SKIP LOCKED)

Задачи отправляются в пул потоков

При успешном выполнении статус меняется на COMPLETED

При ошибке:

Если остались попытки - перепланирование

Если попытки исчерпаны - статус FAILED

Мониторинг
Система предоставляет консольный интерфейс для мониторинга:

text
=== СПИСОК ВОРКЕРОВ ===
┌───────────────────────┬──────────┬────────────┬──────────────┬─────────────────────┐
│ Категория             │ Потоки   │ Макс попыт.│ Задержка     │ Создан             │
├───────────────────────┼──────────┼────────────┼──────────────┼─────────────────────┤
│ email                 │ 5        │ 3          │ экспоненц.   │ 12:30:45           │
│ notification          │ 3        │ 5          │ фиксир.      │ 12:32:10           │
└───────────────────────┴──────────┴────────────┴──────────────┴─────────────────────┘
Лицензия
Проект распространяется под лицензией MIT. Подробнее см. в файле LICENSE.

