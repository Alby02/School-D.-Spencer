package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws SQLException, MqttException {
        System.out.println("Hello, World!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword
        );

        while (true) {

            Statement statement = databaseConnection.createStatement();
            // Mappa per memorizzare l'ordine e l'id delle bevande
            Map<Integer, Integer> bevandeMap = new HashMap<>();

            // 1. Stampare opzioni disponibili con numerazione incrementale
            ResultSet resultSet = statement.executeQuery("SELECT nome, id FROM bevande");
            int index = 1;
            System.out.println("Scegli una bevanda:");

            while (resultSet.next()) {
                String nome = resultSet.getString("nome");
                int id = resultSet.getInt("id");

                // Assegna l'id all'indice incrementale
                bevandeMap.put(index, id);

                // Stampa l'opzione con numerazione
                System.out.println(index + ". " + nome);
                index++;
            }

            // 2. Leggere input utente
            Scanner scanner = new Scanner(System.in);
            System.out.print("Inserisci il numero della bevanda scelta: ");
            int sceltaNumero = scanner.nextInt();

            // 3. Recuperare l'id dalla mappa
            Integer sceltaId = bevandeMap.get(sceltaNumero);

            if (sceltaId != null) {
                // 4. Query per recuperare il prezzo
                PreparedStatement prezzoStmt = databaseConnection.prepareStatement(
                        "SELECT prezzo FROM prezzario WHERE bevanda_id = ?"
                );
                prezzoStmt.setInt(1, sceltaId);
                ResultSet prezzoResult = prezzoStmt.executeQuery();

                if (prezzoResult.next()) {
                    double prezzo = prezzoResult.getDouble("prezzo");
                    System.out.println("Il prezzo della bevanda selezionata è: " + prezzo + "€");
                    // 5. Invio messaggio MQTT
                    try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {
                        mqttClient.connect();
                        mqttClient.publish("macchinetta", new byte[]{(byte) sceltaId.intValue()}, 0, false);
                    }
                } else {
                    System.out.println("Bevanda non trovata nel prezzario.");
                }

                prezzoResult.close();
                prezzoStmt.close();
            } else {
                System.out.println("Selezione non valida.");
            }

            // Chiudere risorse
            resultSet.close();
            statement.close();
        }
    }
}
