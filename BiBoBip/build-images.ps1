# Ask the user if they want to compile everything or a specific Docker image
$compileChoice = Read-Host "Do you want to compile all Docker images or just a specific one? (all/specific default: specific)"

if ($compileChoice -eq "all") {
	# Define the folders to build
	$folders = @("assistance", "bank", "issuer", "keypad", "manager")

	foreach ($folder in $folders) {
		Write-Host "Building Docker image in folder: $folder"
		# Run docker build directly with the folder name
		docker build -t "$folder" .\$folder
	}
} else {
	# Ask the user which specific folder to compile
	$specificFolder = Read-Host "Enter the name of the folder to compile (assistance, bank, issuer, keypad, manager)"

	# Check if the folder is valid
	if ($specificFolder -in @("assistance", "bank", "issuer", "keypad", "manager")) {
		Write-Host "Building Docker image in folder: $specificFolder"
		docker build -t "$specificFolder" .\$specificFolder
	} else {
		Write-Host "Invalid folder name. Skipping the build."
	}
}