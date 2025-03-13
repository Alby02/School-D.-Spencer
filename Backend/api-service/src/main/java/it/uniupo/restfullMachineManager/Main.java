package it.uniupo.restfullMachineManager;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.sql.*;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
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
            //crea tabella macchinette e universita e guadagni
            try {
                Statement stmt = databaseConnection.createStatement();
                stmt.execute("CREATE TABLE IF NOT EXISTS universita (id SERIAL PRIMARY KEY, nome TEXT NOT NULL, guadagno INT DEFAULT 0)");
                stmt.execute("CREATE TABLE IF NOT EXISTS macchinette (id VARCHAR(10) PRIMARY KEY, id_uni INT NOT NULL, nome TEXT NOT NULL, guadagno INT DEFAULT 0, cassa_piena BOOLEAN DEFAULT FALSE, no_resto BOOLEAN DEFAULT FALSE, no_cialde BOOLEAN DEFAULT FALSE, rotta BOOLEAN DEFAULT FALSE, FOREIGN KEY (id_uni) REFERENCES universita(id))");
                stmt.execute("CREATE TABLE IF NOT EXISTS guadagni (id SERIAL PRIMARY KEY, id_macchinetta VARCHAR(10) NOT NULL, data DATE NOT NULL, guadagno INT NOT NULL)");
            } catch (SQLException e) {
                System.err.println("Errore durante la creazione delle tabelle: " + e.getMessage());
                e.printStackTrace();
            }
            Spark.after((request, response) -> {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE");
                response.header("Access-Control-Allow-Credentials", "true");
                response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Accept,Origin");
            });

            //Token autetication Keycloak
            Spark.before((req, res)->{
                if (!KeycloakAuthMiddleware.authenticate(req, res)) {
                    System.out.println("Non autorizzato");
                    Spark.halt(401, "Non autorizzato");
                }
                System.out.println("Request authorized");
            });

            Spark.exception(Exception.class, (e, request, response) ->{
                e.printStackTrace();
            });

            //spark get per recuperare le informazioni universita dal database
            Spark.get("/universita", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("manager")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

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
            Spark.post("/universita", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("admin")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String nomeUniversita = body.get("nome");

                PreparedStatement stmt = databaseConnection.prepareStatement("INSERT INTO universita (nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, nomeUniversita);

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
                return gson.toJson("Errore durante l'aggiunta dell'universita");
            });

            //spark delete per rimuovere universita dal database con controllo presenza macchinette e amministratore
            Spark.delete("/universita/:id", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("admin")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

                String idUniversita = req.params(":id");

                try {
                    PreparedStatement checkStmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id_uni = ?");
                    checkStmt.setInt(1, Integer.parseInt(idUniversita));
                    ResultSet rs = checkStmt.executeQuery();
                    rs.next();
                    int count = rs.getInt(1);

                    if (count > 0) {
                        res.status(400);
                        return gson.toJson("Errore: L'università ha Macchinette e non può essere eliminata.");
                    }

                    PreparedStatement stmt = databaseConnection.prepareStatement("DELETE FROM universita WHERE id = ?");
                    stmt.setInt(1, Integer.parseInt(idUniversita));

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        return gson.toJson("Università rimossa con successo");
                    } else {
                        res.status(404);
                        return gson.toJson("Università non trovata");
                    }
                } catch (Exception e) {
                    System.err.println("Errore durante la rimozione dell'università: " + e.getMessage());
                    res.status(500);
                    return gson.toJson("Errore durante la rimozione dell'università");
                }
            });

            //spark get per recuperare le informazioni macchinette dal database
            Spark.get("/macchinette/:id", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("manager")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

                ArrayList<HashMap<String, String>> macchinette = new ArrayList<>();
                String uniId = req.params(":id");

                PreparedStatement stmt = databaseConnection.prepareStatement("SELECT * FROM macchinette WHERE id_uni = ?");
                stmt.setInt(1, Integer.parseInt(uniId));
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    HashMap<String, String> macchinetta = new HashMap<>();
                    macchinetta.put("id", rs.getString("id"));
                    macchinetta.put("id_uni", rs.getInt("id_uni") + "");
                    macchinetta.put("nome", rs.getString("nome"));
                    macchinetta.put("no_resto", rs.getBoolean("no_resto")+ "");
                    macchinetta.put("cassa_piena", rs.getBoolean("cassa_piena")+ "");
                    macchinetta.put("no_cialde", rs.getBoolean("no_cialde")+ "");
                    macchinetta.put("rotta", rs.getBoolean("rotta")+ "");
                    macchinette.add(macchinetta);
                }

                return new Gson().toJson(macchinette);
            });

            //spark post per aggiungere macchinette nel database
            Spark.post("/macchinette", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("admin")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String id = body.get("id");
                String idUni = body.get("id_uni");
                String nomeMacchinetta = body.get("nome");

                PreparedStatement stmt = databaseConnection.prepareStatement("INSERT INTO macchinette (id, id_uni, nome) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, id);
                stmt.setInt(2, Integer.parseInt(idUni));
                stmt.setString(3, nomeMacchinetta);

                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    res.status(HttpServletResponse.SC_OK);
                    return gson.toJson(Map.of("message", "Università aggiunta con successo"));
                } else {
                    res.status(HttpServletResponse.SC_BAD_REQUEST);
                    return gson.toJson(Map.of("error", "Errore durante l'aggiunta dell'università"));
                }
            });

            //spark delete per rimuovere macchinette dal database
            Spark.delete("/macchinette/:id", (req, res) -> {
                res.type("application/json");

                List<String> roles = req.attribute("roles");

                if (roles == null || !roles.contains("admin")) {
                    res.status(403);
                    return gson.toJson(Map.of("message", "Accesso negato: Solo un amministratore può rimuovere un'università."));
                }

                String idMacchinetta = req.params(":id");

                try {
                    PreparedStatement stmt = databaseConnection.prepareStatement("DELETE FROM macchinette WHERE id = ?");
                    stmt.setString(1, idMacchinetta);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        return gson.toJson("Macchinetta rimossa con successo");
                    } else {
                        res.status(404);
                        return gson.toJson("Macchinetta non trovata");
                    }
                } catch (Exception e) {
                    System.err.println("Errore durante la rimozione della macchinetta: " + e.getMessage());
                    e.printStackTrace();
                    res.status(500);
                    return gson.toJson("Errore durante la rimozione della macchinetta");
                }
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

                    PreparedStatement stmt = databaseConnection.prepareStatement(
                            "UPDATE macchinette SET no_cialde = FALSE WHERE id = ?"
                    );
                    stmt.setString(1, idMacchina);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        System.out.println("Stato della macchinetta aggiornato con successo.");
                    } else {
                        System.err.println("Macchinetta non trovata per l'aggiornamento.");
                        res.status(404);
                        return gson.toJson("Macchinetta non trovata");
                    }
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

                    PreparedStatement stmt = databaseConnection.prepareStatement(
                            "UPDATE macchinette SET no_resto = FALSE WHERE id = ?"
                    );
                    stmt.setString(1, idMacchina);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        System.out.println("Stato della macchinetta aggiornato con successo.");
                    } else {
                        System.err.println("Macchinetta non trovata per l'aggiornamento.");
                        res.status(404);
                        return gson.toJson("Macchinetta non trovata");
                    }
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

                    PreparedStatement stmt = databaseConnection.prepareStatement(
                            "UPDATE macchinette SET cassa_piena = FALSE WHERE id = ?"
                    );
                    stmt.setString(1, idMacchina);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        System.out.println("Stato della macchinetta aggiornato con successo.");
                    } else {
                        System.err.println("Macchinetta non trovata per l'aggiornamento.");
                        res.status(404);
                        return gson.toJson("Macchinetta non trovata");
                    }
                } catch (MqttException e) {
                    System.err.println("Errore nell'invio del messaggio MQTT: " + e.getMessage());
                    res.status(500);
                    return gson.toJson("Errore nell'invio del messaggio");
                }

                return gson.toJson("Richiesta inviata con successo");
            });

            //spark post per inviare il messaggio ad assistance per il guasto
            Spark.post("assistenza/guasto", (req, res) -> {
                res.type("application/json");

                Map<String, String> body = gson.fromJson(req.body(), Map.class);
                String idMacchina = body.get("id_macchina");

                if (idMacchina == null) {
                    res.status(400);
                    return gson.toJson("Errore: ID macchinetta non fornito");
                }

                System.out.println("Richiesta di assistenza per la cassa segnalata per la macchina: " + idMacchina);

                try {
                    mqttRemoteClient.publish("assistance/guasto/riparazione", new MqttMessage(idMacchina.getBytes()));
                    System.out.println("Messaggio di assistenza per il guasto inviato per la macchina " + idMacchina);

                    PreparedStatement stmt = databaseConnection.prepareStatement(
                            "UPDATE macchinette SET rotta = FALSE WHERE id = ?"
                    );
                    stmt.setString(1, idMacchina);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        System.out.println("Stato della macchinetta aggiornato con successo.");
                    } else {
                        System.err.println("Macchinetta non trovata per l'aggiornamento.");
                        res.status(404);
                        return gson.toJson("Macchinetta non trovata");
                    }
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
                pstmt = databaseConnection.prepareStatement("UPDATE macchinette SET cassa_piena = ? WHERE id = ?");
                pstmt.setBoolean(1, true);
                pstmt.setString(2, new String(message.getPayload()));
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
                pstmt = databaseConnection.prepareStatement("UPDATE macchinette SET no_resto = ? WHERE id = ?");
                pstmt.setBoolean(1, true);
                pstmt.setString(2, new String(message.getPayload()));
                pstmt.executeUpdate();
            } else{
                System.out.println("Macchinetta non trovata");
            }
        });

        //ricevo messaggio mqtt dell'assistance contenente "id_macchinetta" - "ricavo"
        mqttRemoteClient.subscribe("service/assistance/ricavo", (topic, message) -> {
            String[] parts = new String(message.getPayload()).split("-");
            System.out.println("Assistenza richiesta: " + parts[1] + " su topic " + topic);

            //aggiungi riga alla tabella dei guadagni con il ricavo della macchinetta
            PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO guadagni (id_macchinetta, data, guadagno) VALUES (?, ?, ?)");
            statement.setString(1, parts[0]);
            statement.setDate(2, new Date(System.currentTimeMillis()));
            statement.setInt(3, Integer.parseInt(parts[1]));
            statement.executeUpdate();

            PreparedStatement pstmt = databaseConnection.prepareStatement("UPDATE macchinette SET guadagno = guadagno + ? WHERE id = ? ");
            pstmt.setString(1, parts[0]);
            pstmt.setInt(2, Integer.parseInt(parts[1]));
            pstmt.executeUpdate();

            PreparedStatement statement1 = databaseConnection.prepareStatement("UPDATE universita SET guadagno = guadagno + ? WHERE id = (SELECT id_uni FROM macchinette WHERE id = ?)");
            statement1.setInt(1, Integer.parseInt(parts[1]));
            statement1.setString(2, parts[0]);
            statement1.executeUpdate();

        });

        //ricevo messaggio mqtt dell'assistance per le cialde
        mqttRemoteClient.subscribe("service/assistance/cialde", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);

            //controlla se la macchinetta esiste nella tabella "macchinette"
            PreparedStatement pstmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id = ?");
            pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));

            //se esiste inserisci un messaggio di errore nel database
            if (pstmt.executeQuery().getInt(1) == 1) {
                pstmt = databaseConnection.prepareStatement("UPDATE macchinette SET no_cialde = ? WHERE id = ?");
                pstmt.setBoolean(1, true);
                pstmt.setString(2, new String(message.getPayload()));
                pstmt.executeUpdate();
            } else{
                System.out.println("Macchinetta non trovata");
            }
        });

        //ricevo messaggio mqtt dell'assistance per il guasto
        mqttRemoteClient.subscribe("service/assistance/guasto", (topic, message) -> {
            System.out.println("Assistenza richiesta: " + new String(message.getPayload()) + " su topic " + topic);

            //controlla se la macchinetta esiste nella tabella "macchinette"
            PreparedStatement pstmt = databaseConnection.prepareStatement("SELECT COUNT(*) FROM macchinette WHERE id = ?");
            pstmt.setInt(1, Integer.parseInt(new String(message.getPayload())));

            //se esiste inserisci un messaggio di errore nel database
            if (pstmt.executeQuery().getInt(1) == 1) {
                pstmt = databaseConnection.prepareStatement("UPDATE macchinette SET rotta = ? WHERE id = ?");
                pstmt.setBoolean(1, true);
                pstmt.setString(2, new String(message.getPayload()));
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