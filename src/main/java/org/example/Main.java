package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            TaskManager taskManager = new TaskManagerImpl();
            WorkerManager workerManager = new WorkerManagerImpl(taskManager);

            WorkerParams workerParams = new WorkerParams("email", 5);
            RetryPolicyParam retryParam = new RetryPolicyParam();
        } finally {

        }

    }
}