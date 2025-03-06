package it.uniupo.restfullMachineManager;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.sql.*;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import spark.Spark;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class Main {


    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        Spark.port(443);
        Spark.secure("/app/certs/https.p12", "", "/app/certs/ca.p12", "");

        String postgresUrl = System.getenv("POSTGRES_URL");
        String postgresDatabase = System.getenv("POSTGRES_DATABASE");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String mqttUrl = System.getenv("MQTT_URL");

        String postgresFullUrl = "jdbc:postgresql://" + postgresUrl + "/" + postgresDatabase;

        try (Connection databaseConnection = DriverManager.getConnection(postgresFullUrl, postgresUser, postgresPassword)) {
            MqttClient mqttRemoteClient = getMqttClient(mqttUrl, databaseConnection);
            Spark.after((request, response) -> {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET");
            });

            //spark get per recuperare le informazioni universita dal database
            Spark.get("/universita", (req, res) -> {
                res.type("application/json");
                ArrayList<HashMap<String, String>> universita = new ArrayList<>();

                Statement stmt = databaseConnection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM universita");

                while (rs.next()) {
                    HashMap<String, String> uni = new HashMap<>();
                    uni.put("id", rs.getInt("id") + "");
                    uni.put("nome", rs.getString("nome"));
                    universita.add(uni);
                }

                return new Gson().toJson(universita);
            });

            //spark get per recuperare le informazioni macchinette dal database
        Spark.get("/macchinette/:id", (req, res) -> {
            res.type("application/json");
            ArrayList<HashMap<String, String>> macchinette = new ArrayList<>();
            String uniId = req.params(":id");

            PreparedStatement stmt = databaseConnection.prepareStatement("SELECT * FROM macchinette WHERE id_uni = ?");
            stmt.setInt(1, Integer.parseInt(uniId));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                HashMap<String, String> macchinetta = new HashMap<>();
                macchinetta.put("id", rs.getInt("id") + "");
                macchinetta.put("id_uni", rs.getInt("id_uni") + "");
                macchinetta.put("nome", rs.getString("nome"));
                macchinette.add(macchinetta);
            }

            return new Gson().toJson(macchinette);
        });

        //spark post per inviare il messaggio ad assistance per la ricarica delle cialde
            Spark.post("assistenza/cialde", (req, res) -> {
                res.type("application/json");

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String idMacchina = body.get("id_macchina");

                if (idMacchina == null) {
                    res.status(400);
                    return gson.toJson("Errore: ID macchinetta non fornito");
                }

                System.out.println("Ricarica segnalata per la macchina: " + idMacchina);

                try {
                    mqttRemoteClient.publish("assistance/cialde/ricarica", new MqttMessage(idMacchina.getBytes()));
                    System.out.println("Messaggio di ricarica inviato per la macchina " + idMacchina);
                } catch (MqttException e) {
                    System.err.println("Errore nell'invio del messaggio MQTT: " + e.getMessage());
                    res.status(500);
                    return gson.toJson("Errore nell'invio del messaggio");
                }

                return gson.toJson("Richiesta inviata con successo");
            });
        }catch (Exception e){
            System.err.println("Errore " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static MqttClient getMqttClient(String mqttUrl, Connection databaseConnection) throws Exception {

        SSLSocketFactory sslSocketFactory = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/mqtt.p12", "");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(sslSocketFactory);

        MqttClient mqttRemoteClient = new MqttClient(mqttUrl, "api-service");
        mqttRemoteClient.connect(options);
        mqttRemoteClient.subscribe("assistance", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);
        });

        //ricevo messaggio mqtt dell'assistance per le cialde
        mqttRemoteClient.subscribe("service/assistance/cialde", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);

            //controlla se la macchinetta esiste nella tabella "macchinette"
            PreparedStatement pstmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id = ?");
            pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));

            //se esiste inserisci un messaggio di errore nel database
            if (pstmt.executeQuery().getInt(1) == 1) {
                pstmt = databaseConnection.prepareStatement("INSERT INTO assistenza (macchinetta_id, messaggio) VALUES (?, ?)");
                pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));
                pstmt.setString(2, "Cialde esaurite");
                pstmt.executeUpdate();
            } else{
                System.out.println("Macchinetta non trovata");
            }
        });
        return mqttRemoteClient;
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