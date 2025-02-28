# Ask the user whether to compile or not
$compileChoice = Read-Host "Do you want to compile all Docker images before running? (yes/no default: no)"

# If compile is requested, build all images
if ($compileChoice -eq "yes") {
    # Ask the user if they want to compile everything or a specific Docker image
    $compileChoice = Read-Host "Do you want to compile all Docker images or just a specific one? (all/specific default: specific)"

    if ($compileChoice -eq "all") {
        # Define the folders to build
        $folders = @("assistance", "bank", "issuer", "keypad", "manager")

        foreach ($folder in $folders) {
            Write-Host "Building Docker image in folder: $folder"
            # Run docker build directly with the folder name
            docker build -t "$folder-local" .\$folder
        }
    } else {
        # Ask the user which specific folder to compile
        $specificFolder = Read-Host "Enter the name of the folder to compile (assistance, bank, issuer, keypad, manager)"

        # Check if the folder is valid
        if ($specificFolder -in @("assistance", "bank", "issuer", "keypad", "manager")) {
            Write-Host "Building Docker image in folder: $specificFolder"
            docker build -t "$specificFolder-local" .\$specificFolder
        } else {
            Write-Host "Invalid folder name. Skipping the build."
        }
    }
} else {
    Write-Host "Skipping Docker image compilation."
}

# Run docker-compose up
Write-Host "Starting Docker Compose..."
docker-compose up -d

# Open a new terminal and attach to the `keypad` container
Write-Host "Opening terminal for 'keypad' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach keypad-local"

# Open another new terminal and attach to the `bank` container
Write-Host "Opening terminal for 'bank' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach bank-local"

# Open another new terminal and attach to the `issuer` container
Write-Host "Opening terminal for 'issuer' container..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "docker attach issuer-local"

Write-Host "All done! Docker Compose is running, and terminals are attached."
