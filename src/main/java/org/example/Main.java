package org.example;

import java.sql.SQLException;
import java.time.LocalDateTime;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

public class Main {

    @SuppressWarnings("unchecked")
    private static Class<Task> asTaskClass(Class<? extends Task> clazz) {
        return (Class<Task>) clazz;
    }

    public static void main(String[] args) {
        try {
            TaskManager taskManager = new TaskManagerImpl();
            WorkerManager workerManager = new WorkerManagerImpl(taskManager);

            // Инициализация воркера для категории "email"
            workerManager.init(
                    new WorkerParams("email", 3), // 3 потока для обработки
                    new RetryPolicyParam(true, 3, 2.0, 30000) // Политика повторов
            );

            // Создание и отправка задачи
            TaskParams params = new TaskParams(
                    "{\"to\":\"user@example.com\",\"subject\":\"Test\"}",
                    3, true, 2.0, 30000
            );

            long taskId = taskManager.schedule(
                    "email",
                    asTaskClass(EmailTask.class),
                    params,
                    LocalDateTime.now().plusSeconds(10)
            );

            System.out.println("Задача создана с ID: " + taskId);

            Thread.sleep(30000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}