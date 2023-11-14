package ru.project.chat.server;

public interface AuthenticationProvider {
    String getNicknameByLoginAndPassword(String login, String password);

    boolean register(String login, String password, String nickname);

    // Является ли пользователь администратором
    boolean isCurrentUserAdmin(String nickname);

    // Забанить пользователя
    void banUser(String nicknameForBan, long minutesBan);

    // Получение времени до конца бана
    long getMinutesUntilTheEndBan(String nickname);
}