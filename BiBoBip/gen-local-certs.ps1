# PowerShell script to generate MQTT TLS certificates for multiple services

# Set certificate parameters
$CERT_DIR = ".\MQTT-Certs"
$CA_KEY = "$CERT_DIR\ca.key"
$CA_CRT = "$CERT_DIR\ca.crt"
$SERVER_KEY = "$CERT_DIR\server.key"
$SERVER_CSR = "$CERT_DIR\server.csr"
$SERVER_CRT = "$CERT_DIR\server.crt"

# List of client services
$SERVICES = @("manager", "assistance", "keypad", "bank", "issuer")

# Create directory if not exists
if (!(Test-Path -Path $CERT_DIR)) {
    New-Item -ItemType Directory -Path $CERT_DIR | Out-Null
}

# Generate Certificate Authority (CA)
Write-Host "Generating CA Certificate..."
openssl genrsa -out $CA_KEY 2048
openssl req -x509 -new -nodes -key $CA_KEY -sha256 -days 365 -out $CA_CRT -subj "/C=US/ST=State/L=City/O=MyMQTT/CN=MyCA"

# Generate Server Certificate
Write-Host "Generating Server Certificate..."
openssl genrsa -out $SERVER_KEY 2048
openssl req -new -key $SERVER_KEY -out $SERVER_CSR -subj "/C=US/ST=State/L=City/O=MyMQTT/CN=mosquitto-local"
openssl x509 -req -in $SERVER_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVER_CRT -days 365 -sha256

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
    $CLIENT_PEM = "$SERVICE_DIR\client.pem"
    $CLIENT_P12 = "$SERVICE_DIR\client.p12"

    Write-Host "Generating Certificate for $SERVICE..."
    openssl genrsa -out $CLIENT_KEY 2048
    openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR -subj "/C=US/ST=State/L=City/O=MyMQTT/CN=$SERVICE"
    openssl x509 -req -in $CLIENT_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $CLIENT_CRT -days 365 -sha256

    # Convert Client Certificate to Passwordless PKCS#12 (.p12)
    Write-Host "Converting $SERVICE Certificate to Passwordless .p12..."
    openssl pkcs12 -export -in $CLIENT_CRT -inkey $CLIENT_KEY -certfile $CA_CRT -out $CLIENT_P12 -name "$SERVICE" -passout pass:
}

Write-Host "✅ All certificates generated successfully in $CERT_DIR!"

# copy the generated certificates to the required directories

# Copy CA certificate to the server

Copy-Item -Path $CA_CRT -Destination "./mosquitto/conf/" -Force
Copy-Item -Path $SERVER_KEY -Destination "./mosquitto/conf/" -Force
Copy-Item -Path $SERVER_CRT -Destination "./mosquitto/conf/" -Force
