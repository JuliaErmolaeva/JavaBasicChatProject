package ru.project.chat.client;

import java.io.IOException;
import java.util.Scanner;

public class Client implements Runnable {

    @Override
    public void run() {
        try (Network network = new Network()) {
            network.connect(8080);
            new Gui("chat", network);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String msg = scanner.nextLine();
                network.sendMessage(msg);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}