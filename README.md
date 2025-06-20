# Подсистема запуска отложенных задач

***Система для управления и выполнения отложенных задач с поддержкой повторных попыток, категоризацией задач и многопоточной обработкой.***

## Ключевые возможности

- ***📅 Планирование задач*** на определенное время  
- ***🔁 Автоматические повторы*** с экспоненциальной или фиксированной задержкой  
- ***🧩 Разделение задач по категориям*** с изолированными пулами потоков  
- ***⚡️ Многопоточная обработка*** задач  
- ***❌ Отмена*** запланированных задач  
- ***🛡 Гарантия однократной обработки*** задачи  
- ***📊 Мониторинг*** состояния задач и воркеров  
- ***💾 Хранение состояния*** в базе данных (MySQL)  

## Технологический стек

- ***Язык:*** Java 17+  
- ***База данных:*** MySQL  
- ***Зависимости:***  
  - `HikariCP` - пул соединений с БД  
  - `SLF4J` - логирование  
  - `Jackson` - работа с JSON  

## Быстрый старт

### Требования

- Java 17+  
- MySQL 5.7+  
- Maven 3.6+  


