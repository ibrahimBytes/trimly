# Trimly

> A simple, production-oriented URL shortener built with Spring Boot.

**Live:** [Trimly](https://trimly.s-ibrahim-devx.workers.dev)

Trimly turns long URLs into short, shareable links with custom aliases, expiration, authentication, and click analytics.

The application is intentionally closed-source. This repository contains the public product and engineering documentation.

---

## Product

Trimly focuses on the core URL-shortening workflow:

**Create → Share → Resolve → Manage → Analyze**

### Features

* Short URL generation
* Custom aliases
* Link expiration
* Authenticated link management
* Google sign-in
* Two-factor authentication
* Click analytics
* Redis-backed URL caching
* Asynchronous analytics processing
* PostgreSQL persistence
* Database migrations with Flyway
* Containerized deployment
* Cloud-oriented infrastructure

---

## Problem

Long URLs are difficult to share, remember, and manage.

The product requirement is simple:

> **Turn a long URL into a short link without making link management unnecessarily complicated.**

The engineering challenge is keeping that simple user experience reliable while supporting:

* Fast public redirects
* User ownership
* Secure management
* Persistent storage
* Analytics
* Expiration
* Cache performance
* Asynchronous processing

---

## Architecture

```text
                         ┌──────────────┐
                         │    Client    │
                         └──────┬───────┘
                                │
                                ▼
                      ┌──────────────────┐
                      │   Spring Boot    │
                      │      API         │
                      └────┬─────┬────┬──┘
                           │     │    │
              ┌────────────┘     │    └────────────┐
              ▼                  ▼                 ▼
       ┌────────────┐     ┌───────────┐     ┌───────────┐
       │ PostgreSQL │     │   Redis   │     │   Kafka   │
       │            │     │           │     │           │
       │ Persistence│     │   Cache   │     │ Analytics │
       └────────────┘     └───────────┘     └───────────┘
```

### Responsibility

| Component   | Responsibility                                         |
| ----------- | ------------------------------------------------------ |
| Spring Boot | API, business logic, authentication and URL operations |
| PostgreSQL  | Durable application data                               |
| Redis       | Frequently accessed URL lookup cache                   |
| Kafka       | Asynchronous click-event processing                    |
| Flyway      | Database schema versioning                             |
| Container   | Reproducible application runtime                       |

---

## Request Flows

### Create Short URL

```text
Client
  │
  ▼
Spring Boot API
  │
  ├── Validate URL / alias
  │
  ├── Persist link
  │       │
  │       ▼
  │   PostgreSQL
  │
  └── Cache link
          │
          ▼
        Redis
```

### Resolve Short URL

```text
Client
  │
  ▼
GET /{shortCode}
  │
  ▼
Redis
  │
  ├── Cache hit ──────────────► Redirect
  │
  └── Cache miss
          │
          ▼
      PostgreSQL
          │
          ▼
        Redis
          │
          ▼
       Redirect
```

The resolution path prioritizes the minimum work required to resolve the link.

### Analytics

```text
Short URL Request
       │
       ▼
    Redirect
       │
       ▼
   Kafka Event
       │
       ▼
Analytics Processing
       │
       ▼
   PostgreSQL
```

Analytics processing is separated from the primary redirect operation so that analytics work does not unnecessarily block the user-facing request.

---

# Engineering Decisions

## PostgreSQL as the source of truth

PostgreSQL stores durable application state, including users, links, and analytics data.

Redis is treated as a performance layer rather than the authoritative data store.

**Principle:**

> Cache for speed. Persist for correctness.

---

## Redis for URL resolution

Short-link resolution is a high-frequency operation.

Redis is used to reduce repeated database reads for frequently accessed links.

The database remains the fallback when cached data is unavailable.

---

## Kafka for asynchronous analytics

Recording a click is useful, but analytics processing does not need to be part of the critical redirect path.

Click events are therefore handled asynchronously through Kafka.

This separates:

```text
User-facing redirect
```

from:

```text
Analytics processing
```

---

## Ownership and authorization

Public URL resolution is independent from link ownership.

Private operations are authenticated and scoped to the user who owns the resource.

This creates a clear separation:

```text
Public
  └── Resolve short URL

Authenticated
  ├── Create links
  ├── Manage links
  ├── Configure expiration
  └── View analytics
```

---

## Authentication

Trimly supports authenticated access through:

* Google sign-in
* Two-factor authentication
* Protected management operations
* User-scoped resources

Security is treated as part of the application architecture rather than an additional layer added after the core functionality.

---

## Database migrations

Database schema changes are managed with Flyway.

This provides versioned migrations instead of relying on undocumented manual database changes.

```text
Application
     │
     ▼
  Flyway
     │
     ▼
PostgreSQL Schema
```

---

# Deployment

Trimly is containerized and designed around a cloud deployment model.

```text
                    Cloud Environment

                 ┌────────────────────┐
                 │  Spring Boot App   │
                 │    Container       │
                 └───────┬────────────┘
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
        PostgreSQL     Redis       Kafka
         Managed      Managed      Managed
```

Deployment principles include:

* Containerized application runtime
* Managed PostgreSQL
* Managed Redis
* Managed Kafka
* Environment-based configuration
* Database migrations
* Health checks
* Secrets kept outside source control

---

# Configuration

Application configuration is supplied through environment-specific configuration.

Sensitive values are not committed to the repository.

Examples include:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

REDIS_URL

KAFKA_BOOTSTRAP_SERVERS

GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET

JWT_SECRET
```

> The exact configuration depends on the deployment environment.

---

# Repository Scope

Trimly is intentionally **closed-source**.

This repository does not contain the application's private source code.

It exists to document:

* Product decisions
* System architecture
* Engineering decisions
* Deployment approach
* Public project information

No credentials or private application secrets are stored in the repository.

---

# Live Product

The current deployment is available here:

**[Open Trimly](https://trimly.s-ibrahim-devx.workers.dev)**

The live deployment represents the product beyond the documentation: the goal is to build, deploy, observe, and iterate on the system rather than treat architecture as a purely theoretical exercise.

---

# Status

**Live — Cloud Deployment / Active Development**

Trimly is currently being prepared for continued cloud deployment and product iteration.

Current engineering priorities include:

* Production hardening
* Infrastructure reliability
* Observability
* Performance
* Security
* Further analytics capabilities
* Continued product refinement

---

# Design Philosophy

Trimly is built from a small number of engineering principles:

1. **Keep the critical path small.**
2. **Use durable storage as the source of truth.**
3. **Use caching to improve performance, not correctness.**
4. **Move non-critical processing asynchronously.**
5. **Separate public resolution from private ownership.**
6. **Treat security as an architectural concern.**
7. **Prefer explicit, reproducible deployment over manual configuration.**

The product is simple.

The engineering underneath it is intentionally structured to keep it that way.

---

## Tech Stack

```text
Backend
└── Spring Boot

Database
└── PostgreSQL

Cache
└── Redis

Messaging
└── Kafka

Database Migration
└── Flyway

Authentication
├── Google Sign-In
└── Two-Factor Authentication

Deployment
└── Containerized / Cloud-oriented
```

---

## Project

**Trimly**
*A shorter path between a URL and its destination.*
 
