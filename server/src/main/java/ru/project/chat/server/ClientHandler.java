package ru.project.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static ru.project.chat.server.Command.*;

public class ClientHandler {
    private Socket socket;

    private Server server;
    private DataInputStream in;
    private DataOutputStream out;

    private boolean isAuthenticated = false;

    private String nickname;

    private static long TIME_TO_WAIT_ACTIVITY = 1_200_000L; // 20 минут в миллисекундах;

    private AtomicLong atomicLastActivityTime = new AtomicLong();

    public String getNickname() {
        return nickname;
    }

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                authenticateUser(server);
                checkUserActivity();
                communicateWithUser(server);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        }).start();
    }

    private void checkUserActivity() {
        new Thread(() -> {
            try {
                while (true) {
                    long lastActivityTime = atomicLastActivityTime.get();
                    if (System.currentTimeMillis() - lastActivityTime >= TIME_TO_WAIT_ACTIVITY) {
                        disconnect();
                        System.out.println("Превышено время ожидания действий от клиента");
                        break;
                    }
                    //System.out.println("Тестовый комментарий для проверки времени ожидания"); /TODO прикрутить логгер
                    Thread.sleep(10000L);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                disconnect();
            }
        }).start();
    }

    private void authenticateUser(Server server) throws IOException {
        while (!isAuthenticated) {
            String message = in.readUTF();
            String[] args = message.split(" ");
            String command = args[0];

            switch (command) {
                case AUTH -> {
                    String login = args[1];
                    String password = args[2];
                    String nickname = server.getAuthenticationProvider().getNicknameByLoginAndPassword(login, password);
                    if (nickname == null || nickname.isBlank()) {
                        sendMessage("Указан неверный логин/пароль");
                    } else {
                        successAuthenticate(nickname);
                    }
                }
                case REGISTER -> {
                    String login = args[1];
                    String nickname = args[2];
                    String password = args[3];
                    boolean isRegistered = server.getAuthenticationProvider().register(login, password, nickname);
                    if (!isRegistered) {
                        sendMessage("Указанный логин/никнейм уже заняты");
                    } else {
                        successAuthenticate(nickname);
                    }
                }
                default -> sendMessage("Авторизуйтесь сперва");
            }
        }
    }

    private void successAuthenticate(String name) {
        this.nickname = name;
        sendMessage(nickname + ", добро пожаловать в чат!");
        server.subscribe(this);
        isAuthenticated = true;
        atomicLastActivityTime.set(System.currentTimeMillis());
    }

    private void communicateWithUser(Server server) throws IOException {
        while (true) {
            String message = in.readUTF();
            if (message.length() > 0) {
                String[] splitMessage = message.split(" ");
                String command = splitMessage[0];

                switch (command) {
                    case EXIT -> {
                        //TODO: написать логику
                    }
                    case LIST -> {
                        long minutesUntilTheEndBan = server.getAuthenticationProvider().getMinutesUntilTheEndBan(nickname);
                        if (minutesUntilTheEndBan > 0) {
                            server.sendMessageToUser(Collections.singletonList(nickname), "Вы забанены. Данное действие будет доступно через "
                                    + minutesUntilTheEndBan + "(минут)");
                        } else {
                            List<String> userList = server.getUserList();
                            String joinedUsers = String.join(", ", userList);
                            atomicLastActivityTime.set(System.currentTimeMillis());
                            sendMessage(joinedUsers);
                        }
                    }
                    case WRITE -> {
                        long minutesUntilTheEndBan = server.getAuthenticationProvider().getMinutesUntilTheEndBan(nickname);
                        if (minutesUntilTheEndBan > 0) {
                            server.sendMessageToUser(Collections.singletonList(nickname), "Вы забанены. Данное действие будет доступно через "
                                    + minutesUntilTheEndBan + "(минут)");
                        } else {
                            if (splitMessage.length > 2) {
                                String recipient = splitMessage[1];
                                String messageToUser = convertArrayToString(Arrays.copyOfRange(splitMessage, 2, splitMessage.length));
                                LocalDateTime now = LocalDateTime.now();
                                String formatNowDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                atomicLastActivityTime.set(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                                server.sendMessageToUser(Arrays.asList(recipient, nickname), formatNowDateTime + " " + nickname + ": " + messageToUser);
                            }
                        }
                    }
                    case KICK -> {
                        if (server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
                            String nicknameForKick = splitMessage[1];
                            var clientForKick = server.getClientForKick(nicknameForKick);
                            if (clientForKick != null) {
                                clientForKick.disconnect();
                            }
                        }
                    }
                    case BAN -> {
                        if (server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
                            String nicknameForBan = splitMessage[1];
                            long minutesBan = 0L;
                            if (splitMessage.length > 2) {
                                minutesBan = Long.parseLong(splitMessage[2]);
                            }
                            server.getAuthenticationProvider().banUser(nicknameForBan, minutesBan);
                        }
                    }
                    default -> {
                        long minutesUntilTheEndBan = server.getAuthenticationProvider().getMinutesUntilTheEndBan(nickname);
                        if (minutesUntilTheEndBan > 0) {
                            server.sendMessageToUser(Collections.singletonList(nickname), "Вы забанены. Данное действие будет доступно через "
                                    + minutesUntilTheEndBan + "(минут)");
                        } else {
                            LocalDateTime now = LocalDateTime.now();
                            String formatNowDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                            atomicLastActivityTime.set(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                            server.broadcastMessage(formatNowDateTime + " " + nickname + ": " + message);
                        }
                    }
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