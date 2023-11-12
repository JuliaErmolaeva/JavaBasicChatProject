package ru.project.chat.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Network implements AutoCloseable {
    private Socket socket;

    private DataInputStream in;
    private DataOutputStream out;

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void connect(int port) throws IOException {
        socket = new Socket("localhost", port);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            try {
                while (true) {
                    String message = in.readUTF();
                    if (callback != null) {
                        callback.call(message);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                close();
            }
        }).start();
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String msg) throws IOException {
        out.writeUTF(msg);
    }
}