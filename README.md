# Tenant Service

A core supporting microservice in the Traversium platform responsible for managing tenants used by Traversium Tenants.
The service handles tenant lifecycle logic, persistence, and tenant-to-user linkage, enabling simplified tenant-based registration and login across web and mobile clients.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Service](#running-the-service)
- [API Documentation](#api-documentation)
- [Architecture](#architecture)
- [Integration](#integration)
- [Monitoring and Health](#monitoring-and-health)

## Overview

The Tenant Service is used by **Traversium Tenants** to:

- Display all active tenants
- Create new tenants programmatically (instead of manually via Google Cloud Console)
- Persist tenants in a database
- Link users to tenants
- Resolve tenant identifiers by tenant name for frontend and mobile usage 

Although tenants exist in external systems (e.g. Firebase / Google Cloud), this service acts as the central authority for tenant metadata and tenant-user relationships inside Traversium.

## Features

### Tenant Management
- Retrieve all active tenants
- Create new tenants via REST API
- Store and manage tenant metadata in a database
- Avoid manual tenant creation in Google Cloud Console

### User–Tenant Linking
- Link users to tenants
- Support creation of an admin user for each tenant
- Admin users are technically regular users, but are explicitly linked to a tenant in Tenant Service

### Frontend Tenant Resolution
- Resolve tenant ID by tenant name
- Simplifies registration and login flows for web and mobile clients
- Eliminates the need for users to manually enter tenant Firebase IDs

### Security
- Firebase Authentication integration
- JWT token validation

### Integration
- REST API endpoints
- Prometheus metrics for monitoring
- Swagger documentation

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- Firebase project with service account credentials
- Kafka cluster (used by Spring Cloud Bus for configuration refresh)
- Docker (optional, for containerized deployment)

## Configuration

### Application Properties

The service is configured via `src/main/resources/application.properties`. Key configurations:

```properties
# Application
spring.application.name=TenantService

# Service port
server.port=8085

# Config server
spring.config.import=optional:configserver:http://localhost:8888/

# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/tenants_db
spring.datasource.username=<user>
spring.datasource.password=<password>
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.flyway.schemas=public
spring.flyway.locations=classpath:db/migration

# Endpoints
management.endpoints.web.exposure.include=health,info,prometheus,refresh,busrefresh
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true

management.endpoint.health.group.readiness.include=readinessState
management.endpoint.health.group.readiness.show-details=always

management.endpoint.health.group.liveness.include=livenessState,ping
management.endpoint.health.group.liveness.show-details=always

management.health.ping.enabled=true
```

## Configuration Notes

Some notes on property configuration:

- **`spring.config.import`**: Config Server address and port
- **`spring.kafka.bootstrap-servers`**: Kafka broker address for connecting to the Kafka cluster
- **Flyway**: Handles schema migration for tenant persistence

## Running the Service

### Local Development

```bash
# Run with Maven
mvn spring-boot:run

# Or build and run JAR (e.g. version 1.1.0-SNAPSHOT)
mvn clean package
java -jar target/TenantService-1.1.0-SNAPSHOT.jar
```

### Using Docker

```bash
# Build Docker image
docker build -t traversium-tenant-service .

# Run container
docker run -p 8085:8085 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/tenants_db \
  traversium-tenant-service
```

### Verify Service is Running

```bash
# Health check
curl http://localhost:8085/actuator/health

# Liveness probe
curl http://localhost:8085/actuator/health/liveness

# Readiness probe
curl http://localhost:8085/actuator/health/readiness
```

## API Documentation

### Swagger UI

Once the service is running, access the Swagger UI:
```
http://localhost:8085/swagger-ui.html
```


### Key Endpoints

- `GET /rest/v1/tenants` - Get all tenants
- `GET /rest/v1/tenants/name/{name}` - Get tenant by name (returns full data: username + Firebase ID)
- `POST /rest/v1/tenants` - Create a new tenant 
- `POST /rest/v1/tenants/{tenantId}/admin` - Create a new admin user for an existing tenant 

## Architecture

### Multi-Tenancy
- Each tenant represents an isolated environment
- Tenants are resolved early and propagated to downstream services
- Other services rely on Tenant Service for tenant validation

## Integration

### Traversium Ecosystem

- Traversium Tenants (Web)
- Mobile App
- Authentication and User-related services

### External Systems

- Firebase / Google Cloud (tenant creation)
- PostgreSQL (tenant persistence)

## Monitoring and Health

### Health Endpoints

- **Liveness**: `/actuator/health/liveness` - Indicates if the application is running
- **Readiness**: `/actuator/health/readiness` - Indicates if the application is ready to serve traffic
- **Database**: `/actuator/health/db` - Database connectivity check

### Metrics

Prometheus metrics exposed at:
```bash
curl http://localhost:8085/actuator/prometheus
```

Key metrics:
- JVM metrics (memory, threads, GC)
- HTTP request metrics
- Database connection pool metrics
- Custom business metrics

### Logging

Logs are structured in JSON format (Logstash encoder) for ELK Stack integration:
- Application logs: Log4j2
- Request/response logging
- Error tracking
