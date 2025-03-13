package src;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;

public class MonsterSender {

    //private static String url = ActiveMQConnection.DEFAULT_BROKER_URL;
    private static String url = "tcp://192.168.100.12:61616";
    // default broker URL is : tcp://localhost:61616"
    private static String subject = "Monsters"; // Topic Name. You can create any/many topic names as per your requirement.
    private final int k = 1000;
    private static final int WIN_CONDITION = 5;

    private ConcurrentHashMap<String, Integer> playerScore = new ConcurrentHashMap<>();
    private MessageProducer producer;
    private Session session;
    private boolean gameRunning = true;

    public MonsterSender() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
            Connection connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createTopic(subject);
            producer = session.createProducer(destination);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void startGame() {
        new Thread(() -> {
            int id = 0;
            while (gameRunning) {
                try {
                    int x = (int) (Math.random() * 9);
                    int y = (int) (Math.random() * 9);
                    sendMonster(id, x, y);
                    id++;
                    Thread.sleep(k);
                } catch (InterruptedException | JMSException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendMonster(int id, int x, int y) throws JMSException {
        TextMessage message = session.createTextMessage(id + " " + x + " " + y);
        producer.send(message);
        System.out.println("Sending monster ID: " + id + " in position: " + x + ", " + y);
    }

    private void sendWinner(String player) throws JMSException {
        TextMessage message = session.createTextMessage("WINNER " + player);
        producer.send(message);
        System.out.println(player + " won the game");
    }

    public void startTCPServer(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("TCP Server started with port " + port);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new PlayerHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private class PlayerHandler implements Runnable {
        private Socket socket;
        private String playerName;

        public PlayerHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("WELCOME TO THE MONSTERS");
                out.println("Write your name");
                playerName = in.readLine();
                playerScore.putIfAbsent(playerName, 0);
                out.println("Welcome " + playerName + "!");
                String input;
                while ((input = in.readLine()) != null) {
                    if (input.equalsIgnoreCase("exit")) break;
                    if (input.startsWith("hit")) {
                        processHit(playerName, input);
                    }
                }
                socket.close();
            } catch (IOException | JMSException e) {
                e.printStackTrace();
            }
        }
    }

    private void processHit(String playerName, String input) throws JMSException {
        String[] tokens = input.split(" ");
        if (tokens.length == 3) {
            int id = Integer.parseInt(tokens[1]);
            long time = Long.parseLong(tokens[2]);

            int newScore = playerScore.get(playerName) + 1;
            playerScore.put(playerName, newScore);
            System.out.println(playerName + " has scored " + id + " on " + time + "ms. Points: " + newScore);
            if (newScore >= WIN_CONDITION) {
                sendWinner(playerName);
                resetGame();
            }
        }
    }

    private void resetGame() {
        playerScore.clear();
        System.out.println("Restarting game...");
    }


    public static void main(String[] args) {
        MonsterSender sender = new MonsterSender();
        sender.startTCPServer(5000);
        sender.startGame();
    }
}