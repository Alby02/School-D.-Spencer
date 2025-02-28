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

# Run the application
CMD ["build/install/Bank/bin/Bank"]