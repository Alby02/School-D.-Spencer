# Coffee Machine Management Software

## Overview

This project was developed as part of the **PISSIR** course (*Progettazione e Implementazione di Sistemi Software in Reti*). The objective is to implement a management software system for coffee machines across different universities.

The system includes backend services, a frontend interface, and integration with Keycloak for authentication and authorization.

## Features

- User authentication and role-based access control via Keycloak.
- Management of coffee machines at various universities.
- Interaction between backend services and university-registered machines.
- Deployment and testing scripts for easy setup.

## Project Structure

```
./               # Top-level repository folder
│-- Backend/     # Backend services and scripts
│   ├── .env     # Environment configuration file for Backend
│-- BiBoBip/     # Coffee machine generation and management
│   ├── .env     # Environment configuration file for BiBoBip
│   ├── machines/ # Instances of generated coffee machines (created after running gen-BiBoBip.ps1)
│-- scripts/     # Deployment and setup scripts
│-- realm-export.json # Keycloak realm configuration
```

## Getting Started

To set up and test the project, follow the instructions in the [TESTING.md](TESTING.md) file.

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.

