param(
    [string]$p
)

# Generate a random password if none is provided
if (-not $p) {
    $p = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_})
}

# Get all available IPv4 addresses
$ipAddresses = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.InterfaceAlias -notlike "Loopback*" -and $_.InterfaceAlias -notlike "Virtual*"
} | Select-Object -ExpandProperty IPAddress

# Ask user to select an IP address
if ($ipAddresses.Count -gt 1) {
    Write-Host "Available IP addresses:"
    for ($i = 0; $i -lt $ipAddresses.Count; $i++) {
        Write-Host "[$i] $($ipAddresses[$i])"
    }

    do {
        $selection = Read-Host "Select the index of the IP address to use"
    } while (-not ($selection -match "^\d+$") -or [int]$selection -ge $ipAddresses.Count)

    $localIP = $ipAddresses[[int]$selection]
} else {
    $localIP = $ipAddresses[0]
}

# Ensure the MQTT_URL_REMOTE is formatted correctly
$MQTT_URL_REMOTE = "ssl://" + $localIP + ":8883"
$KEYCLOAK_ISSUER = "https://" + $localIP + ":8080/realms/School-D.Spencer"
$API_URL = "https://" + $localIP + ":8443"

# Define the .env file content
$envContent = @"
POSTGRES_DB_LOCAL=local
POSTGRES_USER_LOCAL=local
POSTGRES_PASSWORD_LOCAL=$p
POSTGRES_URL_LOCAL=postgres-local:5432
MQTT_URL_LOCAL=ssl://mosquitto-local:8883
MQTT_URL_REMOTE=$MQTT_URL_REMOTE
"@

# Write to .env file
$envContent | Set-Content -Path BiBoBip/.env

$envContent = @"
# Database settings
POSTGRES_URL_API=postgres-api:5432
POSTGRES_DB_API=api
POSTGRES_USER_API=apiSpark
POSTGRES_PASSWORD_API=$p
MQTT_URL=$MQTT_URL_REMOTE

# Keycloak settings
KC_DB=postgres
KC_DB_URL_HOST=postgres-keycloak
KC_DB_URL_DATABASE=keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=$p
KC_ADMIN=admin
KC_ADMIN_PASSWORD=$p
KC_HOSTNAME=$localIP

# api keycloak
OIDC_ISSUER=$KEYCLOAK_ISSUER
OIDC_CLIENT_ID=api-service
OIDC_REDIRECT_URI=https://localhost
OIDC_CLIENT_SECRET=
API_URL=$API_URL

"@

# Write to .env file
$envContent | Set-Content -Path Backend/.env

Write-Host ".env file has been created successfully." -ForegroundColor Green

# Generate certificate
Write-Host "Generating certificates..."

# Set certificate parameters
$CERT_DIR = ".\certs"
$CA_KEY = "$CERT_DIR\ca.key"
$CA_CRT = "$CERT_DIR\ca.crt"
$CA_P12 = "$CERT_DIR\ca.p12"

# Create directory if not exists
if (!(Test-Path -Path $CERT_DIR)) {
    New-Item -ItemType Directory -Path $CERT_DIR | Out-Null
}

# Generate Certificate Authority (CA)
Write-Host "Generating CA Certificate..."
openssl genrsa -out $CA_KEY 2048
openssl req -x509 -new -nodes -key $CA_KEY -sha256 -days 365 -out $CA_CRT -subj "/C=US/ST=State/L=City/O=School-D.-Spencer/CN=MyCA"
openssl pkcs12 -export -in $CA_CRT -out $CA_P12 -name "MyCA" -nokeys -passout pass:

# Set the certificate directories
$BIBOBIP = ".\BiBoBip"
$BACKEND = ".\Backend"
$BIBOBIP_CERT_DIR = "$BIBOBIP\certs"
$BACKEND_CERT_DIR = "$BACKEND\certs"

# Copy the entire certs directory to the required directories
Copy-Item -Path $CERT_DIR -Destination $BIBOBIP -Recurse -Force
Copy-Item -Path $CERT_DIR -Destination $BACKEND -Recurse -Force

Write-Host "CA Certificates have been generated successfully." -ForegroundColor Green

# Generate server certificates for api-service and web-service

Write-Host "Generating Backend Certificates..."

$CERTS = $(
	@{ name = "broker-mqtt"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=MQTT/CN=$localIP"; properName = "IP:$localIP"; san = "IP:$localIP"},
	@{ name = "api-mqtt"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=MQTT/CN=api-service"; properName = "api-service"},
	@{ name = "api-https"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=HTTPS/CN=localhost"; properName = "localhost"; san = "IP:$localIP"},
	@{ name = "web-https"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=HTTPS/CN=localhost"; properName = "localhost"; san = "IP:$localIP"},
	@{ name = "keycloak-https"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=HTTPS/CN=$localIP"; properName = "IP:$localIP"; san = "IP:$localIP"}
)

$SAN_TEMPLATE = @"
[ v3_req ]
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

"@

# Generate Service Certificate
foreach ($CERT in $CERTS) {
	$SERVICE_DIR = "$BACKEND_CERT_DIR\$($CERT.name)"
	$SERVICE_KEY = "$SERVICE_DIR\$($CERT.name).key"
	$SERVICE_CSR = "$SERVICE_DIR\$($CERT.name).csr"
	$SERVICE_CRT = "$SERVICE_DIR\$($CERT.name).crt"
	$SERVICE_P12 = "$SERVICE_DIR\$($CERT.name).p12"
	$SERVICE_SAN = "$SERVICE_DIR\$($CERT.name).cnf"

	# Create directory if not exists
	if (!(Test-Path -Path $SERVICE_DIR)) {
		New-Item -ItemType Directory -Path $SERVICE_DIR | Out-Null
	}

	# Create a san file
	if ($($CERT.san)) {
		($SAN_TEMPLATE + "subjectAltName=$($CERT.san)") | Out-File -Encoding ascii $SERVICE_SAN

		Write-Host "Generating Certificate for $($CERT.name)..."
		openssl genrsa -out $SERVICE_KEY 2048
		openssl req -new -key $SERVICE_KEY -out $SERVICE_CSR -subj "$($CERT.subject)" -addext "subjectAltName=$($CERT.san)"
		openssl x509 -req -in $SERVICE_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVICE_CRT -days 365 -sha256 -extfile $SERVICE_SAN -extensions v3_req
	} else {
		Write-Host "Generating Certificate for $($CERT.name)..."

		openssl genrsa -out $SERVICE_KEY 2048
		openssl req -new -key $SERVICE_KEY -out $SERVICE_CSR -subj "$($CERT.subject)"
		openssl x509 -req -in $SERVICE_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVICE_CRT -days 365 -sha256
	}

	# Convert Client Certificate to Passwordless PKCS#12 (.p12)
	Write-Host "Converting $($CERT.name) Certificate to Passwordless .p12..."
	openssl pkcs12 -export -in $SERVICE_CRT -inkey $SERVICE_KEY -certfile $CA_CRT -out $SERVICE_P12 -name "$($CERT.properName)" -passout pass:
}

Write-Host "All Backend Certificates have been generated successfully." -ForegroundColor Green
