package it.uniupo.restfullMachineManager;

import java.sql.*;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import spark.Filter;
import spark.Spark;

public class Main {


    private static final Gson gson = new Gson();

    public static void main(String[] args) throws SQLException, MqttException {
        Spark.port(80);

        String postgresUrl = System.getenv("POSTGRES_URL");
        String postgresDatabase = System.getenv("POSTGRES_DATABASE");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String mqttUrl = System.getenv("MQTT_URL");

        Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDatabase,
                postgresUser, postgresPassword
        );

        MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId());
        mqttClient.connect();
        mqttClient.subscribe("assistance", (topic, message) -> {
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

        Spark.after((Filter) (request, response) -> {
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
        Spark.post("assistenza/cialde", (req,res) -> {
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

        /*Spark.get("/bevande", (req, res) -> {
            res.type("application/json");
            Map<String, Double> bevande = new HashMap<>();

            Statement stmt = databaseConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT nome, prezzo FROM bevande");

            while (rs.next()) {
                bevande.put(rs.getString("nome"), rs.getDouble("prezzo"));
            }

            return new Gson().toJson(bevande);
        });

        Spark.get("/cialde", (req, res) -> {
            res.type("application/json");
            Map<String, Integer> cialdeDisponibili = new HashMap<>();

            Statement stmt = databaseConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT tipo, quantita FROM cialde");

            while (rs.next()) {
                cialdeDisponibili.put(rs.getString("tipo"), rs.getInt("quantita"));
            }
            return new Gson().toJson(cialdeDisponibili);
        });

        Spark.post("/ricarica", (req, res) -> {
            String json = req.body();
            Map<String, Integer> ricarica = gson.fromJson(json, Map.class);

            PreparedStatement pstmt = databaseConnection.prepareStatement("UPDATE cialde SET quantita = ? WHERE tipo = ?");

            for (Map.Entry<String, Integer> entry : ricarica.entrySet()) {
                pstmt.setInt(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
            }
            return gson.toJson("Cialde ricaricate con successo");
        });

        Spark.post("/utilizzo", (req, res) -> {
            String json = req.body();
            Map<String, Integer> utilizzo = gson.fromJson(json, Map.class);

            PreparedStatement pstmt = databaseConnection.prepareStatement("UPDATE cialde SET quantita = quantita - ? WHERE tipo = ?");

            for (Map.Entry<String, Integer> entry : utilizzo.entrySet()) {
                pstmt.setInt(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
            }
            return gson.toJson("Cialde utilizzate con successo");

        });*/
    }
}