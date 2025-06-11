package org.example;

import java.time.LocalDateTime;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws InterruptedException {
        TaskManager taskManager = new TaskManagerImpl();
        WorkerManager workerManager = new WorkerManagerImpl(taskManager);

        WorkerParams workerParams = new WorkerParams("email", 3);
        RetryPolicyParam retryPolicyParam = new RetryPolicyParam(
                true,
                3,
                2.0,
                30000
        );
        workerManager.init(workerParams, retryPolicyParam);

        TaskParams params = new TaskParams("test@example.com");
        long taskid = taskManager.schedule(
                "email",
                Task.class,
                params,
                LocalDateTime.now().plusSeconds(10)
        );
        System.out.println("Создана задача ID: " + taskid);

        long immediateTaskId = taskManager.schedule(
                "email",
                Task.class,
                params,
                LocalDateTime.now().minusMinutes(1)
        );
        System.out.println("Создана немедленная задача ID: " + taskid);

        System.out.println("Ожидаем выполнение задач...");
        Thread.sleep(60000); // Ждем 1 минуту

        boolean cancelled = taskManager.cancel("email", taskid);
        System.out.println("Задача " + taskid + " отменена: " + cancelled);

        workerManager.destroy("email");
        System.out.println("Система остановлена");



    }
}