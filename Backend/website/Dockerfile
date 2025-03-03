FROM node:20.18.3-bookworm

WORKDIR /app

# Copy only express wrapper and build files to leverage Docker caching
COPY package.json package.json

# Download express dependencies (caching layer)
RUN npm install

# Copy the source code into the container
COPY src src

# Variebile ambiente port
ENV PORT=80

# Expose the application port
EXPOSE 80

# Run the application
CMD ["node", "./src/server.js"]