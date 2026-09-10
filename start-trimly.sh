#!/usr/bin/env bash

set -e

PROJECT_DIR="$HOME/url-shortener"

echo "========================================"
echo "        Trimly Backend Startup"
echo "========================================"
# shellcheck disable=SC2155
# ----------------------------------------
# 1. Navigate to backend project
# ----------------------------------------

echo ""
echo "[1/6] Navigating to project..."
cd "$PROJECT_DIR"

echo "Project: $PROJECT_DIR"
#export JWT_SECRET="$(openssl rand -base64 48)"
# ----------------------------------------
# 2. Start PostgreSQL
# ----------------------------------------

echo ""
echo "[2/6] Starting PostgreSQL..."
docker compose up -d postgres

echo "Checking PostgreSQL..."
docker compose ps postgres

# ----------------------------------------
# 3. Start Redis
# ----------------------------------------

echo ""
echo "[3/6] Starting Redis..."
docker compose up -d redis

echo "Checking Redis..."
docker compose ps redis

# ----------------------------------------
# 4. Start Kafka
# ----------------------------------------

echo ""
echo "[4/6] Starting Kafka..."
docker compose up -d kafka

echo "Checking Kafka..."
docker compose ps kafka

# ----------------------------------------
# 5. Verify all infrastructure
# ----------------------------------------

echo ""
echo "[5/6] Verifying infrastructure..."
echo ""

docker compose ps

echo ""
echo "Required services:"
echo "  PostgreSQL -> localhost:5431"
echo "  Redis      -> localhost:6380"
echo "  Kafka      -> localhost:9092"

echo ""
echo "Waiting briefly for infrastructure..."
sleep 5

# ----------------------------------------
# 6. Run tests
# ----------------------------------------

echo ""
echo "[6/6] Running backend tests..."
echo ""

./mvnw test

echo ""
echo "========================================"
echo "        Tests PASSED"
echo "========================================"

# ----------------------------------------
# Start Spring Boot
# ----------------------------------------

echo ""
echo "Starting Trimly Spring Boot backend..."
echo "Backend will be available at:"
echo "http://localhost:8080"
echo ""
./mvnw spring-boot:run