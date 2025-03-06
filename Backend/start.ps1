# Create folder ./postgres/api and ./postgres/keycloak in folder don't exist

if (!(Test-Path -Path ".\postgres")) {
	New-Item -ItemType Directory -Path ".\postgres" | Out-Null
}
if (!(Test-Path -Path ".\postgres\api")) {
	New-Item -ItemType Directory -Path ".\postgres\api" | Out-Null
}
if (!(Test-Path -Path ".\postgres\keycloak")) {
	New-Item -ItemType Directory -Path ".\postgres\keycloak" | Out-Null
}

# Start the Docker containers
Write-Host "Starting Docker containers..."
docker-compose up -d
