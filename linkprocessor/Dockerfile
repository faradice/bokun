# Use Amazon Corretto JDK 20 for Kotlin 2.1.10 compatibility
FROM amazoncorretto:20

# Set working directory
WORKDIR /app

# Install SQLite (needed for database operations)
RUN yum install -y sqlite

# Copy the fat JAR
COPY build/libs/email-processor.jar app.jar

# Expose the port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
