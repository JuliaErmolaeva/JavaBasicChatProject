package ru.project.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import static ru.project.chat.server.Command.*;

public class ClientHandler {
    private Socket socket;

    private Server server;
    private DataInputStream in;
    private DataOutputStream out;

    private boolean isAuthenticated = false;

    private String username;

    public String getUsername() {
        return username;
    }

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                authenticateUser(server);
                communicateWithUser(server);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        }).start();
    }

    private void authenticateUser(Server server) throws IOException {
        while (!isAuthenticated) {
            String message = in.readUTF();
//            /auth login password /auth admin admin
//            /register login nick password
            String[] args = message.split(" ");
            String command = args[0];

            switch (command) {
                case AUTH -> {
                    String login = args[1];
                    String password = args[2];
                    String username = server.getAuthenticationProvider().getUsernameByLoginAndPassword(login, password);
                    if (username == null || username.isBlank()) {
                        sendMessage("Указан неверный логин/пароль");
                    } else {
                        successAuthenticate(username);
                    }
                }
                case REGISTER -> {
                    String login = args[1];
                    String nick = args[2];
                    String password = args[3];
                    boolean isRegistered = server.getAuthenticationProvider().register(login, password, nick);
                    if (!isRegistered) {
                        sendMessage("Указанный логин/никнейм уже заняты");
                    } else {
                        successAuthenticate(nick);
                    }
                }
                default -> sendMessage("Авторизуйтесь сперва");
            }
        }
    }

    private void successAuthenticate(String name) {
        this.username = name;
        sendMessage(username + ", добро пожаловать в чат!");
        server.subscribe(this);
        isAuthenticated = true;
    }

    private void communicateWithUser(Server server) throws IOException {
        while (true) {
            // /exit -> disconnect()
            // /w user message -> user
            // /w tom Hello tom

            String message = in.readUTF();
            if (message.length() > 0) {
                String[] splitMessage = message.split(" ");
                String command = splitMessage[0];

                switch (command) {
                    case EXIT -> {
                        //TODO: написать логику
                    }
                    case LIST -> {
                        List<String> userList = server.getUserList();
                        String joinedUsers = String.join(", ", userList);
                        sendMessage(joinedUsers);
                    }
                    case WRITE -> {
                        if (splitMessage.length > 2) {
                            String recipient = splitMessage[1];
                            String messageToUser = convertArrayToString(Arrays.copyOfRange(splitMessage, 2, splitMessage.length));
                            server.sendMessageToUser(recipient, username + ": " + messageToUser);
                        }
                    }
                    case KICK -> {
                        if (server.getAuthenticationProvider().isCurrentUserCanKick(username)) {
                            String usernameForKick = splitMessage[1];
                            var clientForKick = server.getClientForKick(usernameForKick);
                            if (clientForKick != null) {
                                clientForKick.disconnect();
                            }
                        }
                    }
                    default -> server.broadcastMessage(username + ": " + message);
                }
            }
        }
    }

    private String convertArrayToString(String[] strings) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : strings) {
            stringBuilder.append(string).append(" ");
        }
        return stringBuilder.toString();
    }

    public void disconnect() {
        server.unsubscribe(this);
        try {
            if (socket != null) {
                socket.close();
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
            disconnect();
        }
    }
}