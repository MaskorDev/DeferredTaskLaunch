# DeferredTaskLaunch System

Система для отложенного выполнения задач с поддержкой MySQL и интерактивным CLI

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![JDBC](https://img.shields.io/badge/JDBC-MySQL-blue)
![HikariCP](https://img.shields.io/badge/Connection%20Pool-HikariCP-brightgreen)
![Concurrent](https://img.shields.io/badge/Concurrent-Multi--threaded-orange)

## Оглавление
1. [Особенности](#особенности)
2. [Быстрый старт](#быстрый-старт)
3. [Интерактивное управление](#интерактивное-управление)
4. [Примеры использования](#примеры-использования)
5. [Архитектура](#архитектура)
6. [Лицензия](#лицензия)

## Особенности

### Ключевые возможности
- **Гибкое планирование задач** на конкретное время
- **Многопоточная обработка** с настраиваемым количеством потоков
- **Категории задач** для изолированного выполнения
- **Повторы с экспоненциальной задержкой**
- **Гарантированная однократная обработка**
- **Интерактивный CLI интерфейс**

## Быстрый старт

### Требования
- Java 17+
- MySQL 8.0+
- Maven 3.6+

### Настройка БД
```sql
CREATE DATABASE task_launcher;
GRANT ALL PRIVILEGES ON task_launcher.* TO 'user'@'localhost';

### Конфигурация
В классе DatabaseConnection укажите:

config.setJdbcUrl("jdbc:mysql://localhost:3306/task_launcher");
config.setUsername("user");
config.setPassword("password");
Запуск
bash
mvn clean package
java -jar target/DeferredTaskLaunch.jar
Интерактивное управление
После запуска откроется главное меню:


=== ГЛАВНОЕ МЕНЮ ===
1. Управление задачами
2. Управление воркерами
3. Просмотр задач
0. Выход
Доступные действия

### Управление задачами:
- Создание одиночных задач
- Пакетное создание задач
- Отмена запланированных задач

### Управление воркерами:
- Добавление новых воркеров
- Остановка работающих воркеров
- Просмотр списка активных

### Просмотр задач:
- Список задач по категориям
- Детальная информация о задаче
- Статистика выполнения

Примеры использования
Создание задачи
Выберите "Управление задачами" → "Создать задачу"

Укажите категорию (например, "notifications")

Выберите тип задачи:

SuccessTask (успешное выполнение)

FailingTask (имитация ошибки)

LongRunningTask (долгая задача)

Введите параметры в JSON формате

Укажите время выполнения

Настройка воркера
text
Введите название категории: payments
Количество потоков (1-10): 5
Макс. попыток (1-10): 3
Экспоненциальная задержка (y/n)? y
Базовый множитель (1.0-5.0): 2.0
Макс. задержка (мс): 10000
Архитектура
Основные компоненты
Main - точка входа, CLI интерфейс

TaskManager - управление задачами

WorkerManager - управление воркерами

DatabaseConnection - работа с БД

Схема БД
sql
CREATE TABLE workers_config (
    category VARCHAR(50) PRIMARY KEY,
    thread_count INT NOT NULL,
    max_attempts INT NOT NULL,
    exponential_backoff BOOLEAN NOT NULL,
    backoff_base DOUBLE NOT NULL,
    max_backoff_ms BIGINT NOT NULL
);

CREATE TABLE deferred_<category> (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_class VARCHAR(255) NOT NULL,
    params TEXT NOT NULL,
    status ENUM('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED'),
    scheduled_time TIMESTAMP NOT NULL
);
