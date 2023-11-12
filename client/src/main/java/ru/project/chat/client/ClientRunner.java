package ru.project.chat.client;


public class ClientRunner {
    public static void main(String[] args) throws InterruptedException {
        Thread client = new Thread(new Client());
        client.start();
        client.join();
    }
}