package it.uniupo.restfullMachineManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configs {

    private final String postgresUrl;
    private final String postgresPort;
    private final String postgresUser;
    private final String postgresPassword;
    private final String postgresDatabase;

    Configs(String configFilePath) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(configFilePath)) {
            properties.load(fis);
            postgresUrl = properties.getProperty("postgresUrl");
            postgresPort = properties.getProperty("postgresPort");
            postgresUser = properties.getProperty("postgresUser");
            postgresPassword = properties.getProperty("postgresPassword");
            postgresDatabase = properties.getProperty("postgresDatabase");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getPostgresUrl() {
        return postgresUrl;
    }

    public String getPostgresPort() {
        return postgresPort;
    }

    public String getPostgresUser() {
        return postgresUser;
    }

    public String getPostgresPassword() {
        return postgresPassword;
    }

    public String getPostgresDatabase() {
        return postgresDatabase;
    }
}
