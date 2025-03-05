# PowerShell script to generate server MQTT TLS certificates, copy the generated certificates to the required directories
# and generate server certificates for api-service and web-service

# Set certificate parameters
$CERT_DIR = ".\certs"
$CA_KEY = "$CERT_DIR\ca.key"
$CA_CRT = "$CERT_DIR\ca.crt"
$CA_P12 = "$CERT_DIR\ca.p12"

$CERTS = $(
	@{ name = "broker-mqtt"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=MQTT/CN=mosquitto-backend"; properName = "mosquitto-backend"},
	@{ name = "api-mqtt"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=MQTT/CN=api-service"; properName = "api-service"},
	@{ name = "api-https"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=HTTPS/CN=api-service"; properName = "api-service"},
	@{ name = "web-https"; subject = "/C=US/ST=State/L=City/O=MyOrg/OU=HTTPS/CN=website"; properName = "website"}
)

# Create directory if not exists
if (!(Test-Path -Path $CERT_DIR)) {
    New-Item -ItemType Directory -Path $CERT_DIR | Out-Null
}

# Generate Certificate Authority (CA)
Write-Host "Generating CA Certificate..."
openssl genrsa -out $CA_KEY 2048
openssl req -x509 -new -nodes -key $CA_KEY -sha256 -days 365 -out $CA_CRT -subj "/C=US/ST=State/L=City/O=MyMQTT/CN=MyCA"
openssl pkcs12 -export -in $CA_CRT -out $CA_P12 -name "MyCA" -nokeys -passout pass:

# Generate Service Certificate
foreach ($CERT in $CERTS) {
	$SERVICE_DIR = "$CERT_DIR\$($CERT.name)"
	$SERVICE_KEY = "$SERVICE_DIR\$($CERT.name).key"
	$SERVICE_CSR = "$SERVICE_DIR\$($CERT.name).csr"
	$SERVICE_CRT = "$SERVICE_DIR\$($CERT.name).crt"
	$SERVICE_P12 = "$SERVICE_DIR\$($CERT.name).p12"

	# Create directory if not exists
	if (!(Test-Path -Path $SERVICE_DIR)) {
		New-Item -ItemType Directory -Path $SERVICE_DIR | Out-Null
	}

	Write-Host "Generating Certificate for $($CERT.name)..."
	openssl genrsa -out $SERVICE_KEY 2048
	openssl req -new -key $SERVICE_KEY -out $SERVICE_CSR -subj $CERT.subject
	openssl x509 -req -in $SERVICE_CSR -CA $CA_CRT -CAkey $CA_KEY -CAcreateserial -out $SERVICE_CRT -days 365 -sha256

	# Convert Client Certificate to Passwordless PKCS#12 (.p12)
	Write-Host "Converting $($CERT.name) Certificate to Passwordless .p12..."
	openssl pkcs12 -export -in $SERVICE_CRT -inkey $SERVICE_KEY -certfile $CA_CRT -out $SERVICE_P12 -name "$($CERT.properName)" -passout pass:
}
