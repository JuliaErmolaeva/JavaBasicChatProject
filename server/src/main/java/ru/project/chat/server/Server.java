package ru.project.chat.server;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class Server {
    private int port;
    private Map<String, ClientHandler> clients;
    private final AuthenticationProvider authenticationProvider;
    private ServerSocket serverSocket;

    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public Server(int port, AuthenticationProvider authenticationProvider) {
        this.port = port;
        clients = new ConcurrentHashMap<>();
        this.authenticationProvider = authenticationProvider;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            log.info("Сервер запущен на порту " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, this);
            }
        } catch (IOException e) {
            log.error(e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void subscribe(ClientHandler clientHandler) {
        clients.put(clientHandler.getNickname(), clientHandler);
        String message = clientHandler.getNickname() + " вошел в чат";
        if (!"admin".equals(clientHandler.getNickname())) {
            broadcastMessage(message);
        }
        log.info(message);
    }

    public void broadcastMessage(String message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }

    public void sendMessageToUser(List<String> nicknames, String message) {
        for (ClientHandler client : clients.values()) {
            for (String nickname : nicknames) {
                if (nickname.equals(client.getNickname())) {
                    client.sendMessage(message);
                }
            }
        }
    }

    public void unsubscribe(ClientHandler clientHandler) {
        for (String nickname : clients.keySet()) {
            if (clientHandler.getNickname().equals(nickname)) {
                clients.remove(clientHandler.getNickname());
                String message = clientHandler.getNickname() + " вышел из чата";
                broadcastMessage(message);
                log.info(message);
            }
        }
    }

    public List<String> getUserList() {
        return new ArrayList<>(clients.keySet());
    }

    public ClientHandler getClientForKick(String nicknameForKick) {
        for (ClientHandler client : clients.values()) {
            if (nicknameForKick.equals(client.getNickname())) {
                client.disconnect();
                return client;
            }
        }
        return null;
    }

    public void shutdownServer() throws IOException {
        for (ClientHandler client : clients.values()) {
            client.disconnect();
        }

        serverSocket.close();
        log.info("Stopped server");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.exit(0);
    }

    public boolean changeNickname(ClientHandler clientHandler, String newNickname) {
        String oldNickname = clientHandler.getNickname();
        clients.remove(oldNickname);
        clientHandler.setNickname(newNickname);
        clients.put(newNickname, clientHandler);
        return authenticationProvider.changeNickname(oldNickname, newNickname);
    }
}