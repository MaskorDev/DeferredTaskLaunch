<div align="center">
  <h1>🚀 DeferredTaskLaunch System</h1>
  <p>Система для отложенного выполнения задач с поддержкой MySQL и интерактивным CLI</p>

  <div>
    <img src="https://img.shields.io/badge/Java-17%2B-blue" alt="Java 17+">
    <img src="https://img.shields.io/badge/JDBC-MySQL-blue" alt="JDBC MySQL">
    <img src="https://img.shields.io/badge/Connection%20Pool-HikariCP-brightgreen" alt="HikariCP">
    <img src="https://img.shields.io/badge/Concurrent-Multi--threaded-orange" alt="Multi-threaded">
  </div>
</div>

## 📌 Оглавление
- [Особенности](#-особенности)
- [Быстрый старт](#-быстрый-старт)
- [Интерактивное управление](#-интерактивное-управление)
- [Примеры использования](#-примеры-использования)
- [Архитектура](#-архитектура)
- [Лицензия](#-лицензия)

## 🌟 Особенности

<details>
<summary><b>Ключевые возможности системы</b></summary>

✔ **Гибкое планирование задач** на конкретное время  
✔ **Многопоточная обработка** с настраиваемым количеством потоков  
✔ **Категории задач** для изолированного выполнения  
✔ **Интеллектуальные повторы** с экспоненциальной задержкой  
✔ **Гарантированная однократная обработка**  
✔ **Полноценное CLI** для управления системой  
✔ **Автоматическое восстановление** при перезапуске  
</details>

## 🚀 Быстрый старт

### Требования
- Java 17+
- MySQL 8.0+
- Maven 3.6+

### Настройка базы данных
```sql
CREATE DATABASE task_launcher;
GRANT ALL PRIVILEGES ON task_launcher.* TO 'user'@'localhost' IDENTIFIED BY 'password';
