package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL_LOCAL");
        String mqttRemoteUrl = System.getenv("MQTT_URL_REMOTE");
        String idMacchina = System.getenv("ID_MACCHINA");

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword)) {

            try (MqttClient mqttLocalClient = new MqttClient(mqttUrl, MqttClient.generateClientId());
            MqttClient mqttRemoteClient = new MqttClient(mqttRemoteUrl, MqttClient.generateClientId())) {
                mqttLocalClient.connect();
                mqttLocalClient.subscribe("assistance/bank/cassa", (topic, message) -> {
                    System.out.println("Cassa: " + new String(message.getPayload()));
                    mqttRemoteClient.publish("assistance/" + idMacchina, new MqttMessage("Scassaie".getBytes()));
                });
                mqttLocalClient.subscribe("assistance/bank/resto", (topic, message) -> {
                    System.out.println("Resto: " + new String(message.getPayload()));
                    mqttRemoteClient.publish("assistance/" + idMacchina, new MqttMessage("Scassaie".getBytes()));
                });
                mqttLocalClient.subscribe("assistance/cialde", (topic, message) -> {
                    System.out.println("Bevanda: " + new String(message.getPayload()));
                    mqttRemoteClient.publish("assistance/" + idMacchina, new MqttMessage("Sbevanda".getBytes()));
                });
                //thread scassa macchina
                while (true) {
                    Thread.sleep(1000 * 60 * 5);
                    mqttRemoteClient.publish("assistance/" + idMacchina, new MqttMessage("Scassaie".getBytes()));
                }
            } catch (MqttException e) {
                System.err.println("Errore MQTT: " + e.getMessage());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        //
    }
}
