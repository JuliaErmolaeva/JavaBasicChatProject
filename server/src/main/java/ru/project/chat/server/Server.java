package ru.project.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private final AuthenticationProvider authenticationProvider;

    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public Server(int port, AuthenticationProvider authenticationProvider) {
        this.port = port;
        clients = new ArrayList<>();
        this.authenticationProvider = authenticationProvider;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                System.out.println("Сервер запущен на порту " + port);
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized void subscribe(ClientHandler clientHandler) {
        clients.add(clientHandler);
        if (!"admin".equals(clientHandler.getUsername())) {
            broadcastMessage("Клиент: " + clientHandler.getUsername() + " вошел в чат");
        }
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void sendMessageToUser(String username, String message) {
        for (ClientHandler client : clients) {
            if (username.equals(client.getUsername())) {
                client.sendMessage(message);
            }
        }
    }

    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage("Клиент: " + clientHandler.getUsername() + " вышел из чата");
    }

    // TODO: переделать на concurrentHashMap
    public synchronized List<String> getUserList() {
        var listUsers = new ArrayList<String>();
        for (ClientHandler client : clients) {
            listUsers.add(client.getUsername());
        }
        return listUsers;
    }

    public ClientHandler getClientForKick(String usernameForKick) {
        for (ClientHandler client : clients) {
            if (usernameForKick.equals(client.getUsername())) {
                return client;
                //unsubscribe(client);
                // TODO: закрыть все соединения для этого ClientHandler
                // разобраться почему удаляется несколько пользователей - тот которого удаляем и админ
            }
        }
        return null;
    }
}