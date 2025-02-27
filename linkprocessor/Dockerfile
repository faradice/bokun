# Use Amazon Corretto JDK 20 for Kotlin 2.1.10 compatibility
FROM amazoncorretto:20

# Set working directory
WORKDIR /app

# Install SQLite (needed for database operations)
RUN yum install -y sqlite

# Copy JAR, config and db
COPY build/libs/email-processor.jar app.jar
COPY config.properties config.properties
COPY data/email_processor.db data/email_processor.d

# Expose the port
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
