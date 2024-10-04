package it.uniupo.restfullMachineManager;

import java.sql.*;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import static spark.Spark.*;
import io.github.cdimascio.dotenv.Dotenv;

public class Main {

    private static final Dotenv dotenv = Dotenv.configure().directory("./back/").load();
    private static final String DB_URL = "jdbc:postgresql://postgres-network:5432/" + dotenv.get("POSTGRES_DB");
    private static final String USER = dotenv.get("POSTGRES_USER");
    private static final String PASS = dotenv.get("POSTGRES_PASSWORD");
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        port(8080);

        get("/bevande", (req, res) -> {
            res.type("application/json");
            Map<String, Double> bevande = new HashMap<>();
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT nome, prezzo FROM bevande")) {

                while (rs.next()) {
                    bevande.put(rs.getString("nome"), rs.getDouble("prezzo"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new Gson().toJson(bevande);
        });

        get("/cialde", (req, res) -> {
            res.type("application/json");
            Map<String, Integer> cialdeDisponibili = new HashMap<>();
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT tipo, quantita FROM cialde")) {

                while (rs.next()) {
                    cialdeDisponibili.put(rs.getString("tipo"), rs.getInt("quantita"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new Gson().toJson(cialdeDisponibili);
        });

        post("/ricarica", (req, res) -> {
            String json = req.body();
            Map<String, Integer> ricarica = gson.fromJson(json, Map.class);
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("UPDATE cialde SET quantita = ? WHERE tipo = ?")) {

                for (Map.Entry<String, Integer> entry : ricarica.entrySet()) {
                    pstmt.setInt(1, entry.getValue());
                    pstmt.setString(2, entry.getKey());
                    pstmt.executeUpdate();
                }
                return gson.toJson("Cialde ricaricate con successo");
            } catch (SQLException e) {
                e.printStackTrace();
                return gson.toJson("Errore durante la ricarica delle cialde");
            }
        });

        post("/utilizzo", (req, res) -> {
            String json = req.body();
            Map<String, Integer> utilizzo = gson.fromJson(json, Map.class);
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("UPDATE cialde SET quantita = quantita - ? WHERE tipo = ?")) {

                for (Map.Entry<String, Integer> entry : utilizzo.entrySet()) {
                    pstmt.setInt(1, entry.getValue());
                    pstmt.setString(2, entry.getKey());
                    pstmt.executeUpdate();
                }
                return gson.toJson("Cialde utilizzate con successo");
            } catch (SQLException e) {
                e.printStackTrace();
                return gson.toJson("Errore durante l'utilizzo delle cialde");
            }
        });
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }
}