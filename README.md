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
