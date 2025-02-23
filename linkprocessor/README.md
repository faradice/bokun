# Email Link Processor

## Overview

The **Email Link Processor** is a service that processes outbound email links. It replaces embedded links with managed URLs, tracks user clicks, and redirects users to their original destinations. The service also supports security features, analytics, and rate limiting.

## Features

- **URL Shortening**: Converts long URLs into short, manageable links.
- **Click Tracking**: Logs user clicks, including timestamps, IP addresses, and user agents.
- **Rate Limiting**: Prevents excessive clicks from a single IP within a short time.
- **Expiration Handling**: Supports expiration dates for links to enhance security.
- **Monitoring & Alerting**: Integrated with Prometheus, Grafana, and Slack alerts.
- **Scalability**: Can be deployed using Docker or Kubernetes with Helm.

## API Endpoints

### 1. Create a Shortened Link

**Endpoint:** `POST /api/links`

**Request Body:**

```json
{
  "originalUrl": "https://example.com",
  "expiration": "2025-12-31T23:59:59"
}
```

**Response:**

```json
{
  "shortId": "abcd1234",
  "originalUrl": "https://example.com",
  "expiration": "2025-12-31T23:59:59"
}
```

### 2. Redirect to Original URL

**Endpoint:** `GET /api/r/{shortId}`

**Response:**

- **302 Redirect** → Redirects to the original URL.
- **410 Gone** → If the link has expired.
- **429 Too Many Requests** → If rate limit is exceeded.
- **404 Not Found** → If the short link does not exist.

### 3. Get Click Analytics

**Endpoint:** `GET /api/analytics`

**Response:**

```json
{
  "abcd1234": 42,
  "efgh5678": 15
}
```

## Usage Guide

### 1. Running with Docker

```sh
docker-compose up -d
```

- Access the service at `http://localhost:8081`
- View Prometheus at `http://localhost:9090`
- View Grafana at `http://localhost:3000`

### 2. Running with Kubernetes & Helm

```sh
helm install email-processor email-processor-chart/
```

- Check deployed services:

```sh
kubectl get services
```

- Forward Grafana port:

```sh
kubectl port-forward svc/prometheus-grafana 3000:80
```

### 3. Testing Alerts

Trigger a failure to check Slack alerts:

```sh
curl -X POST http://localhost:8081/api/trigger-error
```

## Monitoring & Alerts

- **Prometheus**: Collects real-time metrics.
- **Grafana**: Visualizes service performance.
- **Slack Alerts**: Notifies failures and rate limits.

## Design Rationale

### Why did we choose this solution?
This approach was chosen to balance **scalability, security, and monitoring** while keeping the service simple and efficient. By using a lightweight **Javalin-based Kotlin service**, it remains easy to deploy and manage. Additionally, using **SQLite** ensures quick storage without requiring external dependencies, while Kubernetes support allows for seamless scaling when needed.

The service is built with **observability and resilience** in mind, integrating Prometheus and Grafana for real-time monitoring, rate-limiting for abuse prevention, and Slack alerts to notify on failures.

### How to ensure smooth scaling at Bókun?
To scale this service effectively for Bókun’s outbound email processing, we recommend the following steps:

1. **Deploy on Kubernetes**
   - Use **auto-scaling (HPA)** based on request load.
   - Run multiple replicas of the service behind a load balancer.

2. **Use a Distributed Database**
   - Migrate from SQLite to **PostgreSQL or MySQL** for multi-instance support.
   - Use **Redis** for caching frequent queries.

3. **Optimize Performance**
   - Enable **connection pooling** for database access.
   - Implement **background job processing** for analytics.
   - Compress database logs to reduce storage costs.

4. **Enhance Security**
   - Implement **API authentication** for link creation.
   - Add **encryption** for stored URLs.
   - Implement **bot detection** to prevent spam clicks.

5. **Improve Monitoring & Alerting**
   - Use **Grafana dashboards** for real-time observability.
   - Configure **Alertmanager thresholds** for proactive issue resolution.
   - Implement **distributed tracing** with OpenTelemetry.

By following these steps, the Email Link Processor will efficiently handle large-scale email tracking at Bókun without compromising performance or security.

## Contributing

- Fork the repository.
- Submit a pull request with improvements.
- Report issues via GitHub.

## License

MIT License

