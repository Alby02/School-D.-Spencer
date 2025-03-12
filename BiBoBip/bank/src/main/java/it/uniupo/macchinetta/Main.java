package it.uniupo.macchinetta;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.security.KeyStore;
import java.sql.*;
import java.util.Scanner;

public class Main {
    public static final int SOFT_LIMIT;
    public static final int HARD_LIMIT;

    static {
        try {
            SOFT_LIMIT = caricaLimitiDaJson("/app/bank.json", "soft_limit");
            HARD_LIMIT = caricaLimitiDaJson("/app/bank.json", "hard_limit");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static int caricaLimitiDaJson(String filePath, String limit) throws FileNotFoundException {
        // Legge il JSON
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(new FileReader(filePath), JsonObject.class);

        // Estrae i valori dal JSON
        return jsonObject.get(limit).getAsInt();

    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");

        // Recupero delle variabili d'ambiente per la configurazione
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

            try (MqttClient mqttClient = new MqttClient(mqttUrl, "bank")) {
                mqttClient.connect(options);
                handleMqttRequests(mqttClient, databaseConnection);
                gestisciRicaricaResto(mqttClient, databaseConnection);
                gestisciSvuotamentoCassa(mqttClient, databaseConnection);

                // Gestione dell'interazione con l'utente
                handleUserInput(databaseConnection);

            } catch (MqttException e) {
                System.err.println("Errore MQTT: " + e.getMessage());
            }



        } catch (SQLException e) {
            System.err.println("Errore durante la connessione al database: " + e.getMessage());
        }
    }

    private static void gestisciSvuotamentoCassa(MqttClient mqttClient, Connection databaseConnection) {
        try {
            mqttClient.subscribe("assistance/cassa/svuotamento", (topic, message) -> {
                //manda il ricavo a assistance
                try (Statement statement = databaseConnection.createStatement()) {
                    ResultSet resultSet = statement.executeQuery("SELECT ricavo FROM cassa");
                    resultSet.next();
                    int ricavo = resultSet.getInt("ricavo");
                    mqttClient.publish("assistance/bank/ricavo", new MqttMessage(String.valueOf(ricavo).getBytes()));
                } catch (SQLException | MqttException e) {
                    System.err.println("Errore durante lo svuotamento della cassa: " + e.getMessage());
                }

                try (Statement statement = databaseConnection.createStatement()) {
                    statement.executeUpdate("UPDATE cassa SET ricavo = 0, monete = 0");
                    System.out.println("Cassa svuotata con successo");
                } catch (SQLException e) {
                    System.err.println("Errore durante lo svuotamento della cassa: " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore durante la sottoscrizione al topic 'assistance/cassa/svuotamento': " + e.getMessage());
        }
    }

    private static void gestisciRicaricaResto(MqttClient mqttClient, Connection databaseConnection) {
        try {
            mqttClient.subscribe("assistance/resto/ricarica", (topic, message) -> {
                try (Statement statement = databaseConnection.createStatement()) {
                    statement.execute("TRUNCATE TABLE resto");
                    inserisciRestoDaJson(databaseConnection, "/app/bank.json");
                    System.out.println("Ricarica del resto effettuata con successo");
                } catch (SQLException e) {
                    System.err.println("Errore durante la ricarica del resto: " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            System.err.println("Errore durante la sottoscrizione al topic 'assistance/resto/ricarica': " + e.getMessage());}
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

            if(verificaEsistenzaTabella(databaseConnection, "resto")) return;

            statement.execute("CREATE TABLE IF NOT EXISTS cassa (ricavo INT DEFAULT 0, monete INT DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS resto (valore INT PRIMARY KEY, quantita INT NOT NULL)");

            statement.executeUpdate("INSERT INTO cassa (ricavo, monete) VALUES (0, 0)");

            inserisciRestoDaJson(databaseConnection, "/app/bank.json");

        } catch (SQLException e) {
            System.err.println("Errore durante la creazione delle tabelle: " + e.getMessage());
        }
    }

    private static void inserisciRestoDaJson(Connection connection, String filePath) {
        try {
            // Legge il JSON
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(new FileReader(filePath), JsonObject.class);
            JsonArray restoArray = jsonObject.getAsJsonArray("resto");

            // Query di INSERT con ON CONFLICT DO UPDATE per aggiornare la quantità in caso di duplicato
            String insertQuery = "INSERT INTO resto (valore, quantita) VALUES (?, ?) ON CONFLICT (valore) DO NOTHING";

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                for (var element : restoArray) {
                    JsonObject restoItem = element.getAsJsonObject();

                    int valore = restoItem.get("valore").getAsInt();
                    int quantita = restoItem.get("quantita").getAsInt();

                    preparedStatement.setInt(1, valore);
                    preparedStatement.setInt(2, quantita);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                System.out.println("Dati inseriti nella tabella 'resto' con successo!");
            }
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento dei dati: " + e.getMessage());
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

    private static void handleMqttRequests(MqttClient mqttClient, Connection databaseConnection) throws MqttException {
        mqttClient.subscribe("bank/request", (topic, message) -> {
            String richiesta = new String(message.getPayload());
            try {
                if ("vero".equals(richiesta)) {
                    // Invia il credito attuale
                    mqttClient.publish("bank/risposta", new MqttMessage(String.valueOf(Coins.getInstance().getCredito()).getBytes()));
                } else {
                    processRichiesta(Integer.parseInt(richiesta), databaseConnection, mqttClient);
                }
            } catch (NumberFormatException e) {
                System.out.println("Richiesta non valida: " + richiesta);
            }
        });
    }

    private static void processRichiesta(int richiesta, Connection databaseConnection, MqttClient mqttClient) {
        try {
            Coins credito = Coins.getInstance();
            int dischargedCoins = credito.subtractAndDischarge(richiesta);
            System.out.println("Resto: " + credito.getCredito() + " centesimi");

            //salvo nel database della macchinetta richiesta che mi contiene il ricavo e dischargedCoins che mi contiene il numero di monete scaricate nella cassa
            try (PreparedStatement statement = databaseConnection.prepareStatement("UPDATE cassa SET ricavo = ricavo + ?, monete = monete + ?")) {
                statement.setInt(1, richiesta);
                statement.setInt(2, dischargedCoins);
                statement.executeUpdate();
            }
            //recupero il valore aggiornato di monete nella cassa e se è maggiore del soft limit invio una notifica all'assistance
            try (Statement statement = databaseConnection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT monete FROM cassa");
                resultSet.next();
                int monete = resultSet.getInt("monete");
                if (monete >= SOFT_LIMIT) {
                    mqttClient.publish("assistance/bank/cassa", new MqttMessage("Cassa da svuotare".getBytes()));
                    System.out.println("Invio notifica all'assist");
                }
            }

            boolean restoErogato = true;

            try (Statement statement = databaseConnection.createStatement()) {

                ResultSet resultSet = statement.executeQuery("SELECT valore, quantita FROM resto ORDER BY valore DESC");

                while (resultSet.next()) {
                    int valore = resultSet.getInt("valore");
                    int quantita = resultSet.getInt("quantita");
                    int resto = credito.getCredito() / valore;

                    if (resto > 0) {
                        int monete = Math.min(resto, quantita);
                        credito.changeCredit(monete * valore);
                        try(Statement statement1 = databaseConnection.createStatement()){
                            statement1.executeUpdate("UPDATE resto SET quantita = quantita - " + monete + " WHERE valore = " + valore);
                        }
                        System.out.println("Monete da " + valore + " centesimi: " + monete);
                    }
                }
                if (credito.getCredito() > 0){
                    restoErogato = false;
                    mqttClient.publish("assistance/bank/resto", new MqttMessage("Resto non erogato correttamente".getBytes()));
                    System.out.println("Invio notifica all'assistenza per il resto insufficiente");
                }
            }
            if (restoErogato){
                System.out.println("Resto erogato correttamente");
            }

            System.out.println("Credito attuale: " + credito.getCredito() + " centesimi");
        } catch (SQLException | MqttException e) {
            System.err.println("Errore durante l'elaborazione del resto: " + e.getMessage());
        }
    }

    private static void handleUserInput(Connection databaseConnection) {
        Scanner scanner = new Scanner(System.in);
        Coins credito = Coins.getInstance();

        while (true) {
            System.out.println("Credito attuale: " + credito.getCredito() + " centesimi");
            System.out.println("Inserisci moneta: 5 cent (1), 10 cent (2), 20 cent (3), 50 cent (4), 1 euro (5), 2 euro (6)");
            System.out.println("Premi 0 per restituire la moneta inserita");

            try {
                int scelta = scanner.nextInt();

                //Richiesta al database del numero di monete contenute nella cassa.
                try (Statement statement = databaseConnection.createStatement()) {
                    ResultSet resultSet = statement.executeQuery("SELECT monete FROM cassa");
                    resultSet.next();
                    int monete = resultSet.getInt("monete");
                    //Se la cassa ha un numero maggiore di monete nel soft limit e credito non ha monete la moneta non viene inserita
                    if (monete >= SOFT_LIMIT) {
                        System.out.println("La cassa è piena");
                        continue;
                    }
                    if (monete + credito.getMonetine() == HARD_LIMIT){
                        System.out.println("La cassa è piena");
                        continue;
                    }
                }
                switch (scelta) {
                    case 1 -> credito.insert5Cents();
                    case 2 -> credito.insert10Cents();
                    case 3 -> credito.insert20Cents();
                    case 4 -> credito.insert50Cents();
                    case 5 -> credito.insert1Euro();
                    case 6 -> credito.insert2Euros();
                    case 0 -> credito.emptyCoins();
                    default -> System.out.println("Inserimento non valido");
                }
            } catch (Exception e) {
                System.out.println("Errore di input. Inserire un numero valido.");
                e.printStackTrace();
            }
        }
    }
}
