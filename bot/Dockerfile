FROM eclipse-temurin:21-jdk

WORKDIR /app

# Install required packages for Solana CLI
RUN apt-get update && apt-get install -y \
    curl \
    build-essential \
    pkg-config \
    libudev-dev \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Rust
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:${PATH}"

# Install Solana CLI
RUN sh -c "$(curl -sSfL https://release.solana.com/v1.17.16/install)" && \
    ln -s /root/.local/share/solana/install/active_release/bin/solana /usr/local/bin/solana

# Install SPL Token CLI
RUN cargo install spl-token-cli

# Copy the Maven configuration file
COPY pom.xml .

# Copy the source code
COPY src ./src

# Copy the Maven wrapper
COPY mvnw .
COPY .mvn ./.mvn

# Make the Maven wrapper executable
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

# Create directory for application properties
RUN mkdir -p /app/src/main/resources

# Copy application.properties
COPY src/main/resources/application.properties /app/src/main/resources/

EXPOSE 8888

# Run the application
CMD ["java", "-jar", "target/telegramBot-0.0.1-SNAPSHOT.jar"] 