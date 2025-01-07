package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.sql.*;

public class Main {
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

            gestisciErogazioneBevande(databaseConnection, mqttUrl);

        } catch (SQLException e) {
            System.err.println("Errore durante la connessione al database: " + e.getMessage());
        }
    }

    private static void gestisciErogazioneBevande(Connection databaseConnection, String mqttUrl) {
        try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {
            mqttClient.connect();
            mqttClient.subscribe("issuer/bevanda", (topic, message) -> {
                String idBevanda = new String(message.getPayload());
                try {
                    if (erogaBevanda(databaseConnection, idBevanda)) {
                        System.out.println("Bevanda erogata con successo!");
                    } else {
                        System.out.println("Bevanda non erogabile: mancano le cialde necessarie.");
                    }
                } catch (SQLException e) {
                    System.err.println("Errore durante l'elaborazione della bevanda: " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore MQTT: " + e.getMessage());
        }
    }

    private static boolean erogaBevanda(Connection databaseConnection, String idBevanda) throws SQLException {
        // Verifica se la bevanda è erogabile
        if (!isBevandaErogabile(databaseConnection, idBevanda)) {
            return false;
        }

        // Aggiorna il database e decrementa le cialde utilizzate
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id = ?")) {

            ricettaStmt.setString(1, idBevanda);
            try (ResultSet ricettaResult = ricettaStmt.executeQuery()) {
                while (ricettaResult.next()) {
                    String idCialda = ricettaResult.getString("id_cialda");
                    int quantita = ricettaResult.getInt("quantita");

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

    private static boolean isBevandaErogabile(Connection databaseConnection, String idBevanda) throws SQLException {
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id = ?")) {

            ricettaStmt.setString(1, idBevanda);
            try (ResultSet ricettaResult = ricettaStmt.executeQuery()) {
                while (ricettaResult.next()) {
                    String idCialda = ricettaResult.getString("id_cialda");
                    int quantitaRichiesta = ricettaResult.getInt("quantita");

                    try (PreparedStatement cialdaStmt = databaseConnection.prepareStatement(
                            "SELECT quantita FROM cialde WHERE id = ?")) {
                        cialdaStmt.setString(1, idCialda);

                        try (ResultSet cialdaResult = cialdaStmt.executeQuery()) {
                            if (cialdaResult.next()) {
                                int quantitaDisponibile = cialdaResult.getInt("quantita");
                                if (quantitaDisponibile < quantitaRichiesta) {
                                    return false; // Cialde insufficienti
                                }
                            } else {
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
