package ru.project.chat.server;

import java.sql.SQLException;

public class ServerRunner {
    public static void main(String[] args) throws SQLException {
        Server server = new Server(8080, new DatabaseAuthenticationProvider());
        server.start();
    }
}