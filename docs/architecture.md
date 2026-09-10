# Trimly Architecture

## System boundary

Trimly consists of a client-facing application and a backend service responsible for URL management, authentication, redirects, and analytics.

The implementation is closed-source. This document describes the architecture without exposing implementation details.

## High-level flow

```text
User
 │
 ▼
Frontend
 │
 ▼
Spring Boot API
 │
 ├── PostgreSQL
 │     └── durable application data
 │
 ├── Redis
 │     └── URL/cache acceleration
 │
 └── Kafka
       └── asynchronous click analytics
````

## URL creation

```text
Client
  │
  ▼
Authenticated API
  │
  ▼
Validate request
  │
  ▼
Generate or validate alias
  │
  ▼
Persist URL
  │
  ▼
Return short URL
```

## URL resolution

```text
Incoming short URL
        │
        ▼
      Redis
        │
   ┌────┴────┐
   │         │
 HIT        MISS
   │         │
   │         ▼
   │      PostgreSQL
   │         │
   └────┬────┘
        ▼
    Destination
        │
        ▼
   Click event
        │
        ▼
      Kafka
        │
        ▼
    Analytics
```

## Ownership model

Public redirection does not require authentication.

Private operations such as link management and analytics are owner-scoped.

This separates the public function of a short URL from the private management of the resource that created it.

## Data infrastructure

### PostgreSQL

Durable source of truth for users, URLs, authentication state, two-factor authentication data, and analytics data.

### Redis

Used for low-latency URL lookup and cache-backed operations.

### Kafka

Used to decouple click-event production from analytics processing.

This prevents analytics processing from becoming a synchronous dependency of the redirect path.
EOF

cat > docs/engineering-decisions.md <<'EOF'

# Engineering Decisions

## Why PostgreSQL?

URL ownership, authentication, expiration, and other application state require durable relational storage and transactional guarantees.

PostgreSQL provides the consistency model needed for these relationships.

## Why Redis?

URL redirection is a high-read operation.

Caching frequently accessed short-code mappings avoids requiring PostgreSQL for every redirect.

## Why Kafka?

A redirect should not need to wait for analytics processing to complete.

The click event can be produced asynchronously and processed independently.

This separates the latency-sensitive redirect path from the analytics pipeline.

## Why Flyway?

Database schema changes need to be explicit, versioned, and reproducible.

Flyway provides migration history rather than relying on Hibernate to modify production schemas.

## Why containers?

The application and its runtime dependencies should behave consistently across development and deployment environments.

Containerization provides a reproducible application runtime.

## Why environment-based configuration?

Infrastructure credentials and deployment-specific values should not be embedded in source code.

Configuration is supplied through environment variables at runtime.
EOF

cat > docs/deployment.md <<'EOF'

# Deployment Architecture

Trimly's deployment separates application code from infrastructure services.

```text
                    Internet
                       │
                       ▼
                 Cloud platform
                       │
                       ▼
                Spring Boot API
                  container
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
      PostgreSQL      Redis        Kafka
       managed       managed      managed
```

## Configuration

The application receives environment-specific configuration at runtime.

Examples include:

* database connection
* Redis connection
* Kafka connection
* JWT secret
* Google client configuration
* application base URL

Secrets are not committed to source control.

## Health

The backend exposes an application health endpoint for platform-level health checks.

## Database migrations

Flyway migrations are executed as part of application startup and the application validates the resulting schema through Hibernate.

## Source-code policy

The application implementation is closed-source.

This repository intentionally contains architecture and deployment documentation rather than the backend and frontend source code. 