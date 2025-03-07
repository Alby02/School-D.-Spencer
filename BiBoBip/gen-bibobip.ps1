# Define the file that stores used IDs
$usedIdsFile = "used_ids.txt"

# Ensure the file exists
if (!(Test-Path $usedIdsFile)) {
    New-Item -ItemType File -Path $usedIdsFile -Force | Out-Null
}

# Load existing IDs
$usedIds = Get-Content -Path $usedIdsFile -ErrorAction SilentlyContinue

# Function to generate a new unique ID
function Get-UniqueId {
    do {
        $newId = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 10 | ForEach-Object {[char]$_})
    } while ($usedIds -contains $newId)
    return $newId
}

# Generate a new unique ID
$uniqueId = Get-UniqueId

# Save the new ID to the file
$uniqueId | Add-Content -Path $usedIdsFile

# Output the generated ID
Write-Host "Generated Unique ID: $uniqueId" -ForegroundColor Green

if (!(Test-Path -Path ".\machines")) {
	New-Item -ItemType Directory -Path ".\machines" | Out-Null
}

$machineDir = ".\machines\$uniqueId"
New-Item -ItemType Directory -Path $machineDir | Out-Null



# Copy certificate files to the new directory
Copy-Item -Path ".\certs" -Destination "$machineDir\certs" -Recurse -Force

# copy docker-compose.yml, ./mosquitto/conf/mosquitto.conf, start.ps1, .env

Copy-Item -Path ".\docker-compose.yml" -Destination "$machineDir\docker-compose.yml" -Force
New-Item -ItemType Directory -Path "$machineDir\mosquitto\conf" | Out-Null
Copy-Item -Path ".\mosquitto\conf\mosquitto.conf" -Destination "$machineDir\mosquitto\conf"  -Force
Copy-Item -Path ".\start.ps1" -Destination "$machineDir\start.ps1" -Force
Copy-Item -Path ".\.env" -Destination "$machineDir\.env" -Force
Copy-Item -Path ".\init_values" -Destination "$machineDir\init_values" -Recurse -Force

# Append the unique ID to the .env text file
"ID=$uniqueId" | Add-Content -Path "$machineDir\.env"

Write-Host "BiBoBip files copied to $machineDir successfully!" -ForegroundColor Green

Write-Host "Generating BiBoBip Certificates..." -ForegroundColor Green

$CERT_DIR = "$machineDir\certs"
$CA_KEY = "$CERT_DIR\ca.key"
$CA_CRT = "$CERT_DIR\ca.crt"

# Generate MQTT remote certificates
$CLIENT_KEY = "$CERT_DIR\remote.key"
$CLIENT_CSR = "$CERT_DIR\remote.csr"
$CLIENT_CRT = "$CERT_DIR\remote.crt"
$CLIENT_P12 = "$CERT_DIR\remote.p12"

Write-Host "Generating BiBoBip remote certificate" -ForegroundColor Yellow

openssl genrsa -out $CLIENT_KEY 2048
$SUBJECT = "/C=US/ST=State/L=City/O=MyMQTT/CN=BiBoBip-$uniqueId"
openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR -subj $SUBJECT
openssl x509 -req -in $CLIENT_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $CLIENT_CRT -days 365 -sha256

# Convert Client Certificate to Passwordless PKCS#12 (.p12)
Write-Host "Converting BiBoBip-$uniqueId Certificate to Passwordless .p12..."
openssl pkcs12 -export -in $CLIENT_CRT -inkey $CLIENT_KEY -certfile $CA_CRT -out $CLIENT_P12 -name "BiBoBip-$uniqueId" -passout pass:

Write-Host "BiBoBip remote certificate generated successfully!" -ForegroundColor Green

# Set certificate parameters
$SERVER_KEY = "$CERT_DIR\server.key"
$SERVER_CSR = "$CERT_DIR\server.csr"
$SERVER_CRT = "$CERT_DIR\server.crt"

# List of client services
$SERVICES = @("manager", "assistance", "keypad", "bank", "issuer")

# Generate Server Certificate
Write-Host "Generating Local Certificate..." -ForegroundColor Yellow
openssl genrsa -out $SERVER_KEY 2048
openssl req -new -key $SERVER_KEY -out $SERVER_CSR -subj "/C=US/ST=State/L=City/O=MyMQTT/CN=mosquitto-local"
openssl x509 -req -in $SERVER_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVER_CRT -days 365 -sha256

Write-Host "Local Certificate generated successfully!" -ForegroundColor Green

Write-Host "Generating Client Certificates for Each Service..." -ForegroundColor Green

# Generate Client Certificates for Each Service
foreach ($SERVICE in $SERVICES) {

	$SERVICE_DIR = "$CERT_DIR\$SERVICE"

	# Create directory if not exists
    if (!(Test-Path -Path $SERVICE_DIR)) {
        New-Item -ItemType Directory -Path $SERVICE_DIR | Out-Null
    }
    $CLIENT_KEY = "$SERVICE_DIR\client.key"
    $CLIENT_CSR = "$SERVICE_DIR\client.csr"
    $CLIENT_CRT = "$SERVICE_DIR\client.crt"
    $CLIENT_P12 = "$SERVICE_DIR\client.p12"

    Write-Host "Generating Certificate for $SERVICE..." -ForegroundColor Yellow
    openssl genrsa -out $CLIENT_KEY 2048
    $SUBJECT = "/C=US/ST=State/L=City/O=MyMQTT/CN=" + $SERVICE
    openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR -subj $SUBJECT
    openssl x509 -req -in $CLIENT_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $CLIENT_CRT -days 365 -sha256

    # Convert Client Certificate to Passwordless PKCS#12 (.p12)
    Write-Host "Converting $SERVICE Certificate to Passwordless .p12..."
    openssl pkcs12 -export -in $CLIENT_CRT -inkey $CLIENT_KEY -certfile $CA_CRT -out $CLIENT_P12 -name "$SERVICE" -passout pass:

    Write-Host "$SERVICE Certificate generated successfully!" -ForegroundColor Green
}

Write-Host "All BiBoBip certificates generated successfully!" -ForegroundColor Green




