package it.uniupo.restfullMachineManager;

import java.sql.*;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import static spark.Spark.*;

public class Main {


    private static final Gson gson = new Gson();

    public static void main(String[] args) throws SQLException {
        port(8888);

        Configs configs = new Configs("config.properties");
        String DB_URL = "jdbc:postgresql://"+ configs.getPostgresUrl() +":"+ configs.getPostgresPort() +"/" + configs.getPostgresDatabase();

        Connection databaseConnection = DriverManager.getConnection(DB_URL, configs.getPostgresUser(), configs.getPostgresPassword());

        get("/bevande", (req, res) -> {
            res.type("application/json");
            Map<String, Double> bevande = new HashMap<>();

            Statement stmt = databaseConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT nome, prezzo FROM bevande");

            while (rs.next()) {
                bevande.put(rs.getString("nome"), rs.getDouble("prezzo"));
            }

            return new Gson().toJson(bevande);
        });

        get("/cialde", (req, res) -> {
            res.type("application/json");
            Map<String, Integer> cialdeDisponibili = new HashMap<>();

            Statement stmt = databaseConnection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT tipo, quantita FROM cialde");

            while (rs.next()) {
                cialdeDisponibili.put(rs.getString("tipo"), rs.getInt("quantita"));
            }
            return new Gson().toJson(cialdeDisponibili);
        });

        post("/ricarica", (req, res) -> {
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

        post("/utilizzo", (req, res) -> {
            String json = req.body();
            Map<String, Integer> utilizzo = gson.fromJson(json, Map.class);

            PreparedStatement pstmt = databaseConnection.prepareStatement("UPDATE cialde SET quantita = quantita - ? WHERE tipo = ?");

            for (Map.Entry<String, Integer> entry : utilizzo.entrySet()) {
                pstmt.setInt(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
            }
            return gson.toJson("Cialde utilizzate con successo");

        });
    }
}