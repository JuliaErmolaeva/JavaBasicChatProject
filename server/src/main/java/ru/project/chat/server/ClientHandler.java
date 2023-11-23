package ru.project.chat.server;

import lombok.extern.log4j.Log4j2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
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

@Log4j2
public class ClientHandler {
    private Socket socket;

    private Server server;
    private DataInputStream in;
    private DataOutputStream out;

    private boolean isAuthenticated = false;

    private String nickname;

    private static final long TIME_TO_WAIT_ACTIVITY = 1_200_000L; // 20 минут в миллисекундах

    private AtomicLong atomicLastActivityTime = new AtomicLong();

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                authenticateUser();
                checkUserActivity();
                communicateWithUser();
            } catch (EOFException e) {
                log.warn("End of file reached");
            } catch (IOException e) {
                log.warn(e.getMessage());
            } finally {
                disconnect();
            }
        }).start();
    }

    private void checkUserActivity() {
        if (!server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
            new Thread(() -> {
                try {
                    while (true) {
                        long lastActivityTime = atomicLastActivityTime.get();
                        if (System.currentTimeMillis() - lastActivityTime >= TIME_TO_WAIT_ACTIVITY) {
                            log.info("Превышено время ожидания действий от клиента " + nickname);
                            disconnect();
                            break;
                        }
                        Thread.sleep(10000L);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    disconnect();
                }
            }).start();
        }
    }

    private void authenticateUser() throws IOException {
        while (!isAuthenticated) {
            String message = in.readUTF();
            String[] splitMessage = message.split(" ");
            String command = splitMessage[0];

            switch (command) {
                case AUTH -> executeAuthCommand(splitMessage);
                case REGISTER -> executeRegisterCommand(splitMessage);
                default -> sendMessage("Авторизуйтесь сперва");
            }
        }
    }

    private void executeAuthCommand(String[] splitMessage) {
        String login = splitMessage[1];
        String password = splitMessage[2];
        String nickname = server.getAuthenticationProvider().getNicknameByLoginAndPassword(login, password);
        if (nickname == null || nickname.isBlank()) {
            sendMessage("Указан неверный логин/пароль");
        } else {
            successAuthenticate(nickname);
        }
    }

    private void executeRegisterCommand(String[] splitMessage) {
        String login = splitMessage[1];
        String nickname = splitMessage[2];
        String password = splitMessage[3];
        boolean isRegistered = server.getAuthenticationProvider().register(login, password, nickname);
        if (!isRegistered) {
            sendMessage("Указанный логин/никнейм уже заняты");
        } else {
            successAuthenticate(nickname);
        }
    }

    private void successAuthenticate(String name) {
        this.nickname = name;
        sendMessage(nickname + ", добро пожаловать в чат!");
        server.subscribe(this);
        isAuthenticated = true;
        atomicLastActivityTime.set(System.currentTimeMillis());
    }

    private void communicateWithUser() throws IOException {
        while (true) {
            String message = in.readUTF();
            if (message.length() > 0) {
                String[] splitMessage = message.split(" ");
                String command = splitMessage[0];

                if (command.equals(EXIT)) {
                    break;
                }

                long minutesUntilTheEndBan = server.getAuthenticationProvider().getMinutesUntilTheEndBan(nickname);
                if (minutesUntilTheEndBan > 0) {
                    server.sendMessageToUser(Collections.singletonList(nickname), "Вы забанены. Данное действие будет доступно через "
                            + minutesUntilTheEndBan + "(минут)");
                    break;
                }

                switch (command) {
                    case LIST -> executeCommandList();
                    case WRITE -> executeCommandWrite(splitMessage);
                    case CHANGE_NICK -> executeCommandChangeNick(splitMessage);
                    case KICK -> executeCommandKick(splitMessage);
                    case BAN -> executeCommandBan(splitMessage);
                    case SHUTDOWN -> executeCommandShutdown();
                    default -> executeBroadcastMessage(message);
                }
            }
        }
    }

    private void executeCommandList() {
        List<String> userList = server.getUserList();
        String joinedUsers = String.join(", ", userList);
        atomicLastActivityTime.set(System.currentTimeMillis());
        sendMessage(joinedUsers);
    }

    private void executeCommandWrite(String[] splitMessage) {
        if (splitMessage.length > 2) {
            String recipient = splitMessage[1];
            String messageToUser = convertArrayToString(Arrays.copyOfRange(splitMessage, 2, splitMessage.length));
            LocalDateTime now = LocalDateTime.now();
            String formatNowDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            atomicLastActivityTime.set(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            server.sendMessageToUser(Arrays.asList(recipient, nickname), formatNowDateTime + " " + nickname + ": " + messageToUser);
        }
    }

    private void executeCommandChangeNick(String[] splitMessage) {
        if (splitMessage.length == 2) {
            String oldNickname = nickname;
            String newNickname = splitMessage[1];
            boolean successChangeNickname = server.changeNickname(this, newNickname);
            if (successChangeNickname) {
                server.broadcastMessage("Пользователь с ником " + oldNickname + " сменил ник на " + newNickname);
            }
        }
    }

    private void executeCommandKick(String[] splitMessage) {
        if (server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
            String nicknameForKick = splitMessage[1];
            var clientForKick = server.getClientForKick(nicknameForKick);
            if (clientForKick != null) {
                clientForKick.disconnect();
            }
        }
    }

    private void executeCommandBan(String[] splitMessage) {
        if (server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
            String nicknameForBan = splitMessage[1];
            long minutesBan = 0L;
            if (splitMessage.length > 2) {
                minutesBan = Long.parseLong(splitMessage[2]);
            }
            server.getAuthenticationProvider().banUser(nicknameForBan, minutesBan);
        }
    }

    private void executeCommandShutdown() throws IOException {
        if (server.getAuthenticationProvider().isCurrentUserAdmin(nickname)) {
            server.shutdownServer();
        }
    }

    private void executeBroadcastMessage(String message) {
        LocalDateTime now = LocalDateTime.now();
        String formatNowDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        atomicLastActivityTime.set(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        server.broadcastMessage(formatNowDateTime + " " + nickname + ": " + message);
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
        closeResources();
    }

    public void closeResources() {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
            if (socket != null) {
                socket.close();
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}