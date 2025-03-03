FROM node:20.18.3-bookworm

WORKDIR /app-server

# Copy only library-info files to leverage Docker caching
COPY package*.json ./

# Download express dependencies (caching layer)
RUN npm install

# Copy the source code into the container
COPY src src

# Expose the application port
EXPOSE 3000

# Run the application
CMD ["node", "src/server.js"]