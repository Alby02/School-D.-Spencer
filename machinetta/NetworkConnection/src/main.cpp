#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <EEPROM.h>

#define EEPROM_SIZE 96
#define SSID_START_ADDR 0
#define PASS_START_ADDR 32
#define CREDENTIAL_FLAG_ADDR 95

ESP8266WebServer server(80);

// Default login credentials for securing the web page
const char *adminUsername = "admin";
const char *adminPassword = "12345";

String networkList;


void scanNetworks();
bool checkStoredCredentials();
void connectWithStoredCredentials();
String readStringFromEEPROM(int addr);
void startAccessPoint();
void handleRoot();
void handleCredentials();
void writeStringToEEPROM(int addr, String data);

void setup()
{
    Serial.begin(115200);
    EEPROM.begin(EEPROM_SIZE);

    pinMode(D0, OUTPUT);

    WiFi.mode(WIFI_AP_STA);
    scanNetworks();

    if (checkStoredCredentials())
    {
        Serial.println("Stored credentials found.");
        connectWithStoredCredentials();
    }
    else
    {
        Serial.println("No stored credentials found.");
        startAccessPoint(); // Start access point to configure Wi-Fi
    }

    server.begin();

    server.on("/", handleRoot);
    server.on("/submit", handleCredentials); // Handle form submission

    Serial.println("HTTP server started");
}

void loop()
{
    server.handleClient();
    // put your main code here, to run repeatedly:
    digitalWrite(D0, HIGH);
    delay(1000);
    digitalWrite(D0, LOW);
    delay(1000);
}

// Scan for Wi-Fi networks
void scanNetworks()
{
    Serial.println("Scanning for Wi-Fi networks...");
    int n = WiFi.scanNetworks();
    Serial.println("Scan complete.");
    networkList = "";
    for (int i = 0; i < n; i++)
    {
        String option = "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + "</option>";
        Serial.println("Found:" + option);
        networkList += option;
    }
}

bool checkStoredCredentials()
{
    return EEPROM.read(CREDENTIAL_FLAG_ADDR) == 1;
}

void connectWithStoredCredentials()
{
    String ssid = readStringFromEEPROM(SSID_START_ADDR);
    String password = readStringFromEEPROM(PASS_START_ADDR);

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
    }
    else
    {
        Serial.println("\nFailed to connect, starting Access Point...");
        startAccessPoint();
    }
}

String readStringFromEEPROM(int addr)
{
    char data[32];
    int len = 0;
    char ch = EEPROM.read(addr);
    while (ch != '\0' && len < 31)
    {
        data[len++] = ch;
        ch = EEPROM.read(addr + len);
    }
    data[len] = '\0'; // Null terminator
    return String(data);
}

void startAccessPoint()
{
    WiFi.softAP("ConfigESP8266", "password123"); // Starting Access Point
    Serial.println("Access Point started. Connect to 'ConfigESP8266'");
    Serial.print("IP Address: ");
    Serial.println(WiFi.softAPIP());

}

// Handle the root URL "/"
void handleRoot()
{   
    Serial.println("Root URL called.");

    if (!server.authenticate(adminUsername, adminPassword))
    {
        return server.requestAuthentication();
    }

    String htmlPage = "<html><body>";
    htmlPage += "<h1>Select Wi-Fi Network</h1>";
    htmlPage += "<form action='/submit' method='POST'>";
    htmlPage += "Network: <select name='ssid'>" + networkList + "</select><br>";
    htmlPage += "Password: <input type='password' name='password'><br>";
    htmlPage += "<input type='submit' value='Connect'>";
    htmlPage += "</form></body></html>";

    server.send(200, "text/html", htmlPage);

    Serial.println("Sent HTML page.");
}


void handleCredentials()
{
    String ssid = server.arg("ssid");
    String password = server.arg("password");

    server.send(200, "text/html", "<h1>Connecting to " + ssid + "...</h1>");

    // Try to connect to the selected network
    WiFi.begin(ssid.c_str(), password.c_str());

    // Wait for connection
    int count = 0;
    while (WiFi.status() != WL_CONNECTED && count < 20)
    {
        delay(500);
        Serial.print(".");
        count++;
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.println("Connected!");
        server.send(200, "text/html", "<h1>Successfully Connected to " + ssid + "!</h1>");
        // Store the credentials in EEPROM
        writeStringToEEPROM(SSID_START_ADDR, ssid);
        writeStringToEEPROM(PASS_START_ADDR, password);
        EEPROM.write(CREDENTIAL_FLAG_ADDR, 1); // Mark that credentials are stored
        EEPROM.commit();
    }
    else
    {
        Serial.println("Failed to Connect.");
        server.send(200, "text/html", "<h1>Failed to Connect to " + ssid + ".</h1>");
    }
}

void writeStringToEEPROM(int addr, String data)
{
    int len = data.length();
    for (int i = 0; i < len; i++)
    {
        EEPROM.write(addr + i, data[i]);
    }
    EEPROM.write(addr + len, '\0'); // Add null terminator
    EEPROM.commit();
}