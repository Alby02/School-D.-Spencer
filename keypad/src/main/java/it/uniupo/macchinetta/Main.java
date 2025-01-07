package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("Benvenuto nella Macchinetta!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword);
             MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {

            mqttClient.connect();
            Scanner scanner = new Scanner(System.in); // Scanner definito una sola volta

            while (true) {
                Map<Integer, Integer> bevandeMap = stampaOpzioniBevande(databaseConnection);

                if (bevandeMap.isEmpty()) {
                    System.out.println("Nessuna bevanda disponibile al momento.");
                    break;
                }

                int scelta = leggiSceltaUtente(scanner); // Scanner passato come argomento
                if (scelta == -1) {
                    continue;
                }

                if (!bevandeMap.containsKey(scelta)) {
                    System.out.println("Selezione non valida. Riprova.");
                    continue;
                }

                int idBevanda = bevandeMap.get(scelta);
                double prezzo = recuperaPrezzoBevanda(databaseConnection, idBevanda);

                if (prezzo >= 0) {
                    System.out.println("Il prezzo della bevanda selezionata è: " + prezzo + "€");
                    inviaMessaggioMqtt(mqttClient, idBevanda);
                } else {
                    System.out.println("Errore: prezzo della bevanda non trovato.");
                }
            }

        } catch (SQLException e) {
            System.err.println("Errore nella connessione al database: " + e.getMessage());
        } catch (MqttException e) {
            System.err.println("Errore MQTT: " + e.getMessage());
        }
    }

    private static Map<Integer, Integer> stampaOpzioniBevande(Connection databaseConnection) throws SQLException {
        Map<Integer, Integer> bevandeMap = new HashMap<>();

        String query = "SELECT nome, id FROM bevande";
        try (Statement statement = databaseConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {

            int index = 1;
            System.out.println("Scegli una bevanda:");
            while (resultSet.next()) {
                String nome = resultSet.getString("nome");
                int id = resultSet.getInt("id");

                bevandeMap.put(index, id);
                System.out.println(index + ". " + nome);
                index++;
            }
        }

        return bevandeMap;
    }

    private static int leggiSceltaUtente(Scanner scanner) {
        System.out.print("Inserisci il numero della bevanda scelta: ");
        if (scanner.hasNextInt()) {
            return scanner.nextInt();
        } else {
            scanner.next(); // Consuma l'input non valido
            System.out.println("Input non valido. Inserisci un numero.");
            return -1; // Input non valido
        }
    }

    private static double recuperaPrezzoBevanda(Connection databaseConnection, int idBevanda) {
        String query = "SELECT prezzo FROM prezzario WHERE bevanda_id = ?";
        try (PreparedStatement preparedStatement = databaseConnection.prepareStatement(query)) {
            preparedStatement.setInt(1, idBevanda);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("prezzo");
                }
            }
        } catch (SQLException e) {
            System.err.println("Errore durante il recupero del prezzo: " + e.getMessage());
        }

        return -1; // Prezzo non trovato
    }

    private static void inviaMessaggioMqtt(MqttClient mqttClient, int idBevanda) {
        try {
            mqttClient.publish("keypad/bevanda", new MqttMessage(String.valueOf(idBevanda).getBytes()));
            System.out.println("Messaggio inviato per la bevanda ID: " + idBevanda);
        } catch (MqttException e) {
            System.err.println("Errore durante l'invio del messaggio MQTT: " + e.getMessage());
        }
    }
}
