package it.uniupo.restfullMachineManager;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import static spark.Spark.*;

public class Main {

    private static final Map<String, Double> beveragePrices = new HashMap<>();
    private static final Map<String, Integer> cialdeDisponibili = new HashMap<>();

    public static void main(String[] args) {
        port(8080);
        initBeverageData();

        get("/bevande", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(beveragePrices);
        });

        get("/cialde", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(cialdeDisponibili);
        });

        post("/cialde/update", (req, res) -> {
            String beverage = req.queryParams("beverage");
            int quantity = Integer.parseInt(req.queryParams("quantity"));

            if (cialdeDisponibili.containsKey(beverage)) {
                cialdeDisponibili.put(beverage, cialdeDisponibili.get(beverage) + quantity);
                return "Quantità di cialde aggiornata per " + beverage;
            } else {
                res.status(404);
                return "Bevanda non trovata";
            }
        });

        post("/eroga", (req, res) -> {
            String beverage = req.queryParams("beverage");

            if (cialdeDisponibili.containsKey(beverage)) {
                int available = cialdeDisponibili.get(beverage);
                if (available > 0) {
                    cialdeDisponibili.put(beverage, available - 1);
                    return "Bevanda " + beverage + " erogata! Quantità di cialde rimanenti: " + (available - 1);
                } else {
                    res.status(400);
                    return "Cialde non disponibili per " + beverage;
                }
            } else {
                res.status(404);
                return "Bevanda non trovata";
            }
        });

    }

    private static void initBeverageData() {
        // Bevande e prezzi
        beveragePrices.put("Caffè", 1.0);
        beveragePrices.put("Cappuccino", 1.5);
        beveragePrices.put("Tè", 0.8);

        // Disponibilità iniziale di cialde
        cialdeDisponibili.put("Caffè", 10);
        cialdeDisponibili.put("Cappuccino", 5);
        cialdeDisponibili.put("Tè", 7);
    }
}