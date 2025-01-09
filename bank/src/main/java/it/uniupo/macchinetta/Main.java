package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.*;

import java.sql.*;
import java.util.Scanner;

public class Main {
    public static final int SOFT_LIMIT = 10;
    public static final int HARD_LIMIT = 15;

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
            handleUserInput(databaseConnection);

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
                        mqttClient.publish("bank/risposta", new MqttMessage(String.valueOf(Coins.getInstance().getCredito()).getBytes()));
                    } else {
                        processRichiesta(Integer.parseInt(richiesta), databaseConnection, mqttClient);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Richiesta non valida: " + richiesta);
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore MQTT: " + e.getMessage());
        }
    }

    private static void processRichiesta(int richiesta, Connection databaseConnection, MqttClient mqttClient) {
        try {
            Coins credito = Coins.getInstance();
            int dischargedCoins = credito.subtractAndDischarge(richiesta);
            System.out.println("Resto: " + credito.getCredito() + " centesimi");

            //salvo nel database della macchinetta richiesta che mi contiene il ricavo e dischargedCoins che mi contiene il numero di monete scaricate nella cassa
            try (PreparedStatement statement = databaseConnection.prepareStatement("UPDATE cassa SET ricavo = ricavo + ?, monete = monete + ?")) {
                statement.setInt(1, richiesta);
                statement.setInt(2, dischargedCoins);
                statement.executeUpdate();
            }
            //recupero il valore aggiornato di monete nella cassa e se è maggiore del soft limit invio una notifica all'assistance
            try (Statement statement = databaseConnection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT monete FROM cassa");
                resultSet.next();
                int monete = resultSet.getInt("monete");
                if (monete >= SOFT_LIMIT) {
                    mqttClient.publish("assistance/bank/cassa", new MqttMessage("Cassa da svuotare".getBytes()));
                    System.out.println("Invio notifica all'assist");
                }
            }

            boolean restoErogato = true;

            try (Statement statement = databaseConnection.createStatement()) {

                ResultSet resultSet = statement.executeQuery("SELECT valore, quantita FROM resto ORDER BY valore DESC");

                while (resultSet.next()) {
                    int valore = resultSet.getInt("valore");
                    int quantita = resultSet.getInt("quantita");
                    int resto = credito.getCredito() / valore;

                    if (resto > 0) {
                        int monete = Math.min(resto, quantita);
                        credito.changeCredit(monete * valore);
                        statement.executeUpdate("UPDATE resto SET quantita = quantita - " + monete + " WHERE valore = " + valore);
                        System.out.println("Monete da " + valore + " centesimi: " + monete);
                    }
                }
                if (credito.getCredito() > 0){
                    restoErogato = false;
                    mqttClient.publish("assistance/bank/resto", new MqttMessage("Resto non erogato correttamente".getBytes()));
                    System.out.println("Invio notifica all'assistenza per il resto insufficiente");
                }
            }
            if (restoErogato){
                System.out.println("Resto erogato correttamente");
            }

            System.out.println("Credito attuale: " + credito.getCredito() + " centesimi");
        } catch (SQLException | MqttException e) {
            System.err.println("Errore durante l'elaborazione del resto: " + e.getMessage());
        }
    }

    private static void handleUserInput(Connection databaseConnection) {
        Scanner scanner = new Scanner(System.in);
        Coins credito = Coins.getInstance();

        while (true) {
            System.out.println("Credito attuale: " + credito.getCredito() + " centesimi");
            System.out.println("Inserisci moneta: 5 cent (1), 10 cent (2), 20 cent (3), 50 cent (4), 1 euro (5), 2 euro (6)");
            System.out.println("Premi 0 per restituire la moneta inserita");

            try {
                int scelta = scanner.nextInt();

                //Richiesta al database del numero di monete contenute nella cassa.
                try (Statement statement = databaseConnection.createStatement()) {
                    ResultSet resultSet = statement.executeQuery("SELECT monete FROM cassa");
                    resultSet.next();
                    int monete = resultSet.getInt("monete");
                    //Se la cassa ha un numero maggiore di monete nel soft limit e credito non ha monete la moneta non viene inserita
                    if (monete >= SOFT_LIMIT) {
                        System.out.println("La cassa è piena");
                        continue;
                    }
                    if (monete + credito.getMonetine() == HARD_LIMIT){
                        System.out.println("La cassa è piena");
                        continue;
                    }
                }
                switch (scelta) {
                    case 1 -> credito.insert5Cents();
                    case 2 -> credito.insert10Cents();
                    case 3 -> credito.insert20Cents();
                    case 4 -> credito.insert50Cents();
                    case 5 -> credito.insert1Euro();
                    case 6 -> credito.insert2Euros();
                    case 0 -> credito.emptyCoins();
                    default -> System.out.println("Inserimento non valido");
                }
            } catch (Exception e) {
                System.out.println("Errore di input. Inserire un numero valido.");
                scanner.next(); // Consuma l'input non valido
            }
        }
    }
}
