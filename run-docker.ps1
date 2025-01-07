# Ask the user whether to compile or not
$compileChoice = Read-Host "Do you want to compile all Docker images before running? (yes/no default: no)"

# If compile is requested, build all images
if ($compileChoice -eq "yes") {

	# Define the folders to build
	$folders = @("api-service")

	foreach ($folder in $folders) {
		Write-Host "Building Docker image in folder: $folder"
		# Run docker build directly with the folder name
		docker build -t "$folder" .\$folder
	}

} else {
    Write-Host "Skipping Docker image compilation."
}

# Run docker-compose up
Write-Host "Starting Docker Compose..."
docker-compose up -d

Write-Host "All done! Docker Compose is running."
