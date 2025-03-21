package it.uniupo.macchinetta;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.security.KeyStore;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Benvenuto nella Macchinetta!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        SSLSocketFactory sslSocketFactory = createSSLSocketFactory("/app/certs/ca.crt","/app/certs/client.p12", "");
        MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(sslSocketFactory);

        try (Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword);
             MqttClient mqttClient = new MqttClient(mqttUrl, "keypad")) {

            setupDatabase(databaseConnection);

            mqttClient.connect(options);
            Scanner scanner = new Scanner(System.in); // Scanner definito una sola volta

            Thread.sleep(3000L);

            while (true) {
                ArrayList<Integer> bevande = stampaOpzioniBevande(databaseConnection);

                if (bevande.isEmpty()) {
                    System.out.println("Nessuna bevanda disponibile al momento.");;
                }

                int scelta = leggiSceltaUtente(scanner); // Scanner passato come argomento
                if (scelta == -1) {
                    continue;
                }

                if  (scelta < 1 || scelta > bevande.size()) {
                    System.out.println("Selezione non valida. Riprova.");
                    continue;
                }

                int idBevanda = bevande.get(scelta - 1);
                double prezzo = recuperaPrezzoBevanda(databaseConnection, idBevanda);

                if (prezzo >= 0) {
                    System.out.println("Il prezzo della bevanda selezionata è: " + ((int)prezzo/100) + "." + ((int)prezzo%100) + "€");
                    inviaMessaggioMqtt(mqttClient, idBevanda);
                } else {
                    System.out.println("Errore: prezzo della bevanda non trovato.");
                }
            }

        } catch (SQLException e) {
            System.err.println("Errore nella connessione al database: " + e.getMessage());
        } catch (MqttException e) {
            System.err.println("Errore MQTT: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

            if(verificaEsistenzaTabella(databaseConnection, "bevande")) return;

            statement.execute("CREATE TABLE bevande (id SERIAL PRIMARY KEY, nome TEXT NOT NULL, prezzo INTEGER NOT NULL)");

            inserisciBevandeDaJson(databaseConnection, "/app/keypad.json");


        } catch (SQLException e) {
            System.err.println("Errore durante la creazione delle tabelle: " + e.getMessage());
        }
    }

    private static void inserisciBevandeDaJson(Connection connection, String filePath) {
        try {
            // Legge il JSON
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(new FileReader(filePath), JsonObject.class);
            JsonArray bevandeArray = jsonObject.getAsJsonArray("bevande");

            // Query di INSERT con ON CONFLICT per evitare duplicati
            String query = "INSERT INTO bevande (id, nome, prezzo) VALUES (?, ?, ?)";

            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                for (var element : bevandeArray) {
                    JsonObject bevanda = element.getAsJsonObject();

                    int id = bevanda.get("id").getAsInt();
                    String nome = bevanda.get("nome").getAsString();
                    int prezzo = bevanda.get("prezzo").getAsInt();

                    preparedStatement.setInt(1, id);
                    preparedStatement.setString(2, nome);
                    preparedStatement.setInt(3, prezzo);
                    preparedStatement.addBatch(); // Aggiunge alla batch query
                }

                preparedStatement.executeBatch(); // Esegue tutte le INSERT in batch
                System.out.println("Bevande inserite correttamente!");

            }
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento delle bevande: " + e.getMessage());
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


    private static ArrayList<Integer> stampaOpzioniBevande(Connection databaseConnection) throws SQLException {
        ArrayList<Integer> bevande = new ArrayList<>();

        String query = "SELECT nome, id FROM bevande";
        try (Statement statement = databaseConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            System.out.println("Scegli una bevanda:");
            while (resultSet.next()) {
                String nome = resultSet.getString("nome");
                int id = resultSet.getInt("id");

                bevande.add(id);
                System.out.println(bevande.size() + ". " + nome);
            }
        }

        return bevande;
    }

    private static int leggiSceltaUtente(Scanner scanner) {
        System.out.print("Inserisci il numero della bevanda scelta: ");
        if (scanner.hasNextInt()) {
            return scanner.nextInt();
        } else {
            scanner.next(); // Consuma l'input non valido
            System.out.println("Input non valido. Inserisci un numero.");
            return -1; // Input non valido
        }
    }

    private static double recuperaPrezzoBevanda(Connection databaseConnection, int idBevanda) {
        String query = "SELECT prezzo FROM bevande WHERE id = ?";
        try (PreparedStatement preparedStatement = databaseConnection.prepareStatement(query)) {
            preparedStatement.setInt(1, idBevanda);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("prezzo");
                }
            }
        } catch (SQLException e) {
            System.err.println("Errore durante il recupero del prezzo: " + e.getMessage());
        }

        return -1; // Prezzo non trovato
    }

    private static void inviaMessaggioMqtt(MqttClient mqttClient, int idBevanda) {
        try {
            mqttClient.publish("keypad/bevanda", new MqttMessage(String.valueOf(idBevanda).getBytes()));
            System.out.println("Messaggio inviato per la bevanda ID: " + idBevanda);
        } catch (MqttException e) {
            System.err.println("Errore durante l'invio del messaggio MQTT: " + e.getMessage());
        }
    }
}
