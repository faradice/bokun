# Bókun Link Service

## Overview

The **Bókun Link Service** processes outbound email links for Bókun, transforming original links into managed URLs that allow tracking and analysis before redirecting users to their intended destinations. The service captures user interactions, logs analytics, and ensures security compliance while maintaining a seamless user experience.

## Features

- **Email Link Processing**: Intercepts and replaces all links in emails with managed URLs.
- **Click Tracking & Analytics**: Logs user interactions, tracking clicks, expiration, and rate limits.
- **Expiration Handling**: Links automatically expire after a set period.
- **Rate Limiting**: Prevents excessive requests from the same IP.
- **Analytics Dashboard**: Provides insights into click activity, most-clicked links, and traffic trends.
- **Docker Multi-Arch Support**: Runs on **both Linux AMD64 & ARM64 (Mac M1/M2).**

## Live Deployment

The service is deployed and accessible at:
- **Email Test Form:** [http://206.189.245.178:8080/test-email](http://206.189.245.178:8080/test-email)
- **Analytics Dashboard:** [http://206.189.245.178:8080/analytics](http://206.189.245.178:8080/analytics)

## How to Run Locally

### Prerequisites

- **JDK 20** or later
- **Gradle**
- **SQLite**
- **Docker (if running in a container)**

### Steps to Build and Run Locally

1. Clone the repository:
   ```sh
   git clone git@github.com:faradice/bokun.git
   cd bokun-link-service
   ```
2. Build the JAR file:
   ```sh
   ./gradlew clean build fatJar
   ```
3. Verify that the JAR file was created:
   ```sh
   ls build/libs/
   ```
   Ensure that **`email-processor.jar`** is inside `build/libs/`.
4. Run the service:
   ```sh
   java -jar build/libs/email-processor.jar
   ```
5. Open the email test form in a browser:
   ```sh
   http://localhost:8080/test-email
   ```

---

## Running with Docker

### **Building and Running Multi-Arch Docker Images (Linux AMD64 & ARM64)**

To support **both Intel (AMD64) and Mac M1/M2 (ARM64)** platforms, use **Docker Buildx**.

### **Steps to Build and Run Locally with Multi-Platform Support**
1. **Ensure Docker Buildx is enabled:**
   ```sh
   docker buildx create --use
   ```
2. **Build the multi-architecture image:**
   ```sh
   ./gradlew clean build fatJar
   docker buildx build --platform linux/amd64,linux/arm64 -t raggithor/email-processor:latest --push .
   ```
3. **Pull and run the correct image for your platform:**
   ```sh
   docker pull raggithor/email-processor:latest
   docker run -p 8080:8080 raggithor/email-processor
   ```

🚀 **Docker will automatically select the correct architecture for your system!**

---

### **Running from Public Docker Image**
You can also pull and run the **pre-built Docker image** from Docker Hub:

```sh
# Pull the latest version
docker pull raggithor/email-processor:latest

# Run the container
docker run -p 8080:8080 raggithor/email-processor
```

---

## API Endpoints

### **Link Processing**
- `POST /process-email` - Replaces links in email content with trackable URLs.

### **Redirection & Confirmation**
- `GET /r/{shortId}` - Redirects a user to the original URL after logging the click.
- `GET /confirm/{shortId}` - Displays a confirmation page before redirection.

### **Analytics**
- `GET /analytics` - Displays a dashboard of click statistics.
- `GET /links` - Returns all stored links and their metadata.

---

## Analytics Features

- **Most Clicked Links**: Identifies the most popular links.
- **Clicks Per Day**: Shows daily engagement trends.
- **Hourly Click Trends**: Tracks peak traffic hours.
- **Most Frequent Visitors**: Lists the most active IP addresses.
- **Expiration & Rate-Limited Links**: Highlights expired and restricted links.

---

## How to Ensure Smooth Scaling at Bókun?

To scale this service effectively for Bókun’s outbound email processing, we recommend the following steps:

### **1. Deploy on Kubernetes**
- Use **auto-scaling (HPA)** based on request load.
- Run multiple replicas of the service behind a load balancer.

### **2. Use a Distributed Database**
- Migrate from SQLite to **PostgreSQL or MySQL** for multi-instance support.
- Use **Redis** for caching frequent queries.

### **3. Optimize Performance**
- Enable **connection pooling** for database access.
- Implement **background job processing** for analytics.
- Compress database logs to reduce storage costs.

### **4. Enhance Security**
- Implement **API authentication** for link creation.
- Add **encryption** for stored URLs.
- Implement **bot detection** to prevent spam clicks.

### **5. Improve Monitoring & Alerting**
- Use **Grafana dashboards** for real-time observability.
- Configure **Alertmanager thresholds** for proactive issue resolution.
- Implement **distributed tracing** with OpenTelemetry.

By following these steps, the Email Link Processor will efficiently handle large-scale email tracking at Bókun without compromising performance or security.

---

## Future Improvements

- **User Authentication**: Restrict analytics to authorized users.
- **Advanced Security**: Detect malicious or automated clicks.
- **Scalability Enhancements**: Support for distributed databases.
- **Extended Dashboard Features**: Additional visualization tools for deeper insights.
```
