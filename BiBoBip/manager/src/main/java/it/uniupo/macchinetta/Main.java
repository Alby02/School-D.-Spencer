package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");

        SSLSocketFactory sslSocketFactory = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/client.p12", "");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(sslSocketFactory);

        try (Connection databaseConnection = setupDatabaseConnection();
             MqttClient mqttClient = new MqttClient(getEnv("MQTT_URL"), "manager")) {

            mqttClient.connect(options);
            AtomicReference<String> selectedBeverage = new AtomicReference<>();

            mqttClient.subscribe("keypad/bevanda", (topic, message) -> handleBeverageSelection(mqttClient, selectedBeverage, message));
            mqttClient.subscribe("bank/risposta", (topic, message) -> handleBankResponse(mqttClient, databaseConnection, selectedBeverage, message));

            keepRunning();

        } catch (SQLException | MqttException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static SSLSocketFactory createSSLSocketFactory(String caCertPath, String p12Path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(p12Path), password.toCharArray()); // Empty password

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(caCertPath)));
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext.getSocketFactory();
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
            System.err.println("Errore durante l'elaborazione della bevanda: " + e.getMessage());
        }
    }

    private static int fetchBeveragePrice(Connection dbConnection, String beverageId) throws SQLException {
        String query = "SELECT prezzo FROM prezzario WHERE bevanda_id = ?";
        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(beverageId));
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
