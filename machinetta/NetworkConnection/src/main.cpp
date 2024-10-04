#include <Arduino.h>
#include <ESP8266WebServer.h>
#include <WiFiManager.h>

ESP8266WebServer server(80);
WiFiManager8266 wifiManager(server, "ESP8266", "password", "admin", "admin");

unsigned long lastMillis = 0;
int ledState = LOW;

void setup()
{
    Serial.begin(115200);
    pinMode(D0, OUTPUT);

    wifiManager.begin();
    server.begin(); // Start the server
    Serial.println("HTTP server started");
}

void loop()
{
    server.handleClient();

    if (millis() - lastMillis > 1000)
    {
        lastMillis = millis();
        ledState = !ledState;
        digitalWrite(D0, ledState);
    }
}