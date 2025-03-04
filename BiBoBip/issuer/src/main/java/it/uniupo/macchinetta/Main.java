package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.sql.*;

public class Main {
    public static final int SOFT_LIMIT = 2;
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        // Recupero delle variabili d'ambiente
        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword)) {

            try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {

                gestisciErogazioneBevande(databaseConnection, mqttClient);
                gestisciRicaricaCialde(databaseConnection, mqttClient);

                // wait indefinitely for the MQTT client to receive messages
                while (true) {
                    Thread.sleep(1000);
                }
            } catch (MqttException e) {
                System.err.println("Errore MQTT: " + e.getMessage());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        } catch (SQLException e) {
            System.err.println("Errore durante la connessione al database: " + e.getMessage());
        }
    }

    private static void gestisciErogazioneBevande(Connection databaseConnection, MqttClient mqttClient) throws MqttException {

        mqttClient.connect();
        mqttClient.subscribe("issuer/bevanda", (topic, message) -> {
            String idBevanda = new String(message.getPayload());
            try {
                if (erogaBevanda(databaseConnection, idBevanda, mqttClient)) {
                    System.out.println("Bevanda erogata con successo!");
                    mqttClient.publish("manager/bevanda", new MqttMessage("Erogazione avvenuta con successo".getBytes()));
                } else {
                    mqttClient.publish("manager/bevanda", new MqttMessage("Gnoooooooooo bevanda non erogata".getBytes()));
                }
            } catch (SQLException e) {
                System.err.println("Errore durante l'elaborazione della bevanda: " + e.getMessage());
            }
        });
    }

    private static void gestisciRicaricaCialde(Connection databaseConnection, MqttClient mqttClient) throws MqttException {
        mqttClient.subscribe("assistance/cialde/ricarica", (topic, message) -> {
            try {
                try (PreparedStatement cialdeStmt = databaseConnection.prepareStatement(
                        "UPDATE cialde SET quantita = 50")) {
                    cialdeStmt.executeUpdate();
                    System.out.println("Ricarica cialde effettuata con successo!");
                }
            } catch (SQLException e) {
                System.err.println("Errore durante la ricarica delle cialde: " + e.getMessage());
            }
        });
    }

    private static boolean erogaBevanda(Connection databaseConnection, String idBevanda, MqttClient mqttClient) throws SQLException, MqttException {
        // Verifica se la bevanda è erogabile
        if (!isBevandaErogabile(databaseConnection, idBevanda, mqttClient)) {
            return false;
        }

        // Aggiorna il database e decrementa le cialde utilizzate
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id = ?")) {

            ricettaStmt.setInt(1, Integer.parseInt(idBevanda));
            try (ResultSet ricettaResult = ricettaStmt.executeQuery()) {
                while (ricettaResult.next()) {
                    String idCialda = ricettaResult.getString("id_cialda");
                    int quantita = ricettaResult.getInt("quantita");
                    try (PreparedStatement updateStmt = databaseConnection.prepareStatement(
                            "SELECT nome FROM cialde WHERE id = ?")) {
                        updateStmt.setInt(1, Integer.parseInt(idCialda));
                        try (ResultSet updateResult = updateStmt.executeQuery()) {
                            updateResult.next();
                            String nomeCialda = updateResult.getString("nome");
                            System.out.println("Erogazione di " + quantita + " cialde " + nomeCialda);
                        }
                    }
                    try (PreparedStatement updateStmt = databaseConnection.prepareStatement(
                            "UPDATE cialde SET quantita = quantita - ? WHERE id = ?")) {
                        updateStmt.setInt(1, quantita);
                        updateStmt.setString(2, idCialda);
                        updateStmt.executeUpdate();
                    }
                }
            }
        }

        return true;
    }

    private static boolean isBevandaErogabile(Connection databaseConnection, String idBevanda, MqttClient mqttClient) throws SQLException, MqttException {
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id = ?")) {

            ricettaStmt.setInt(1, Integer.parseInt(idBevanda));
            try (ResultSet ricettaResult = ricettaStmt.executeQuery()) {
                while (ricettaResult.next()) {
                    String idCialda = ricettaResult.getString("id_cialda");
                    int quantitaRichiesta = ricettaResult.getInt("quantita");

                    try (PreparedStatement cialdaStmt = databaseConnection.prepareStatement(
                            "SELECT quantita FROM cialde WHERE id = ?")) {
                        cialdaStmt.setInt(1, Integer.parseInt(idCialda));

                        try (ResultSet cialdaResult = cialdaStmt.executeQuery()) {
                            if (cialdaResult.next()) {
                                int quantitaDisponibile = cialdaResult.getInt("quantita");
                                if (quantitaDisponibile < quantitaRichiesta) {
                                    mqttClient.publish("assistance/cialde", new MqttMessage("Errore".getBytes()));
                                    return false; // Cialde insufficienti
                                }
                            } else {
                                mqttClient.publish("assistance/cialde", new MqttMessage("Errore".getBytes()));
                                return false; // Cialda non trovata
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
