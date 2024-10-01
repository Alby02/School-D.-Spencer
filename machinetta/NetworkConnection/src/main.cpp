#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <ArduinoMqttClient.h>


void setup() {
    // put your setup code here, to run once:
    WiFi.begin("FASTWEB-Pkp4N7", "8qFReWCkwG");

    Serial.begin(115200);
    Serial.print("Connecting to WiFi..");

    while(WiFi.isConnected() == false)
    {
        Serial.print(".");
        delay(200);
    }

    Serial.println("\nConnected to the WiFi network");
    

    pinMode(D0, OUTPUT);

}

void loop() {
    // put your main code here, to run repeatedly:
    digitalWrite(D0, HIGH);
    delay(1000);
    digitalWrite(D0, LOW);
    delay(1000);

}
