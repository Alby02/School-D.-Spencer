package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.sql.*;

public class Main {
    public static void main(String[] args) throws SQLException {
        System.out.println("Hello, World!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");


        Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword
        );

        //iscrizione al topic di erogazione della bevanda(id della bevanda) richiede al database la ricetta e controlla se sono disponibili le cialde e se si eroga la bevanda(stampa a video) e decrementa il numero di cialde disponibili
        try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {
            mqttClient.connect();
            mqttClient.subscribe("bevanda/eroga", (topic, message) -> {
                String idBevanda = new String(message.getPayload());
                try {
                    PreparedStatement preparedStatement = databaseConnection.prepareStatement("SELECT * FROM ricette WHERE id = ?");
                    preparedStatement.setString(1, idBevanda);
                    ResultSet resultSet = preparedStatement.executeQuery();
                    //la ricetta può richiedere più cialde di diverso tipo per la stessa bevanda(devono essere tutte presenti per erogare la bevanda)
                    //nella tabella ricette le righe sono composte da id della ricetta, id della cialda e quantità richiesta
                    boolean erogabile = true;
                    while (resultSet.next()) {
                        String idCialda = resultSet.getString("id_cialda");
                        int quantita = resultSet.getInt("quantita");
                        PreparedStatement preparedStatement1 = databaseConnection.prepareStatement("SELECT quantita FROM cialde WHERE id = ?");
                        preparedStatement1.setString(1, idCialda);
                        ResultSet resultSet1 = preparedStatement1.executeQuery();
                        resultSet1.next();
                        int quantitaDisponibile = resultSet1.getInt("quantita");
                        if (quantitaDisponibile < quantita) {
                            erogabile = false;
                        }
                    }
                    //riporta all'inizio resultSet
                    resultSet.beforeFirst();
                    if (erogabile) {
                        System.out.println("Bevanda erogata");
                        while (resultSet.next()) {
                            String idCialda = resultSet.getString("id_cialda");
                            int quantita = resultSet.getInt("quantita");
                            PreparedStatement preparedStatement1 = databaseConnection.prepareStatement("UPDATE cialde SET quantita = quantita - ? WHERE id = ?");
                            preparedStatement1.setInt(1, quantita);
                            preparedStatement1.setString(2, idCialda);
                            preparedStatement1.executeUpdate();
                        }
                    }

                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }





    }
}
