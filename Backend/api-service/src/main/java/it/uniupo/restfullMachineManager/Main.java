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

        Spark.after((Filter) (request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET");
        });

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