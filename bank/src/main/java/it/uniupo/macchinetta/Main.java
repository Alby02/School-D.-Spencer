package it.uniupo.macchinetta;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import java.sql.*;
import java.util.Scanner;

public class Main {

    public static class Credito {
        private static int credito = 0;

        public static synchronized int getCredito() {
            return credito;
        }

        public static synchronized void setCredito(int credito) {
            Credito.credito = credito;
        }
        //add synchronized
        public static synchronized void incrementaCredito(int credito) {
            Credito.credito += credito;
        }
    }
    public static void main(String[] args) throws SQLException {
        System.out.println("Hello, World!");

        String postgresDB = System.getenv("POSTGRES_DB");
        String postgresUser = System.getenv("POSTGRES_USER");
        String postgresPassword = System.getenv("POSTGRES_PASSWORD");
        String postgresUrl = System.getenv("POSTGRES_URL");
        String mqttUrl = System.getenv("MQTT_URL");

        Connection databaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://" + postgresUrl + "/" + postgresDB, postgresUser, postgresPassword
        );

        //thread per la gestione delle richieste mqtt sull'oggetto sincrono Credito
        Thread mqttThread = new Thread(() -> {
            try (MqttClient mqttClient = new MqttClient(mqttUrl, MqttClient.generateClientId())) {
                mqttClient.connect();
                mqttClient.subscribe("credito/richiesta", (topic, message) -> {
                    //quando richiesta è vero invia il credito attuale dall'oggetto sincrono Credito
                    String richiesta = new String(message.getPayload());
                    if (richiesta.equals("vero")) {
                        try {
                            mqttClient.publish("credito/risposta", ("" + Credito.getCredito()).getBytes(), 0, false);
                        } catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    //verifica se la richiesta è un numero e sottrai quel numero al credito per poi stampare il valore a video e settare il credito a 0
                    else {
                        try {
                            int credito = Integer.parseInt(richiesta);
                            Credito.incrementaCredito(-credito);
                            System.out.println("Resto: " + Credito.getCredito() + " centesimi");
                            //Calcolo delle monete necessarie per il resto(le monete disponibili sono salvate nel database nella tabella resto)
                            Statement statement = databaseConnection.createStatement();
                            ResultSet resultSet = statement.executeQuery("SELECT valore, quantita FROM resto ORDER BY valore DESC");
                            while (resultSet.next()) {
                                int valore = resultSet.getInt("valore");
                                int quantita = resultSet.getInt("quantita");
                                int resto = Credito.getCredito() / valore;
                                if (resto > 0) {
                                    int monete = Math.min(resto, quantita);
                                    Credito.incrementaCredito(-monete * valore);
                                    statement.executeUpdate("UPDATE resto SET quantita = quantita - " + monete + " WHERE valore = " + valore);
                                    System.out.println("Monete da " + valore + " centesimi: " + monete);
                                }
                            }
                            System.out.println("Credito attuale: " + Credito.getCredito() + " centesimi");
                            System.out.println("Inserisci moneta: 5 cent (1), 10 cent (2), 20 cent (3), 50 cent (4), 1 euro (5), 2 euro (6)");
                            System.out.println("Premi 0 per restituire la moneta inserita");

                        } catch (NumberFormatException e) {
                            System.out.println("Richiesta non valida");
                        }
                    }
                });
            } catch (MqttPersistenceException e) {
                throw new RuntimeException(e);
            } catch (MqttSecurityException e) {
                throw new RuntimeException(e);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        });
        mqttThread.start();
        Scanner scanner = new Scanner(System.in);
        while (true) {

            //stampa credito attuale, elenco opzioni monete inserite (5 cent, 10 cent, 20 cent, ecc...) e opzione tasto rosso per restituire la moneta inserita
            System.out.println("Credito attuale: " + Credito.getCredito() + " centesimi");
            System.out.println("Inserisci moneta: 5 cent (1), 10 cent (2), 20 cent (3), 50 cent (4), 1 euro (5), 2 euro (6)");
            System.out.println("Premi 0 per restituire la moneta inserita");

            switch (scanner.nextInt()) {
                case 1:
                    Credito.incrementaCredito(5);
                    break;
                case 2:
                    Credito.incrementaCredito(10);
                    break;
                case 3:
                    Credito.incrementaCredito(20);
                    break;
                case 4:
                    Credito.incrementaCredito(50);
                    break;
                case 5:
                    Credito.incrementaCredito(100);
                    break;
                case 6:
                    Credito.incrementaCredito(200);
                    break;
                case 0:
                    Credito.setCredito(0);
                    break;
                default:
                    System.out.println("Inserimento non valido");
            }
        }
    }
}
