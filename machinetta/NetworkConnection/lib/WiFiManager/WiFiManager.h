#ifndef WiFiManager8266_h
#define WiFiManager8266_h

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>

class WiFiManager8266 {
public:
    // Constructor
    WiFiManager8266(ESP8266WebServer& server, const char* apSSID, const char* apPassword, const char* adminUsername, const char* adminPassword, int eepromSize = 96, int ssidAddr = 0, int passAddr = 32, int flagAddr = 95);
    
    // Start the WiFi Manager (AP and STA modes)
    void begin();
    
private:
    // Variables
    ESP8266WebServer& server;       // Web server instance
    const char* apSSID;             // AP SSID
    const char* apPassword;         // AP password
    const char* adminUsername;      // WIFI Manager admin username
    const char* adminPassword;      // WIFI Manager admin password
    const int eepromSize;           // EEPROM size for credentials
    const int ssidAddr;             // EEPROM address for SSID
    const int passAddr;             // EEPROM address for password
    const int flagAddr;             // EEPROM address for credentials flag

    // Functions
    bool checkStoredCredentials();  // Check if credentials are in EEPROM
    void connectWithStoredCredentials();  // Connect using stored credentials
    void startAccessPoint();  // Start AP mode
    String scanNetworks();  // Scan for available networks
    void handleRoot();  // Handle the root page (serve HTML form)
    void handleCredentials();  // Handle credentials submission
    void writeStringToEEPROM(int addr, String data);  // Write string to EEPROM
    String readStringFromEEPROM(int addr);  // Read string from EEPROM
};

#endif