FROM eclipse-temurin:21
LABEL authors="Alberto"

WORKDIR /app

# Copy only Gradle wrapper and build files to leverage Docker caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

# Download Gradle dependencies (caching layer)
RUN ./gradlew dependencies || true

# Copy the source code into the container
COPY src src

# Build the application
RUN ./gradlew clean build -x test
RUN ./gradlew installDist

# Expose the application port
#EXPOSE 80

# Environment variables
#ENV POSTGRES_URL=postgresql:5432
#ENV POSTGRES_DATABASE=machine_manager
#ENV POSTGRES_USER=postgres
#ENV POSTGRES_PASSWORD=postgres

# Run the application
CMD ["build/install/MachineManagerRestFull/bin/MachineManagerRestFull"]