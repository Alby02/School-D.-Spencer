package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        try (Connection databaseConnection = setupDatabaseConnection();
             MqttClient mqttClient = new MqttClient(getEnv("MQTT_URL"), MqttClient.generateClientId())) {

            mqttClient.connect();
            AtomicReference<String> selectedBeverage = new AtomicReference<>();

            mqttClient.subscribe("keypad/bevanda", (topic, message) -> handleBeverageSelection(mqttClient, selectedBeverage, message));
            mqttClient.subscribe("bank/risposta", (topic, message) -> handleBankResponse(mqttClient, databaseConnection, selectedBeverage, message));

            keepRunning();

        } catch (SQLException | MqttException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Connection setupDatabaseConnection() throws SQLException {
        String url = "jdbc:postgresql://" + getEnv("POSTGRES_URL") + "/" + getEnv("POSTGRES_DB");
        return DriverManager.getConnection(url, getEnv("POSTGRES_USER"), getEnv("POSTGRES_PASSWORD"));
    }

    private static void handleBeverageSelection(MqttClient mqttClient, AtomicReference<String> selectedBeverage, MqttMessage message) throws MqttException {
        String beverageId = new String(message.getPayload());
        selectedBeverage.set(beverageId);
        System.out.println("Bevanda selezionata: " + beverageId);
        mqttClient.publish("bank/request", new MqttMessage("vero".getBytes()));
    }

    private static void handleBankResponse(MqttClient mqttClient, Connection dbConnection, AtomicReference<String> selectedBeverage, MqttMessage message) {
        String credit = new String(message.getPayload());
        System.out.println("Credito: " + credit);

        try {
            int price = fetchBeveragePrice(dbConnection, selectedBeverage.get());
            if (Integer.parseInt(credit) >= price) {
                System.out.println("Credito sufficiente");
                mqttClient.publish("issuer/bevanda", new MqttMessage(selectedBeverage.get().getBytes()));
                mqttClient.publish("bank/request", new MqttMessage(String.valueOf(price).getBytes()));
            } else {
                System.out.println("Credito insufficiente");
            }
        } catch (SQLException | MqttException e) {
            throw new RuntimeException(e);
        }
    }

    private static int fetchBeveragePrice(Connection dbConnection, String beverageId) throws SQLException {
        String query = "SELECT prezzo FROM prezzario WHERE bevanda_id = ?";
        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setString(1, beverageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("prezzo");
            } else {
                throw new SQLException("Bevanda non trovata");
            }
        }
    }

    private static void keepRunning() throws InterruptedException {
        while (true) {
            Thread.sleep(1000);
        }
    }

    private static String getEnv(String key) {
        String value = System.getenv(key);
        if (value == null) {
            throw new IllegalArgumentException("Environment variable " + key + " not set");
        }
        return value;
    }
}
