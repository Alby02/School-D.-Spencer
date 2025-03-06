package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL_LOCAL");
        String mqttRemoteUrl = System.getenv("MQTT_URL_REMOTE");
        String idMacchina = System.getenv("ID_MACCHINA");

        SSLSocketFactory sslSocketFactory_local = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/client.p12", "");
        MqttConnectOptions options_local = new MqttConnectOptions();
        options_local.setSocketFactory(sslSocketFactory_local);

        SSLSocketFactory sslSocketFactory_remote = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/remote.p12", "");
        MqttConnectOptions options_remote = new MqttConnectOptions();
        options_remote.setSocketFactory(sslSocketFactory_remote);

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword)) {

            try (MqttClient mqttLocalClient = new MqttClient(mqttUrl, "assistance");
            MqttClient mqttRemoteClient = new MqttClient(mqttRemoteUrl, idMacchina)) {
                mqttLocalClient.connect(options_local);
                mqttRemoteClient.connect(options_remote);
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
                    mqttRemoteClient.publish("service/assistance/cialde", new MqttMessage(idMacchina.getBytes()));
                });
                mqttRemoteClient.subscribe("assistance/cialde/ricarica", (topic, message) -> {
                    System.out.println("Ricarica cialde: " + new String(message.getPayload()));
                    mqttLocalClient.publish("assistance/cialde/ricarica", new MqttMessage("Ricarica".getBytes()));
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
}
