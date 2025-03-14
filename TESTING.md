# Testing Instructions

Follow these steps to set up and test the Coffee Machine Management Software.

## 1. Initial Setup
1. Open a terminal and navigate to the repository's root directory (`./`).
2. Execute the following command to generate necessary configuration files:
   ```sh
   .\gen-files-ps1
   ```
3. The script will prompt you to select the correct IP address that provides internet access to your computer.
    - Identify the correct IP from the list.
    - Enter the corresponding index and press **Enter**.

## 2. Start Backend Services
1. Navigate to the Backend folder:
   ```sh
   cd .\Backend
   ```
2. Build the backend service images:
   ```sh
   .\build-images.ps1
   ```
3. Start the backend services:
   ```sh
   .\start.ps1
   ```
4. Some services (*node-website, api-service*) may not start — this is expected for now.

## 3. Keycloak Configuration
1. Open a web browser and go to the Keycloak URL.
2. Log in using the credentials specified in the `.env` file.
3. Create a new **realm** using the `realm-export.json` file.
4. Navigate to **Clients** > **api-service** > **Credentials**.
5. Generate a new **secret** and paste it into the `.env` file.
6. Create a new **user** in Keycloak and assign them to a group (**Administrator** or **Manager**).
7. Set a password for the newly created user.

## 4. Restart Backend Services
1. Run the following command again:
   ```sh
   .\start.ps1
   ```
2. This time, all services should start successfully.

## 5. Set Up and Run a Coffee Machine
1. Move back one directory:
   ```sh
   cd ..
   ```
2. Enter the `BiBoBip` directory:
   ```sh
   cd .\BiBoBip
   ```
3. Build the BiBoBip images:
   ```sh
   .\build-images.ps1
   ```
4. Generate a new coffee machine instance:
   ```sh
   .\gen-BiBoBip.ps1
   ```
5. Navigate to the newly created machine's folder:
   ```sh
   cd .\machines\<id-machine>
   ```
6. Start the machine:
   ```sh
   .\start.ps1
   ```

## 6. Register the Machine
1. Open the web application.
2. Register the newly created machine under a university.
3. The system should now recognize and manage the machine properly.

Your system is now set up and ready for testing!

