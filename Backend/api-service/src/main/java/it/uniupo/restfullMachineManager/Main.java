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

        try{
            Connection databaseConnection = DriverManager.getConnection(postgresFullUrl, postgresUser, postgresPassword);
            MqttClient mqttRemoteClient = getMqttClient(mqttUrl, databaseConnection);
            Spark.after((request, response) -> {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE");
                response.header("Access-Control-Allow-Credentials", "true");
                response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Accept,Origin");
            });
/*
            Spark.before((req, res) -> {
                System.out.println("====== Incoming Request ======");
                System.out.println("Method: " + req.requestMethod());
                System.out.println("URL: " + req.url());
                System.out.println("Query Params: " + req.queryString());
                System.out.println("Protocol: " + req.protocol());
                System.out.println("IP: " + req.ip());
                System.out.println("Host: " + req.host());
                System.out.println("Port: " + req.port());
                System.out.println("User-Agent: " + req.userAgent());
                System.out.println("Referrer: " + req.headers("Referer"));
                System.out.println("Content-Length: " + req.contentLength());

                // Log headers
                System.out.println("Headers:");
                req.headers().forEach(header -> System.out.println("  " + header + ": " + req.headers(header)));

                // Log cookies
                System.out.println("Cookies:");
                req.cookies().forEach((name, value) -> System.out.println("  " + name + ": " + value));

                // Read body (only if present)
                if (req.body() != null && !req.body().isEmpty()) {
                    System.out.println("Body: " + req.body());
                }

                System.out.println("==============================");
            });

            Spark.after((req, res) -> {
                System.out.println("====== Response Sent ======");
                System.out.println("Status Code: " + res.status());
                System.out.println("Response Headers:");
                System.out.println("  Content-Type: " + res.type());
                System.out.println("  Set-Cookies: " + res.raw().getHeader("Set-Cookie"));
                System.out.println("===========================");
            });

            Spark.before((req, res)->{
                if (!KeycloakAuthMiddleware.authenticate(req, res))
                    Spark.halt(401, "Non autorizzato");
            });
*/

            //spark get per recuperare le informazioni universita dal database
            Spark.get("/universita", (req, res) -> {
                try {
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
                }
                catch (Exception e) {
                    System.err.println("Errore " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
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

            //spark post per aggiungere macchinette nel database
            Spark.post("/macchinette", (req, res) -> {
                res.type("application/json");

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String nomeMacchinetta = body.get("nome");
                String idUni = body.get("id_uni");

                PreparedStatement stmt = databaseConnection.prepareStatement("INSERT INTO macchinette (nome, id_uni) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, nomeMacchinetta);
                stmt.setInt(2, Integer.parseInt(idUni));

                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        Map<String, String> response = new HashMap<>();
                        response.put("id", generatedKeys.getString(1));
                        return gson.toJson(response);
                    }
                }

                res.status(400);
                return gson.toJson("Errore durante l'aggiunta della macchinetta");
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

            //spark post per inviare il messaggio ad assistance per il resto
            Spark.post("assistenza/resto", (req, res) -> {
                res.type("application/json");

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String idMacchina = body.get("id_macchina");

                if (idMacchina == null) {
                    res.status(400);
                    return gson.toJson("Errore: ID macchinetta non fornito");
                }

                System.out.println("Richiesta di assistenza per il resto segnalata per la macchina: " + idMacchina);

                try {
                    mqttRemoteClient.publish("assistance/resto/ricarica", new MqttMessage(idMacchina.getBytes()));
                    System.out.println("Messaggio di assistenza per il resto inviato per la macchina " + idMacchina);
                } catch (MqttException e) {
                    System.err.println("Errore nell'invio del messaggio MQTT: " + e.getMessage());
                    res.status(500);
                    return gson.toJson("Errore nell'invio del messaggio");
                }

                return gson.toJson("Richiesta inviata con successo");
            });

            //spark post per inviare il messaggio ad assistance per la cassa
            Spark.post("assistenza/cassa", (req, res) -> {
                res.type("application/json");

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String idMacchina = body.get("id_macchina");

                if (idMacchina == null) {
                    res.status(400);
                    return gson.toJson("Errore: ID macchinetta non fornito");
                }

                System.out.println("Richiesta di assistenza per la cassa segnalata per la macchina: " + idMacchina);

                try {
                    mqttRemoteClient.publish("assistance/cassa/svuotamento", new MqttMessage(idMacchina.getBytes()));
                    System.out.println("Messaggio di assistenza per la cassa inviato per la macchina " + idMacchina);
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

        //ricevo messaggio mqtt dell'assistance per la cassa
        mqttRemoteClient.subscribe("service/assistance/cassa", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);

            //controlla se la macchinetta esiste nella tabella "macchinette"
            PreparedStatement pstmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id = ?");
            pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));

            //se esiste inserisci un messaggio di errore nel database
            if (pstmt.executeQuery().getInt(1) == 1) {
                pstmt = databaseConnection.prepareStatement("INSERT INTO assistenza (macchinetta_id, messaggio) VALUES (?, ?)");
                pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));
                pstmt.setString(2, "Cassa piena");
                pstmt.executeUpdate();
            } else{
                System.out.println("Macchinetta non trovata");
            }
        });

        //ricevo messaggio mqtt dell'assistance per il resto
        mqttRemoteClient.subscribe("service/assistance/resto", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);

            //controlla se la macchinetta esiste nella tabella "macchinette"
            PreparedStatement pstmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id = ?");
            pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));

            //se esiste inserisci un messaggio di errore nel database
            if (pstmt.executeQuery().getInt(1) == 1) {
                pstmt = databaseConnection.prepareStatement("INSERT INTO assistenza (macchinetta_id, messaggio) VALUES (?, ?)");
                pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));
                pstmt.setString(2, "Resto non erogato");
                pstmt.executeUpdate();
            } else{
                System.out.println("Macchinetta non trovata");
            }
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