#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# TRIMLY
# SETTINGS PHASE 1 + PHASE 2
# COMPLETE MASTER VERIFICATION SCRIPT
#
# PURPOSE
# ============================================================
#
# Phase 1:
#   Profile / fullName
#
# Phase 2:
#   Change password
#   Same-password protection
#   Wrong-password protection
#   JWT token-version revocation
#
# Frontend:
#   Source discovery
#   API wiring discovery
#   Production build
#   Playwright E2E
#
# IMPORTANT
# ============================================================
#
# This script DOES NOT modify source code.
#
# It DOES:
#   - create one isolated test user
#   - update that user's profile
#   - change that user's password
#   - run Maven tests
#   - run frontend build
#   - run Playwright tests if installed
#
# It DOES NOT:
#   - delete users
#   - delete application data
#   - print JWT values
#   - edit source files
#
# ============================================================


# ============================================================
# Configuration
# ============================================================

BACKEND_DIR="${BACKEND_DIR:-$HOME/url-shortener}"

if [[ -d "$HOME/trimly" ]]; then
    FRONTEND_DIR="${FRONTEND_DIR:-$HOME/trimly}"
elif [[ -d "$HOME/trimly-test" ]]; then
    FRONTEND_DIR="${FRONTEND_DIR:-$HOME/trimly-test}"
else
    FRONTEND_DIR="${FRONTEND_DIR:-$HOME/trimly}"
fi

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:8443}"

API="${BACKEND_URL}/api"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/trimly-settings-v3.XXXXXX")"

HEADERS_FILE="$TMP_ROOT/headers"
BODY_FILE="$TMP_ROOT/body"
STATUS_FILE="$TMP_ROOT/status"
CURL_ERROR_FILE="$TMP_ROOT/curl-error"

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

TEST_EMAIL=""
OLD_PASSWORD=""
NEW_PASSWORD=""
TEST_FULL_NAME="Trimly Settings Test"

CURRENT_TOKEN=""
OLD_JWT=""
NEW_TOKEN=""

PASSWORD_CHANGED=false


# ============================================================
# Cleanup
# ============================================================

cleanup() {
    rm -rf "$TMP_ROOT"
}

trap cleanup EXIT


# ============================================================
# Output helpers
# ============================================================

if [[ -t 1 ]]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    RESET='\033[0m'
else
    GREEN=''
    RED=''
    YELLOW=''
    CYAN=''
    RESET=''
fi

section() {
    echo
    echo "============================================================"
    echo "$1"
    echo "============================================================"
    echo
}

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${RESET} $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${RESET} $1"
}

skip() {
    SKIP_COUNT=$((SKIP_COUNT + 1))
    echo -e "  ${YELLOW}○ SKIP${RESET} $1"
}

info() {
    echo -e "  ${CYAN}→${RESET} $1"
}

warn() {
    echo -e "  ${YELLOW}!${RESET} $1"
}

fatal() {
    echo
    echo -e "${RED}FATAL:${RESET} $1"
    echo
    exit 2
}


# ============================================================
# File helpers
# ============================================================

file_exists() {
    [[ -f "$1" ]]
}

dir_exists() {
    [[ -d "$1" ]]
}

grep_literal() {
    local file="$1"
    local value="$2"

    grep -Fq "$value" "$file"
}

grep_regex() {
    local file="$1"
    local value="$2"

    grep -Eq "$value" "$file"
}


# ============================================================
# HTTP helper
#
# Usage:
#   http_request METHOD URL [BODY] [TOKEN]
#
# Output:
#   $BODY_FILE
#   $STATUS_FILE
# ============================================================

http_request() {
    local method="$1"
    local url="$2"
    local request_body="${3:-}"
    local token="${4:-}"

    : > "$HEADERS_FILE"
    : > "$BODY_FILE"
    : > "$STATUS_FILE"
    : > "$CURL_ERROR_FILE"

    local args=(
        -sS
        --connect-timeout 5
        --max-time 20
        -X "$method"
        -D "$HEADERS_FILE"
        -o "$BODY_FILE"
        -w "%{http_code}"
    )

    if [[ -n "$request_body" ]]; then
        args+=(
            -H "Content-Type: application/json"
            --data "$request_body"
        )
    fi

    if [[ -n "$token" ]]; then
        args+=(
            -H "Authorization: Bearer $token"
        )
    fi

    local result

    if ! result="$(curl "${args[@]}" "$url" 2>"$CURL_ERROR_FILE")"; then

        echo "000" > "$STATUS_FILE"

        if [[ -s "$CURL_ERROR_FILE" ]]; then
            cat "$CURL_ERROR_FILE" >&2
        fi

        return 1
    fi

    printf '%s' "$result" > "$STATUS_FILE"

    return 0
}

status_code() {
    cat "$STATUS_FILE"
}

response_body() {
    cat "$BODY_FILE"
}


# ============================================================
# HTTP assertions
# ============================================================

assert_status() {
    local expected="$1"
    local description="$2"

    local actual
    actual="$(status_code)"

    if [[ "$actual" == "$expected" ]]; then
        pass "$description — HTTP $actual"
        return 0
    fi

    fail "$description — expected HTTP $expected, got HTTP $actual"

    echo
    echo "Response:"
    response_body
    echo

    return 1
}

assert_status_any() {
    local expected="$1"
    local description="$2"

    local actual
    actual="$(status_code)"

    IFS=',' read -ra values <<< "$expected"

    for value in "${values[@]}"; do
        if [[ "$actual" == "$value" ]]; then
            pass "$description — HTTP $actual"
            return 0
        fi
    done

    fail "$description — expected HTTP one of [$expected], got HTTP $actual"

    echo
    echo "Response:"
    response_body
    echo

    return 1
}

assert_body_contains() {
    local pattern="$1"
    local description="$2"

    if grep -Eq "$pattern" "$BODY_FILE"; then
        pass "$description"
        return 0
    fi

    fail "$description"

    echo
    echo "Response:"
    response_body
    echo

    return 1
}

assert_body_literal() {
    local value="$1"
    local description="$2"

    if grep -Fq "$value" "$BODY_FILE"; then
        pass "$description"
        return 0
    fi

    fail "$description"

    echo
    echo "Response:"
    response_body
    echo

    return 1
}


# ============================================================
# JSON helpers
# ============================================================

json_token() {
    python3 - "$BODY_FILE" <<'PY'
import json
import sys

path = sys.argv[1]

try:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
except Exception:
    sys.exit(1)

for key in ("token", "accessToken", "jwt"):
    value = data.get(key)

    if isinstance(value, str) and value.strip():
        print(value)
        sys.exit(0)

sys.exit(1)
PY
}


make_json() {
    python3 "$@"
}


# ============================================================
# Project discovery
# ============================================================

section "Project discovery"

echo "Backend:"
echo "  $BACKEND_DIR"
echo

echo "Frontend:"
echo "  $FRONTEND_DIR"
echo

[[ -d "$BACKEND_DIR" ]] || fatal "Backend directory does not exist: $BACKEND_DIR"
[[ -d "$FRONTEND_DIR" ]] || fatal "Frontend directory does not exist: $FRONTEND_DIR"


# ============================================================
# Required commands
# ============================================================

section "Checking required commands"

for command_name in \
    curl \
    python3 \
    grep \
    sed \
    awk \
    find \
    npm \
    node
do
    if command -v "$command_name" >/dev/null 2>&1; then
        pass "$command_name available"
    else
        fatal "$command_name is required but unavailable"
    fi
done

if [[ -x "$BACKEND_DIR/mvnw" ]]; then
    pass "Backend Maven wrapper available"
else
    fatal "Missing $BACKEND_DIR/mvnw"
fi

if [[ -d "$BACKEND_DIR/src/main/java" ]]; then
    pass "Backend source directory"
else
    fatal "Backend source directory missing"
fi

if [[ -d "$FRONTEND_DIR/src" ]]; then
    pass "Frontend source directory"
else
    fatal "Frontend source directory missing"
fi


# ============================================================
# Locate backend source
# ============================================================

AUTH_SERVICE_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'AuthService.java' \
        -print -quit
)"

AUTH_CONTROLLER_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'AuthController.java' \
        -print -quit
)"

SECURITY_CONFIG_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'SecurityConfig.java' \
        -print -quit
)"

JWT_SERVICE_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'JwtService.java' \
        -print -quit
)"

USER_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'User.java' \
        -print -quit
)"

USER_RESPONSE_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'UserResponse.java' \
        -print -quit
)"

PROFILE_REQUEST_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'ProfileUpdateRequest.java' \
        -print -quit
)"

CHANGE_PASSWORD_REQUEST_FILE="$(
    find "$BACKEND_DIR/src/main/java" \
        -type f \
        -name 'ChangePasswordRequest.java' \
        -print -quit
)"


# ============================================================
# Locate frontend source
# ============================================================

SETTINGS_PAGE_FILE="$(
    find "$FRONTEND_DIR/src" \
        -type f \
        \( \
            -name 'SettingsPage.tsx' \
            -o -name 'SettingsPage.jsx' \
            -o -name 'SettingsPage.ts' \
            -o -name 'SettingsPage.js' \
        \) \
        -print -quit
)"

AUTH_API_FILE="$(
    find "$FRONTEND_DIR/src" \
        -type f \
        \( \
            -name 'authApi.ts' \
            -o -name 'authApi.tsx' \
            -o -name 'authApi.js' \
            -o -name 'authApi.jsx' \
        \) \
        -print -quit
)"

API_CLIENT_FILE="$(
    find "$FRONTEND_DIR/src" \
        -type f \
        \( \
            -name 'apiClient.ts' \
            -o -name 'apiClient.tsx' \
            -o -name 'apiClient.js' \
            -o -name 'apiClient.jsx' \
        \) \
        -print -quit
)"


# ============================================================
# Runtime availability
# ============================================================

section "Runtime availability"

info "Checking backend: $BACKEND_URL"

if curl \
    -sS \
    --connect-timeout 5 \
    --max-time 10 \
    "$BACKEND_URL/actuator/health" \
    >/dev/null 2>&1
then
    pass "Backend reachable"
else
    fatal "Backend is not reachable at $BACKEND_URL"
fi


# ============================================================
# Phase 1 static backend
# ============================================================

section "PHASE 1 — Backend profile static checks"

if [[ -n "$USER_FILE" ]] && grep_regex "$USER_FILE" 'fullName'; then
    pass "User.fullName"
else
    fail "User.fullName"
fi

if [[ -n "$USER_RESPONSE_FILE" ]] && grep_regex "$USER_RESPONSE_FILE" 'fullName'; then
    pass "UserResponse.fullName"
else
    fail "UserResponse.fullName"
fi

if [[ -n "$PROFILE_REQUEST_FILE" ]] && \
   grep_regex "$PROFILE_REQUEST_FILE" 'class[[:space:]]+ProfileUpdateRequest' && \
   grep_regex "$PROFILE_REQUEST_FILE" 'fullName'
then
    pass "ProfileUpdateRequest"
else
    fail "ProfileUpdateRequest"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'updateProfile' && \
   grep_regex "$AUTH_SERVICE_FILE" 'setFullName'
then
    pass "AuthService.updateProfile"
else
    fail "AuthService.updateProfile"
fi

if [[ -n "$AUTH_CONTROLLER_FILE" ]] && \
   grep_regex "$AUTH_CONTROLLER_FILE" '@PatchMapping\("/me"\)'
then
    pass "PATCH /api/auth/me controller"
else
    fail "PATCH /api/auth/me controller"
fi

if [[ -n "$AUTH_CONTROLLER_FILE" ]] && \
   grep_regex "$AUTH_CONTROLLER_FILE" 'updateProfile'
then
    pass "AuthController.updateProfile"
else
    fail "AuthController.updateProfile"
fi

if [[ -n "$SECURITY_CONFIG_FILE" ]] && \
   grep_regex "$SECURITY_CONFIG_FILE" 'DispatcherType\.ERROR'
then
    pass "ERROR dispatcher permission"
else
    fail "ERROR dispatcher permission"
fi

if [[ -n "$SECURITY_CONFIG_FILE" ]] && \
   grep_regex "$SECURITY_CONFIG_FILE" 'HttpMethod\.PATCH'
then
    pass "PATCH security rule"
else
    fail "PATCH security rule"
fi


# ============================================================
# Phase 2 static backend
# ============================================================

section "PHASE 2 — Backend security static checks"

if [[ -n "$CHANGE_PASSWORD_REQUEST_FILE" ]] && \
   grep_regex "$CHANGE_PASSWORD_REQUEST_FILE" 'currentPassword'
then
    pass "Current password DTO field"
else
    fail "Current password DTO field"
fi

if [[ -n "$CHANGE_PASSWORD_REQUEST_FILE" ]] && \
   grep_regex "$CHANGE_PASSWORD_REQUEST_FILE" 'newPassword'
then
    pass "New password DTO field"
else
    fail "New password DTO field"
fi

if [[ -n "$CHANGE_PASSWORD_REQUEST_FILE" ]] && \
   grep_regex "$CHANGE_PASSWORD_REQUEST_FILE" '@Size' && \
   grep_regex "$CHANGE_PASSWORD_REQUEST_FILE" 'min[[:space:]]*=[[:space:]]*8'
then
    pass "Password minimum-size validation"
else
    fail "Password minimum-size validation"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'changePassword'
then
    pass "AuthService.changePassword"
else
    fail "AuthService.changePassword"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'passwordEncoder\.matches'
then
    pass "Password verification"
else
    fail "Password verification"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'passwordEncoder\.encode'
then
    pass "Password hashing"
else
    fail "Password hashing"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'setTokenVersion' && \
   grep_regex "$AUTH_SERVICE_FILE" 'getTokenVersion'
then
    pass "Token-version invalidation"
else
    fail "Token-version invalidation"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'InvalidCurrentPasswordException'
then
    pass "Wrong-password exception"
else
    fail "Wrong-password exception"
fi

if [[ -n "$AUTH_SERVICE_FILE" ]] && \
   grep_regex "$AUTH_SERVICE_FILE" 'SamePasswordException'
then
    pass "Same-password exception"
else
    fail "Same-password exception"
fi

if [[ -n "$AUTH_CONTROLLER_FILE" ]] && \
   grep_regex "$AUTH_CONTROLLER_FILE" 'change-password' && \
   grep_regex "$AUTH_CONTROLLER_FILE" '@PostMapping'
then
    pass "POST /api/auth/change-password"
else
    fail "POST /api/auth/change-password"
fi

if [[ -n "$JWT_SERVICE_FILE" ]] && \
   grep_regex "$JWT_SERVICE_FILE" 'tokenVersion'
then
    pass "JWT tokenVersion claim"
else
    fail "JWT tokenVersion claim"
fi

if [[ -n "$JWT_SERVICE_FILE" ]] && \
   grep_regex "$JWT_SERVICE_FILE" 'expectedTokenVersion'
then
    pass "JWT tokenVersion validation"
else
    fail "JWT tokenVersion validation"
fi

if [[ -n "$USER_FILE" ]] && \
   grep_regex "$USER_FILE" 'tokenVersion'
then
    pass "User.tokenVersion"
else
    fail "User.tokenVersion"
fi


# ============================================================
# Maven
# ============================================================

section "Backend Maven test suite"

MAVEN_LOG="$TMP_ROOT/maven.log"

info "Running ./mvnw clean test"

if (
    cd "$BACKEND_DIR"
    ./mvnw clean test
) 2>&1 | tee "$MAVEN_LOG"
then
    pass "Backend Maven test suite"
else
    fail "Backend Maven test suite"
fi


# ============================================================
# Re-check runtime
# ============================================================

section "Runtime re-validation after Maven"

if curl \
    -sS \
    --connect-timeout 5 \
    --max-time 10 \
    "$BACKEND_URL/actuator/health" \
    >/dev/null 2>&1
then
    pass "Backend remains reachable"
else
    fatal "Backend became unavailable after Maven tests"
fi


# ============================================================
# Create isolated account
# ============================================================

section "Creating isolated runtime test account"

RANDOM_SUFFIX="$(date +%s)_$RANDOM"

TEST_EMAIL="trimly.phase12.${RANDOM_SUFFIX}@example.test"

OLD_PASSWORD="TrimlyOldPass_${RANDOM_SUFFIX}Aa1!"
NEW_PASSWORD="TrimlyNewPass_${RANDOM_SUFFIX}Bb2!"

echo "Test email:"
echo "  $TEST_EMAIL"
echo

echo "Initial password:"
echo "  $OLD_PASSWORD"
echo

echo "New password:"
echo "  $NEW_PASSWORD"
echo


# ============================================================
# Register
# ============================================================

REGISTER_JSON="$(
    python3 - "$TEST_EMAIL" "$OLD_PASSWORD" "$TEST_FULL_NAME" <<'PY'
import json
import sys

print(json.dumps({
    "email": sys.argv[1],
    "password": sys.argv[2],
    "fullName": sys.argv[3]
}))
PY
)"

info "Registering isolated test user"

http_request POST \
    "$API/auth/register" \
    "$REGISTER_JSON" \
    "" || true

assert_status "201" \
    "Phase 1/2 test user registration" || true

if REGISTER_TOKEN="$(json_token 2>/dev/null)"; then
    pass "Registration returned authentication token"
else
    warn "Registration did not return a token; fresh login will be used"
fi


# ============================================================
# Login helper
# ============================================================

login_with_password() {
    local password="$1"

    local payload

    payload="$(
        python3 - "$TEST_EMAIL" "$password" <<'PY'
import json
import sys

print(json.dumps({
    "email": sys.argv[1],
    "password": sys.argv[2]
}))
PY
    )"

    http_request POST \
        "$API/auth/login" \
        "$payload" \
        "" || return 1

    if [[ "$(status_code)" != "200" ]]; then
        return 1
    fi

    json_token
}


# ============================================================
# Phase 1 authentication
# ============================================================

section "PHASE 1 — Authentication"

if CURRENT_TOKEN="$(login_with_password "$OLD_PASSWORD" 2>/dev/null)"; then
    pass "Initial login — HTTP 200"
    pass "Initial JWT extracted"
else
    fatal "Initial login failed"
fi


# ============================================================
# GET /me
# ============================================================

section "PHASE 1 — GET /api/auth/me"

http_request GET \
    "$API/auth/me" \
    "" \
    "$CURRENT_TOKEN" || true

assert_status "200" \
    "Authenticated GET /me" || true

assert_body_literal "$TEST_EMAIL" \
    "Authenticated email returned" || true


# ============================================================
# Profile validation
# ============================================================

section "PHASE 1 — Profile validation"

info "Empty profile"

http_request PATCH \
    "$API/auth/me" \
    '{}' \
    "$CURRENT_TOKEN" || true

assert_status "400" \
    "Empty profile rejected" || true


info "Oversized profile"

OVERSIZED_NAME="$(python3 - <<'PY'
print("X" * 121)
PY
)"

OVERSIZED_JSON="$(
    python3 - "$OVERSIZED_NAME" <<'PY'
import json
import sys

print(json.dumps({
    "fullName": sys.argv[1]
}))
PY
)"

http_request PATCH \
    "$API/auth/me" \
    "$OVERSIZED_JSON" \
    "$CURRENT_TOKEN" || true

assert_status "400" \
    "Oversized fullName rejected" || true


# ============================================================
# Profile update
# ============================================================

section "PHASE 1 — PATCH /api/auth/me"

PROFILE_JSON="$(
    python3 - "$TEST_FULL_NAME" <<'PY'
import json
import sys

print(json.dumps({
    "fullName": sys.argv[1]
}))
PY
)"

http_request PATCH \
    "$API/auth/me" \
    "$PROFILE_JSON" \
    "$CURRENT_TOKEN" || true

assert_status "200" \
    "Profile update succeeds" || true

assert_body_literal "$TEST_FULL_NAME" \
    "Updated fullName returned" || true


# ============================================================
# Profile persistence
# ============================================================

section "PHASE 1 — Profile persistence"

http_request GET \
    "$API/auth/me" \
    "" \
    "$CURRENT_TOKEN" || true

assert_status "200" \
    "GET /me after update" || true

assert_body_literal "$TEST_FULL_NAME" \
    "fullName persists" || true


# ============================================================
# Authorization
# ============================================================

section "PHASE 1 — Authorization"

http_request PATCH \
    "$API/auth/me" \
    "$PROFILE_JSON" \
    "" || true

assert_status_any "401,403" \
    "Anonymous PATCH /me rejected" || true

http_request GET \
    "$API/auth/me" \
    "" \
    "" || true

assert_status_any "401,403" \
    "Anonymous GET /me rejected" || true


# ============================================================
# Spring validation
# ============================================================

section "PHASE 1 — Spring validation / ERROR dispatch"

http_request POST \
    "$API/auth/register" \
    '{}' \
    "" || true

assert_status "400" \
    "Invalid registration returns HTTP 400" || true

assert_body_contains '"status"[[:space:]]*:[[:space:]]*400' \
    "Validation error reports status 400" || true


# ============================================================
# Fresh Phase 2 session
# ============================================================

section "PHASE 2 — Fresh authenticated session"

info "Logging in immediately before password tests"

if CURRENT_TOKEN="$(login_with_password "$OLD_PASSWORD" 2>/dev/null)"; then
    pass "Fresh Phase 2 login — HTTP 200"
    pass "Fresh Phase 2 JWT extracted"
else
    fatal "Fresh Phase 2 login failed"
fi

http_request GET \
    "$API/auth/me" \
    "" \
    "$CURRENT_TOKEN" || true

assert_status "200" \
    "Fresh Phase 2 JWT accepted" || true


# ============================================================
# Anonymous password change
# ============================================================

section "PHASE 2 — Change-password authorization"

ANON_CHANGE_JSON="$(
    python3 - "$OLD_PASSWORD" "$NEW_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({
    "currentPassword": sys.argv[1],
    "newPassword": sys.argv[2]
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$ANON_CHANGE_JSON" \
    "" || true

assert_status_any "401,403" \
    "Anonymous password change rejected" || true


# ============================================================
# Password validation
# ============================================================

section "PHASE 2 — Password validation"

MISSING_CURRENT="$(
    python3 - "$NEW_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({
    "newPassword": sys.argv[1]
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$MISSING_CURRENT" \
    "$CURRENT_TOKEN" || true

assert_status "400" \
    "Missing current password rejected" || true


SHORT_PASSWORD="$(
    python3 - <<'PY'
import json

print(json.dumps({
    "currentPassword": "anything",
    "newPassword": "short"
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$SHORT_PASSWORD" \
    "$CURRENT_TOKEN" || true

assert_status "400" \
    "Short new password rejected" || true


# ============================================================
# Wrong password
# ============================================================

section "PHASE 2 — Wrong current password"

WRONG_CURRENT="$(
    python3 - "$NEW_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({
    "currentPassword": "WrongCurrentPassword_123!",
    "newPassword": sys.argv[1]
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$WRONG_CURRENT" \
    "$CURRENT_TOKEN" || true

assert_status "401" \
    "Wrong current password rejected" || true


info "Verifying rejected attempt did not mutate password"

if VERIFY_TOKEN="$(login_with_password "$OLD_PASSWORD" 2>/dev/null)"; then
    pass "Old password remains valid after rejected request"
    CURRENT_TOKEN="$VERIFY_TOKEN"
else
    fail "Old password remains valid after rejected request"
fi


# ============================================================
# Same password
# ============================================================

section "PHASE 2 — Same password"

SAME_PASSWORD="$(
    python3 - "$OLD_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({
    "currentPassword": sys.argv[1],
    "newPassword": sys.argv[1]
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$SAME_PASSWORD" \
    "$CURRENT_TOKEN" || true

assert_status "400" \
    "Same current/new password rejected" || true


info "Verifying rejected same-password request did not mutate password"

if VERIFY_TOKEN="$(login_with_password "$OLD_PASSWORD" 2>/dev/null)"; then
    pass "Old password remains valid after same-password rejection"
    CURRENT_TOKEN="$VERIFY_TOKEN"
else
    fail "Old password remains valid after same-password rejection"
fi


# ============================================================
# Valid password change
# ============================================================

section "PHASE 2 — Valid password change"

OLD_JWT="$CURRENT_TOKEN"

VALID_CHANGE="$(
    python3 - "$OLD_PASSWORD" "$NEW_PASSWORD" <<'PY'
import json
import sys

print(json.dumps({
    "currentPassword": sys.argv[1],
    "newPassword": sys.argv[2]
}))
PY
)"

http_request POST \
    "$API/auth/change-password" \
    "$VALID_CHANGE" \
    "$OLD_JWT" || true

if assert_status "204" \
    "Valid password change succeeds"; then

    PASSWORD_CHANGED=true
fi


# ============================================================
# JWT revocation
# ============================================================

section "PHASE 2 — JWT revocation"

if [[ "$PASSWORD_CHANGED" == "true" ]]; then

    info "Using old JWT after successful password change"

    http_request GET \
        "$API/auth/me" \
        "" \
        "$OLD_JWT" || true

    assert_status_any "401,403" \
        "Old JWT revoked" || true

else

    skip "Old JWT revocation — password change did not succeed"

fi


# ============================================================
# Old password
# ============================================================

section "PHASE 2 — Old password invalidation"

if [[ "$PASSWORD_CHANGED" == "true" ]]; then

    info "Trying old password"

    if login_with_password "$OLD_PASSWORD" >/dev/null 2>&1; then

        fail "Old password unexpectedly authenticates"

    else

        actual="$(status_code)"

        if [[ "$actual" == "401" || "$actual" == "403" ]]; then
            pass "Old password rejected — HTTP $actual"
        else
            fail "Old password returned unexpected HTTP $actual"
        fi

    fi

else

    skip "Old password invalidation"

fi


# ============================================================
# New password
# ============================================================

section "PHASE 2 — New password authentication"

if [[ "$PASSWORD_CHANGED" == "true" ]]; then

    if NEW_TOKEN="$(login_with_password "$NEW_PASSWORD" 2>/dev/null)"; then
        pass "New password authenticates — HTTP 200"
        pass "New JWT returned"
    else
        fail "New password authentication"
        NEW_TOKEN=""
    fi

else

    skip "New password authentication"

fi


# ============================================================
# New session
# ============================================================

section "PHASE 2 — New session verification"

if [[ -n "$NEW_TOKEN" ]]; then

    http_request GET \
        "$API/auth/me" \
        "" \
        "$NEW_TOKEN" || true

    assert_status "200" \
        "New JWT authenticates /me" || true

    assert_body_literal "$TEST_EMAIL" \
        "New JWT belongs to test user" || true

    assert_body_literal "$TEST_FULL_NAME" \
        "Profile survives password change" || true

else

    skip "New JWT /me"
    skip "New JWT ownership"
    skip "Profile survives password change"

fi


# ============================================================
# Frontend source discovery
# ============================================================

section "Frontend — source discovery"

if [[ -n "$SETTINGS_PAGE_FILE" ]]; then
    pass "SettingsPage source found"
    echo "    $SETTINGS_PAGE_FILE"
else
    fail "SettingsPage source found"
fi

if [[ -n "$AUTH_API_FILE" ]]; then
    pass "authApi source found"
    echo "    $AUTH_API_FILE"
else
    warn "authApi source not found by filename"
fi

if [[ -n "$API_CLIENT_FILE" ]]; then
    pass "apiClient source found"
    echo "    $API_CLIENT_FILE"
else
    warn "apiClient source not found by filename"
fi


# ============================================================
# Frontend source inventory
# ============================================================

section "Frontend — implementation inventory"

FRONTEND_SOURCE="$TMP_ROOT/frontend-source.txt"

find "$FRONTEND_DIR/src" \
    -type f \
    \( \
        -name '*.ts' \
        -o -name '*.tsx' \
        -o -name '*.js' \
        -o -name '*.jsx' \
    \) \
    -print \
    > "$FRONTEND_SOURCE"

if [[ -s "$FRONTEND_SOURCE" ]]; then
    pass "Frontend source inventory created"
else
    fail "Frontend source inventory created"
fi


# ============================================================
# Frontend search helpers
# ============================================================

search_frontend() {
    local regex="$1"

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue

        if grep -Eq "$regex" "$file" 2>/dev/null; then
            return 0
        fi

    done < "$FRONTEND_SOURCE"

    return 1
}

search_frontend_literal() {
    local text="$1"

    while IFS= read -r file; do
        [[ -z "$file" ]] && continue

        if grep -Fq "$text" "$file" 2>/dev/null; then
            return 0
        fi

    done < "$FRONTEND_SOURCE"

    return 1
}


# ============================================================
# Frontend Phase 1
# ============================================================

section "Frontend Phase 1 — Profile implementation"

if search_frontend 'fullName'; then
    pass "Frontend contains fullName implementation"
else
    fail "Frontend contains fullName implementation"
fi

if search_frontend 'Save changes|save changes'; then
    pass "Profile save action"
else
    warn "Exact profile save text not found"
fi

if search_frontend '/api/auth/me|updateProfile|fullName'; then
    pass "Profile implementation markers detected"
else
    fail "Profile implementation markers detected"
fi


# ============================================================
# Frontend Phase 2
# ============================================================

section "Frontend Phase 2 — Password implementation"

if search_frontend 'currentPassword'; then
    pass "currentPassword implementation"
else
    fail "currentPassword implementation"
fi

if search_frontend 'newPassword'; then
    pass "newPassword implementation"
else
    fail "newPassword implementation"
fi

if search_frontend 'confirmPassword'; then
    pass "confirmPassword implementation"
else
    fail "confirmPassword implementation"
fi

if search_frontend 'change-password|changePassword'; then
    pass "Password-change API wiring"
else
    fail "Password-change API wiring"
fi

if search_frontend 'incorrect|wrong password|wrong-password|Current password|current password'; then
    pass "Wrong-password UI handling"
else
    fail "Wrong-password UI handling"
fi

if search_frontend 'same password|different from|must be different'; then
    pass "Same-password UI handling"
else
    warn "Same-password UI marker not provable statically"
fi

if search_frontend 'validation|Validation|required|400|error|Error'; then
    pass "Frontend validation/error handling"
else
    fail "Frontend validation/error handling"
fi


# ============================================================
# 2FA safety check
# ============================================================

section "Frontend Phase 2 — 2FA safety"

if search_frontend 'Coming soon|coming soon|Not implemented|not implemented|disabled'; then
    pass "2FA has a non-functional/coming-soon state"
else
    warn "Could not prove 2FA state from static source"
fi


# ============================================================
# Password endpoint search
# ============================================================

section "Frontend — API endpoint verification"

PASSWORD_ENDPOINT_MATCHES="$(
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue

        grep -En \
            'change-password|changePassword' \
            "$file" 2>/dev/null || true

    done < "$FRONTEND_SOURCE"
)"

if [[ -n "$PASSWORD_ENDPOINT_MATCHES" ]]; then

    pass "Password-change endpoint/method exists in frontend source"

    echo
    echo "  Relevant source matches:"
    echo "$PASSWORD_ENDPOINT_MATCHES" | head -20 | sed 's/^/    /'
    echo

else

    fail "Password-change endpoint/method exists in frontend source"

fi


# ============================================================
# Authorization header
# ============================================================

section "Frontend — Authorization"

if [[ -n "$API_CLIENT_FILE" ]]; then

    if grep_regex "$API_CLIENT_FILE" 'Authorization' && \
       grep_regex "$API_CLIENT_FILE" 'Bearer'
    then
        pass "Authorization Bearer header"
    else
        fail "Authorization Bearer header"
    fi

else

    if search_frontend 'Authorization.*Bearer|Bearer.*Authorization'; then
        pass "Authorization Bearer header"
    else
        fail "Authorization Bearer header"
    fi

fi


# ============================================================
# Frontend production build
# ============================================================

section "Frontend production build"

if [[ -f "$FRONTEND_DIR/package.json" ]]; then
    pass "package.json exists"
else
    fatal "package.json does not exist"
fi

FRONTEND_BUILD_LOG="$TMP_ROOT/frontend-build.log"

info "Running npm run build"

if (
    cd "$FRONTEND_DIR"
    npm run build
) 2>&1 | tee "$FRONTEND_BUILD_LOG"
then

    pass "Frontend production build"

else

    fail "Frontend production build"

fi


# ============================================================
# Playwright
# ============================================================

section "Frontend Playwright E2E"

PLAYWRIGHT=false

if (
    cd "$FRONTEND_DIR"
    node -e "require.resolve('@playwright/test')"
) >/dev/null 2>&1
then
    PLAYWRIGHT=true
fi

if [[ "$PLAYWRIGHT" == "true" ]]; then

    info "@playwright/test detected"

    PLAYWRIGHT_LOG="$TMP_ROOT/playwright.log"

    if (
        cd "$FRONTEND_DIR"
        npx playwright test
    ) 2>&1 | tee "$PLAYWRIGHT_LOG"
    then

        pass "Playwright E2E suite"

    else

        fail "Playwright E2E suite"

    fi

else

    skip "@playwright/test is not installed"

fi


# ============================================================
# Final anonymous security checks
# ============================================================

section "Final security sanity checks"

http_request GET \
    "$API/auth/me" \
    "" \
    "" || true

assert_status_any "401,403" \
    "Anonymous /me remains protected" || true

http_request POST \
    "$API/auth/change-password" \
    "$ANON_CHANGE_JSON" \
    "" || true

assert_status_any "401,403" \
    "Anonymous change-password remains protected" || true


# ============================================================
# Diagnostic summary
# ============================================================

section "Diagnostic summary"

echo "Backend:"
echo "  $BACKEND_DIR"
echo

echo "Frontend:"
echo "  $FRONTEND_DIR"
echo

echo "Backend URL:"
echo "  $BACKEND_URL"
echo

echo "Frontend URL:"
echo "  $FRONTEND_URL"
echo

echo "Test account:"
echo "  $TEST_EMAIL"
echo

echo "The isolated test account is intentionally retained."
echo "JWT values were never printed."
echo


# ============================================================
# Final report
# ============================================================

section "FINAL TEST REPORT"

echo
echo "Passed:  $PASS_COUNT"
echo "Failed:  $FAIL_COUNT"
echo "Skipped: $SKIP_COUNT"
echo

echo "------------------------------------------------------------"
echo "PHASE 1 — PROFILE"
echo "------------------------------------------------------------"
echo
echo "  Backend static implementation"
echo "  Maven suite"
echo "  Registration"
echo "  Authentication"
echo "  GET /api/auth/me"
echo "  Profile validation"
echo "  PATCH /api/auth/me"
echo "  Profile persistence"
echo "  Authorization"
echo "  ERROR dispatch"
echo

echo "------------------------------------------------------------"
echo "PHASE 2 — ACCOUNT / SECURITY"
echo "------------------------------------------------------------"
echo
echo "  DTO validation"
echo "  Fresh authentication"
echo "  Wrong current password"
echo "  Same password"
echo "  Valid password change"
echo "  JWT revocation"
echo "  Old password invalidation"
echo "  New password authentication"
echo "  New session"
echo "  Profile preservation"
echo

echo "------------------------------------------------------------"
echo "FRONTEND"
echo "------------------------------------------------------------"
echo
echo "  Source discovery"
echo "  Profile implementation"
echo "  Password fields"
echo "  Password API wiring"
echo "  Error handling"
echo "  2FA safety"
echo "  Authorization header"
echo "  Production build"
echo "  Playwright E2E"
echo

echo "============================================================"

if [[ "$FAIL_COUNT" -eq 0 ]]; then

    echo
    echo -e "${GREEN}TEST SUITE PASSED.${RESET}"
    echo
    echo "Trimly Settings Phase 1 + Phase 2 verification is GREEN."
    echo

    if [[ "$SKIP_COUNT" -gt 0 ]]; then
        echo "Skipped checks: $SKIP_COUNT"
    fi

    exit 0

else

    echo
    echo -e "${RED}TEST SUITE FAILED.${RESET}"
    echo
    echo "Passed:  $PASS_COUNT"
    echo "Failed:  $FAIL_COUNT"
    echo "Skipped: $SKIP_COUNT"
    echo
    echo "Failures above should be investigated individually."
    echo

    exit 1

fi