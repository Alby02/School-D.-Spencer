#include "WiFiManager.h"

WiFiManager8266::WiFiManager8266(ESP8266WebServer& server, const char* apSSID, const char* apPassword, const char* adminUsername, const char* adminPassword, int eepromSize, int ssidAddr, int passAddr, int flagAddr)
    : server(server), apSSID(apSSID), apPassword(apPassword), adminUsername(adminUsername), adminPassword(adminPassword), eepromSize(eepromSize), ssidAddr(ssidAddr), passAddr(passAddr), flagAddr(flagAddr)
{
    EEPROM.begin(eepromSize);
}

void WiFiManager8266::begin()
{
    WiFi.mode(WIFI_AP_STA); // Set both AP and STA modes

    if (checkStoredCredentials())
    {
        Serial.println("Stored credentials found.");
        connectWithStoredCredentials();
    }
    else
    {
        Serial.println("No stored credentials found.");
        startAccessPoint();
    }

    // Set up server routes
    server.on("/wifi", std::bind(&WiFiManager8266::handleRoot, this));
    server.on("/wifi/submit", std::bind(&WiFiManager8266::handleCredentials, this));
    
}

bool WiFiManager8266::checkStoredCredentials()
{
    return EEPROM.read(flagAddr) == 1;
}

void WiFiManager8266::connectWithStoredCredentials()
{
    String ssid = readStringFromEEPROM(ssidAddr);
    String password = readStringFromEEPROM(passAddr);

    WiFi.begin(ssid.c_str(), password.c_str());
    Serial.print("Connecting to stored SSID: " + ssid);

    int count = 0;
    while (WiFi.status() != WL_CONNECTED && count < 20)
    {
        delay(500);
        Serial.print(".");
        count++;
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.println("\nConnected!");
        Serial.print("STA IP address: ");
        Serial.println(WiFi.localIP());
    }
    else
    {
        Serial.println("\nFailed to connect.");
        Serial.println("starting Access Point...");
        startAccessPoint();
    }
}

void WiFiManager8266::startAccessPoint()
{
    WiFi.softAP(apSSID, apPassword);
    IPAddress IP = WiFi.softAPIP();
    Serial.print("Access Point started. Connect to: ");
    Serial.print(apSSID);
    Serial.print(" at IP: ");
    Serial.println(IP);
}

String WiFiManager8266::scanNetworks()
{
    int n = WiFi.scanNetworks();
    String networkList = "";
    for (int i = 0; i < n; i++)
    {
        String option = "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + "</option>";
        Serial.println("Found:" + option);
        networkList += option;
    }

    return networkList;
}

void WiFiManager8266::handleRoot()
{
    Serial.println("Root URL called.");

    if (!server.authenticate(adminUsername, adminPassword))
    {
        return server.requestAuthentication();
    }

    String networkList = scanNetworks();

    String htmlPage = "<html><body>"
                      "<h1>Select Wi-Fi Network</h1>"
                      "<form action='/wifi/submit' method='POST'>"
                      "Network: <select name='ssid'>" +
                      networkList +
                      "</select><br>"
                      "Password: <input type='password' name='password'><br>"
                      "<input type='submit' value='Connect'>"
                      "</form></body></html>";

    server.send(200, "text/html", htmlPage);

    Serial.println("Sent HTML page.");
}

void WiFiManager8266::handleCredentials()
{
    String ssid = server.arg("ssid");
    String password = server.arg("password");

    server.send(200, "text/html", "<h1>Connecting to " + ssid + "...</h1>");

    writeStringToEEPROM(ssidAddr, ssid);
    writeStringToEEPROM(passAddr, password);
    EEPROM.write(flagAddr, 1); // Mark that credentials are stored
    EEPROM.commit();

    WiFi.begin(ssid.c_str(), password.c_str());

    int count = 0;
    while (WiFi.status() != WL_CONNECTED && count < 10)
    {
        delay(500);
        Serial.print(".");
        count++;
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.println("Connected!");
        server.send(200, "text/html", "<h1>Successfully Connected to " + ssid + "!</h1>");
    }
    else
    {
        Serial.println("Failed to connect.");
        server.send(200, "text/html", "<h1>Failed to Connect to " + ssid + ".</h1>");
    }
}

void WiFiManager8266::writeStringToEEPROM(int addr, String data)
{
    int len = data.length();
    for (int i = 0; i < len; i++)
    {
        EEPROM.write(addr + i, data[i]);
    }
    EEPROM.write(addr + len, '\0'); // Null-terminate the string
    EEPROM.commit();
}

String WiFiManager8266::readStringFromEEPROM(int addr)
{
    char data[32];
    int len = 0;
    char ch = EEPROM.read(addr);
    while (ch != '\0' && len < 31)
    {
        data[len++] = ch;
        ch = EEPROM.read(addr + len);
    }
    data[len] = '\0';
    return String(data);
}