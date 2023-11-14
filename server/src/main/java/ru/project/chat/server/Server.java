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
            System.out.println("Сервер запущен на порту " + port);
            while (true) {
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
        if (!"admin".equals(clientHandler.getNickname())) {
            broadcastMessage(clientHandler.getNickname() + " вошел в чат");
        }
    }

    public synchronized void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void sendMessageToUser(List<String> nicknames, String message) {
        for (ClientHandler client : clients) {
            for (String nickname : nicknames) {
                if (nickname.equals(client.getNickname())) {
                    client.sendMessage(message);
                }
            }
        }
    }

    public synchronized void unsubscribe(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        broadcastMessage(clientHandler.getNickname() + " вышел из чата");
    }

    // TODO: переделать на concurrentHashMap
    public synchronized List<String> getUserList() {
        var listUsers = new ArrayList<String>();
        for (ClientHandler client : clients) {
            listUsers.add(client.getNickname());
        }
        return listUsers;
    }

    public ClientHandler getClientForKick(String nicknameForKick) {
        for (ClientHandler client : clients) {
            if (nicknameForKick.equals(client.getNickname())) {
                return client;
                //unsubscribe(client);
                // TODO: закрыть все соединения для этого ClientHandler
                // разобраться почему удаляется несколько пользователей - тот которого удаляем и админ
            }
        }
        return null;
    }
}