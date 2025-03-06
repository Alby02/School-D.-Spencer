# Define the folders to build
$folders = @("api-service", "website")

foreach ($folder in $folders) {
	Write-Host "Building Docker image in folder: $folder"
	# Run docker build directly with the folder name
	docker build -t "$folder" .\$folder
}