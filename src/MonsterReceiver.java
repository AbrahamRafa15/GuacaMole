package src;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class MonsterReceiver {
    private static final String Broker_url = "tcp://192.168.100.12:61616"; //Cambiar la IP dependiendo del server
    private static final String TOPIC_NAME = "Monsters";
    private static final String SERVER_IP = "192.168.100.12";
    private static final int SERVER_PORT = 5000;

    private JFrame frame;
    private JButton[][] buttons = new JButton[9][9];
    private String playerName;
    private Socket socket;
    private PrintWriter out;

    public MonsterReceiver() {
        playerName = JOptionPane.showInputDialog("Enter Player Name:");
        if(playerName == null || playerName.isEmpty()) {
            System.exit(0);
        }
        connectToServer();

        createUI();

        subscribeToTopic();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(playerName);
            System.out.println("Connected as: " + playerName);

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("WINNER")){
                            JOptionPane.showMessageDialog(frame, "Winner: " + message.split(" ")[1] + "!");
                            resetBoard();
                        }
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }).start();
        }
        catch (IOException e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Could not connect to server!");
            System.exit(1);
        }
    }

    private void createUI() {
        frame = new JFrame("Hit the Monsters - " + playerName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 500);
        frame.setLayout(new GridLayout(9, 9));

        for(int i = 0; i < 9; i++) {
            for(int j = 0; j < 9; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setEnabled(false);
                buttons[i][j].addActionListener(new ButtonClickListener(i,j));
                frame.add(buttons[i][j]);
            }
        }
        frame.setVisible(true);
    }

    private void subscribeToTopic() {
        new Thread(() -> {
            try {
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(Broker_url);
                Connection connection = connectionFactory.createConnection();
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(TOPIC_NAME);
                MessageConsumer consumer = session.createConsumer(destination);

                consumer.setMessageListener(message -> {
                    if (message instanceof TextMessage) {
                        try{
                            String text = ((TextMessage) message).getText();
                            if(text.equals("The game is over!")){
                                JOptionPane.showMessageDialog(frame, "The game is over!");
                                resetBoard();
                            } else if (text.startsWith("WINNER")) {
                                JOptionPane.showMessageDialog(frame, "Winner: " + text.split(" ")[1] + "!");
                                resetBoard();
                            }
                            else {
                                String[] parts = text.split(" ");
                                int id = Integer.parseInt(parts[0]);
                                int x = Integer.parseInt(parts[1]);
                                int y = Integer.parseInt(parts[2]);
                                showMonster(x,y);
                            }
                        }
                        catch (JMSException e){
                            e.printStackTrace();
                        }
                    }
                });
            }
            catch (JMSException e){
                e.printStackTrace();
            }
        }).start();
    }

    private void showMonster(int x, int y) {
        SwingUtilities.invokeLater(() -> {
            buttons[x][y].setText("👾");
            buttons[x][y].setEnabled(true);
        });
    }

    private void hideMonster(int x, int y) {
        SwingUtilities.invokeLater(() -> {
            buttons[x][y].setText("");
            buttons[x][y].setEnabled(false);
        });
    }

    private void resetBoard() {
        SwingUtilities.invokeLater(() -> {
            for(int i = 0; i < 9; i++) {
                for(int j = 0; j < 9; j++) {
                    buttons[i][j].setText("");
                    buttons[i][j].setEnabled(false);
                }
            }
        });
    }

    private class ButtonClickListener implements ActionListener {
        private final int x, y;
        public ButtonClickListener(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long reactionTime = System.currentTimeMillis();
            out.println("Hit the Monster " + x + " " + reactionTime);
            hideMonster(x, y);
        }
    }

    public static void main(String[] args) {
        new MonsterReceiver();
    }

}
