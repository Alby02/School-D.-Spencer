if (!(Test-Path -Path "./postgres")) {
	New-Item -ItemType Directory -Path "./postgres" | Out-Null
}
if (!(Test-Path -Path "./postgres/local")) {
	New-Item -ItemType Directory -Path "./postgres/api" | Out-Null
}

# Get the current directory name
$dir = (Get-Item -Path ".\").Name

# Run docker-compose up
Write-Host "Starting Docker Compose..."
docker-compose up -d

# Open a new terminal and attach to the `keypad` container
Write-Host "Opening terminal for 'keypad' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach keypad-local-$dir"

# Open another new terminal and attach to the `bank` container
Write-Host "Opening terminal for 'bank' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach bank-local-$dir"

# Open another new terminal and attach to the `issuer` container
Write-Host "Opening terminal for 'issuer' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach issuer-local-$dir"

Write-Host "All done! Docker Compose is running, and terminals are attached."
