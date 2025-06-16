package org.example;

public class EmailTask implements Task {
    @Override
    public void execute(TaskParams params) throws Exception {
        String json = params.getJsonData();
        System.out.println("Отправляю email с параметрами: " + json);
    }
}

class EmailData {
    private String to;
    private String subject;


    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
