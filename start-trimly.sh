#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# Trimly Backend Startup Script
# ============================================================

# Generate a fresh JWT secret for this startup session
export JWT_SECRET="$(openssl rand -base64 48)"

# Resolve the directory where this script lives
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# Docker Compose command
if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
else
    echo "ERROR: Docker Compose is not installed or unavailable."
    exit 1
fi

print_header() {
    echo "========================================"
    echo "        Trimly Backend Startup"
    echo "========================================"
}

cleanup_on_error() {
    echo ""
    echo "========================================"
    echo "        Startup FAILED"
    echo "========================================"
    echo ""
    echo "The command that failed:"
    echo "  $BASH_COMMAND"
    echo ""
}

trap cleanup_on_error ERR

# ============================================================
# Start
# ============================================================

print_header

echo ""
echo "[1/6] Navigating to project..."

cd "$PROJECT_DIR"

echo "Project: $PROJECT_DIR"

if [[ ! -f "$PROJECT_DIR/docker-compose.yml" && \
      ! -f "$PROJECT_DIR/docker-compose.yaml" ]]; then
    echo "ERROR: docker-compose.yml not found."
    exit 1
fi

if [[ ! -x "$PROJECT_DIR/mvnw" ]]; then
    echo "ERROR: ./mvnw is missing or not executable."
    echo "Run: chmod +x ./mvnw"
    exit 1
fi

# ============================================================
# 2. Check Docker
# ============================================================

echo ""
echo "[2/6] Checking Docker..."

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker is not running."
    exit 1
fi

echo "Docker: OK"
echo "Docker Compose: OK"

# ============================================================
# 3. Start PostgreSQL
# ============================================================

echo ""
echo "[3/6] Starting PostgreSQL..."

if docker ps -a --format '{{.Names}}' | grep -Fxq "url_shortener_postgres"; then
    echo ""
    echo "ERROR: Existing container 'url_shortener_postgres' found."
    echo ""
    echo "Remove it manually if it is no longer needed:"
    echo "  docker rm -f url_shortener_postgres"
    echo ""
    exit 1
fi

"${COMPOSE[@]}" up -d postgres

echo ""
echo "PostgreSQL status:"
"${COMPOSE[@]}" ps postgres

# ============================================================
# 4. Start Redis
# ============================================================

echo ""
echo "[4/6] Starting Redis..."

"${COMPOSE[@]}" up -d redis

echo ""
echo "Redis status:"
"${COMPOSE[@]}" ps redis

# ============================================================
# 5. Start Kafka + verify infrastructure
# ============================================================

echo ""
echo "[5/6] Starting Kafka..."

"${COMPOSE[@]}" up -d kafka

echo ""
echo "Kafka status:"
"${COMPOSE[@]}" ps kafka

echo ""
echo "========================================"
echo "        Infrastructure Status"
echo "========================================"
echo ""

"${COMPOSE[@]}" ps

echo ""
echo "Required services:"
echo "  PostgreSQL -> localhost:5431"
echo "  Redis      -> localhost:6380"
echo "  Kafka      -> localhost:9092"

echo ""
echo "Waiting briefly for infrastructure..."
sleep 5

echo ""
echo "Checking required services..."

if ! "${COMPOSE[@]}" ps --status running --services | grep -Fxq "postgres"; then
    echo "ERROR: PostgreSQL is not running."
    "${COMPOSE[@]}" logs --tail=50 postgres || true
    exit 1
fi

if ! "${COMPOSE[@]}" ps --status running --services | grep -Fxq "redis"; then
    echo "ERROR: Redis is not running."
    "${COMPOSE[@]}" logs --tail=50 redis || true
    exit 1
fi

if ! "${COMPOSE[@]}" ps --status running --services | grep -Fxq "kafka"; then
    echo "ERROR: Kafka is not running."
    "${COMPOSE[@]}" logs --tail=50 kafka || true
    exit 1
fi

echo ""
echo "PostgreSQL: RUNNING"
echo "Redis:      RUNNING"
echo "Kafka:      RUNNING"

# ============================================================
# 6. Run tests
# ============================================================

echo ""
echo "[6/6] Running backend tests..."
echo ""

./mvnw test

echo ""
echo "========================================"
echo "        Tests PASSED"
echo "========================================"
 
 
# ============================================================
# Start Spring Boot
# ============================================================

echo ""
echo "Starting Trimly Spring Boot backend..."
echo ""
echo "Backend will be available at:"
echo "  http://localhost:8080"
echo ""

./mvnw spring-boot:run 