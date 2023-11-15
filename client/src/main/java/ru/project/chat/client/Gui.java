package ru.project.chat.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public class Gui extends JFrame implements Runnable {

    protected JTextArea outTextArea;
    protected JPanel southPanel;
    protected JTextField inTextField;
    protected JButton inTextSendButton;

    Network network;

    public Gui(String title, Network network) throws HeadlessException {
        super(title);
        southPanel = new JPanel();
        southPanel.setLayout(new GridLayout(2, 1, 10, 10));
        southPanel.add(inTextField = new JTextField());
        inTextField.setEditable(true);
        southPanel.add(inTextSendButton = new JButton("Send message"));
        Container cp = getContentPane();
        cp.setLayout(new BorderLayout());
        cp.add(BorderLayout.CENTER, outTextArea = new JTextArea());
        outTextArea.setEditable(false);
        cp.add(BorderLayout.SOUTH, southPanel);

        this.network = network;

        inTextSendButton.addActionListener(event ->
                {
                    String text = inTextField.getText();
                    try {
                        network.sendMessage(text);
                        inTextField.setText("");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        setSize(400, 500);
        setVisible(true);
        inTextField.requestFocus();
        (new Thread(this)).start();
        this.network.setCallback(new Callback() {
            @Override
            public void call(Object... args) {
                outTextArea.append((String) args[0]);
                outTextArea.append("\n");
            }

            @Override
            public void closeWindow() {
                System.out.println("closing window");
                network.close();
                dispose();
                System.exit(0);
            }
        });

        getRootPane().setDefaultButton(inTextSendButton);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("closing window");
                network.close();
                dispose();
                System.exit(0);
            }
        });
    }

    @Override
    public void run() {

    }
}