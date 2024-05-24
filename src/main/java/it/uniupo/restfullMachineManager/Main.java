package it.uniupo.restfullMachineManager;

import static spark.Spark.get;
import static spark.Spark.port;

public class Main {
    public static void main(String[] args) {

        port(8090);
        get("/hello", (req, res) -> "Hello World");


    }
}