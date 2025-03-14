package it.uniupo.macchinetta;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.KeyStore;
import java.sql.*;

public class Main {
    public static final int SOFT_LIMIT = 2;
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");

        // Recupero delle variabili d'ambiente
        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        SSLSocketFactory sslSocketFactory = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/client.p12", "");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(sslSocketFactory);

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword)) {

            setupDatabase(databaseConnection);

            try (MqttClient mqttClient = new MqttClient(mqttUrl, "issuer")) {
                mqttClient.connect(options);
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

    private static void setupDatabase(Connection databaseConnection) {
        try (Statement statement = databaseConnection.createStatement()) {

            if(verificaEsistenzaTabella(databaseConnection, "cialde")) return;

            statement.execute("CREATE TABLE IF NOT EXISTS cialde (id INT PRIMARY KEY,nome TEXT NOT NULL,quantita INT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS ricette (id_ricetta INT NOT NULL, id_cialda INT NOT NULL, quantita INT NOT NULL, PRIMARY KEY (id_ricetta, id_cialda))");

            inserisciCialdeDaJson(databaseConnection, "/app/issuer.json");
            inserisciRicetteDaJson(databaseConnection, "/app/issuer.json");


        } catch (SQLException e) {
            System.err.println("Errore durante la creazione delle tabelle: " + e.getMessage());
        }
    }

    private static void inserisciCialdeDaJson(Connection connection, String filePath) {
        try {
            // Legge il JSON
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(new FileReader(filePath), JsonObject.class);
            JsonArray cialdeArray = jsonObject.getAsJsonArray("cialde");

            String insertCialdeQuery = "INSERT INTO cialde (id, nome, quantita) VALUES (?, ?, ?)";

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertCialdeQuery)) {
                for (var element : cialdeArray) {
                    JsonObject cialda = element.getAsJsonObject();

                    int id = cialda.get("id").getAsInt();
                    String nome = cialda.get("nome").getAsString();
                    int quantita = cialda.get("quantita").getAsInt();

                    preparedStatement.setInt(1, id);
                    preparedStatement.setString(2, nome);
                    preparedStatement.setInt(3, quantita);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                System.out.println("Cialde inserite con successo!");
            }
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento delle cialde: " + e.getMessage());
        }
    }

    private static void inserisciRicetteDaJson(Connection connection, String filePath) {
        try {
            // Legge il JSON
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(new FileReader(filePath), JsonObject.class);
            JsonArray ricetteArray = jsonObject.getAsJsonArray("ricette");

            String insertRicetteQuery = "INSERT INTO ricette (id_ricetta, id_cialda, quantita) VALUES (?, ?, ?)";

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertRicetteQuery)) {
                for (var element : ricetteArray) {
                    JsonObject ricetta = element.getAsJsonObject();

                    int idRicetta = ricetta.get("id_ricetta").getAsInt();
                    int idCialda = ricetta.get("id_cialda").getAsInt();
                    int quantita = ricetta.get("quantita").getAsInt();

                    preparedStatement.setInt(1, idRicetta);
                    preparedStatement.setInt(2, idCialda);
                    preparedStatement.setInt(3, quantita);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                System.out.println("Ricette inserite con successo!");
            }
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento delle ricette: " + e.getMessage());
        }
    }

    public static boolean verificaEsistenzaTabella(Connection connection, String tableName) throws SQLException {
        String query = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, tableName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1); // Restituisce true se la tabella esiste
                }
            }
        }
        return false; // Se non trova nulla, la tabella non esiste
    }

    private static void gestisciErogazioneBevande(Connection databaseConnection, MqttClient mqttClient) throws MqttException {
        mqttClient.subscribe("issuer/bevanda", (topic, message) -> {
            String idBevanda = new String(message.getPayload());
            try {
                erogaBevanda(databaseConnection, idBevanda, mqttClient);
            } catch (SQLException e) {
                System.err.println("Errore durante l'elaborazione della bevanda: " + e.getMessage());
            }
        });
    }

    private static void gestisciRicaricaCialde(Connection databaseConnection, MqttClient mqttClient) throws MqttException {
        try {
            mqttClient.subscribe("assistance/cialde/ricarica", (topic, message) -> {
                try (Statement statement = databaseConnection.createStatement()){
                    statement.execute("TRUNCATE TABLE cialde");
                    inserisciCialdeDaJson(databaseConnection, "/app/issuer.json");
                    System.out.println("Cialde ricaricate con successo!");
                }
                catch (SQLException e) {
                    System.err.println("Errore durante la ricarica delle cialde: " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore durante la sottoscrizione al topic 'assistance/cialde/ricarica': " + e.getMessage());}

    }

    private static void erogaBevanda(Connection databaseConnection, String idBevanda, MqttClient mqttClient) throws SQLException, MqttException {
        // Verifica se la bevanda è erogabile
        if (!isBevandaErogabile(databaseConnection, idBevanda, mqttClient)) {
            return;
        }

        // Aggiorna il database e decrementa le cialde utilizzate
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id_ricetta = ?")) {

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
                        updateStmt.setInt(2, Integer.parseInt(idCialda));
                        updateStmt.executeUpdate();
                    }
                }
            }
        }
        System.out.println("Bevanda erogata con successo!");
        mqttClient.publish("manager/bevanda", new MqttMessage("Successo".getBytes()));
    }

    private static boolean isBevandaErogabile(Connection databaseConnection, String idBevanda, MqttClient mqttClient) throws SQLException, MqttException {
        try (PreparedStatement ricettaStmt = databaseConnection.prepareStatement(
                "SELECT id_cialda, quantita FROM ricette WHERE id_ricetta = ?")) {

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
