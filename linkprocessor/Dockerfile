# Amazon Corretto JDK 20 for Kotlin 2.1.10 compatibility
FROM amazoncorretto:20

# Set working directory
WORKDIR /app

# Copy build files
COPY build/libs/email-processor.jar app.jar

# Expose the port (ensure it matches your config)
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
