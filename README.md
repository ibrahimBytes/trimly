# Trimly

**Trimly is a consumer-friendly URL shortener designed around one idea: remove unnecessary complexity and create a shorter, clearer path.**

## Product

Trimly lets people turn long URLs into short, shareable links while providing a focused management experience for their links.

### Core capabilities

- Short URL generation
- Custom aliases
- Link expiration
- Authenticated link management
- Two-factor authentication
- Google sign-in
- Click analytics
- Redis-backed caching
- Asynchronous analytics processing with Kafka
- PostgreSQL persistence
- Containerized deployment
- Cloud deployment architecture

## Architecture

```text
                    ┌──────────────┐
                    │    Client    │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   Backend    │
                    │ Spring Boot  │
                    └──┬───┬───┬───┘
                       │   │   │
              ┌────────┘   │   └─────────┐
              ▼            ▼             ▼
        PostgreSQL       Redis         Kafka
        persistence      cache       analytics
````

The implementation is intentionally closed-source.

This repository contains the public product and engineering documentation rather than the application's source code.

## Engineering

Trimly was designed around several principles:

* Keep public URL resolution independent from ownership.
* Scope private management and analytics by authenticated user.
* Use PostgreSQL as the durable source of truth.
* Use Redis to reduce repeated database reads.
* Process click analytics asynchronously.
* Validate database schema with Flyway.
* Run the application in a containerized environment.
* Keep deployment configuration separate from application secrets.

See the `docs/` directory for the architectural and engineering overview.

## Deployment

The production-oriented architecture uses:

* Containerized Spring Boot backend
* Managed PostgreSQL
* Managed Redis
* Managed Kafka
* Environment-based configuration
* Health checks
* Database migrations

No credentials or private application source are stored in this repository.

## Status

Trimly is a closed-source engineering project currently being prepared for cloud deployment.

--- 