package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.*;

import java.sql.*;
import java.util.Scanner;

public class Main {

    public static class Credito {
        private static int credito = 0;

        public static synchronized int getCredito() {
            return credito;
        }

        public static synchronized void setCredito(int credito) {
            Credito.credito = credito;
        }

        public static synchronized void incrementaCredito(int credito) {
            Credito.credito += credito;
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello, World!");

        // Recupero delle variabili d'ambiente per la configurazione
        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword)) {

            // Avvio del thread per la gestione delle richieste MQTT
            Thread mqttThread = new Thread(() -> handleMqttRequests(mqttUrl, databaseConnection));
            mqttThread.start();

            // Gestione dell'interazione con l'utente
            handleUserInput();

        } catch (SQLException e) {
            System.err.println("Errore durante la connessione al database: " + e.getMessage());
        }
    }

    private static void handleMqttRequests(String mqttUrl, Connection databaseConnection) {
        try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {
            mqttClient.connect();
            mqttClient.subscribe("bank/request", (topic, message) -> {
                String richiesta = new String(message.getPayload());
                try {
                    if ("vero".equals(richiesta)) {
                        // Invia il credito attuale
                        mqttClient.publish("bank/risposta", new MqttMessage(String.valueOf(Credito.getCredito()).getBytes()));
                    } else {
                        processRichiesta(Integer.parseInt(richiesta), databaseConnection);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Richiesta non valida: " + richiesta);
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore MQTT: " + e.getMessage());
        }
    }

    private static void processRichiesta(int richiesta, Connection databaseConnection) {
        try {
            Credito.incrementaCredito(-richiesta);
            System.out.println("Resto: " + Credito.getCredito() + " centesimi");

            try (Statement statement = databaseConnection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT valore, quantita FROM resto ORDER BY valore DESC");

                while (resultSet.next()) {
                    int valore = resultSet.getInt("valore");
                    int quantita = resultSet.getInt("quantita");
                    int resto = Credito.getCredito() / valore;

                    if (resto > 0) {
                        int monete = Math.min(resto, quantita);
                        Credito.incrementaCredito(-monete * valore);
                        statement.executeUpdate("UPDATE resto SET quantita = quantita - " + monete + " WHERE valore = " + valore);
                        System.out.println("Monete da " + valore + " centesimi: " + monete);
                    }
                }
            }

            System.out.println("Credito attuale: " + Credito.getCredito() + " centesimi");
        } catch (SQLException e) {
            System.err.println("Errore durante l'elaborazione del resto: " + e.getMessage());
        }
    }

    private static void handleUserInput() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Credito attuale: " + Credito.getCredito() + " centesimi");
            System.out.println("Inserisci moneta: 5 cent (1), 10 cent (2), 20 cent (3), 50 cent (4), 1 euro (5), 2 euro (6)");
            System.out.println("Premi 0 per restituire la moneta inserita");

            try {
                int scelta = scanner.nextInt();

                switch (scelta) {
                    case 1 -> Credito.incrementaCredito(5);
                    case 2 -> Credito.incrementaCredito(10);
                    case 3 -> Credito.incrementaCredito(20);
                    case 4 -> Credito.incrementaCredito(50);
                    case 5 -> Credito.incrementaCredito(100);
                    case 6 -> Credito.incrementaCredito(200);
                    case 0 -> Credito.setCredito(0);
                    default -> System.out.println("Inserimento non valido");
                }
            } catch (Exception e) {
                System.out.println("Errore di input. Inserire un numero valido.");
                scanner.next(); // Consuma l'input non valido
            }
        }
    }
}
