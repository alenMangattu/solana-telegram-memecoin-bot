FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy Maven configuration and source code
COPY pom.xml .
COPY src ./src

# Copy Maven wrapper files
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn ./.mvn

# Make the Maven wrapper executable
RUN chmod +x mvnw

# Build the application
RUN ./mvnw clean package -DskipTests

# Create directory for application properties
RUN mkdir -p /app/src/main/resources

# Copy application.properties
COPY src/main/resources/application.properties /app/src/main/resources/

# Add Solana binaries to PATH
ENV PATH="/home/computer/.local/share/solana/install/active_release/bin:${PATH}"

EXPOSE 8888

# Run the application
CMD ["java", "-jar", "target/telegramBot-0.0.1-SNAPSHOT.jar"] 