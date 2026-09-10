# paste the complete script above here
#!/usr/bin/env bash

set -u

FRONTEND="$HOME/trimly"
SRC="$FRONTEND/src"

SETTINGS="$SRC/pages/SettingsPage.tsx"
AUTH_API="$SRC/auth/authApi.ts"
AUTH_CONTEXT="$SRC/auth/AuthContext.tsx"
API_CLIENT="$SRC/api/apiClient.ts"

echo
echo "============================================================"
echo "TRIMLY FRONTEND AUTH / SETTINGS INSPECTION"
echo "============================================================"
echo
echo "Frontend:"
echo "  $FRONTEND"
echo

# ------------------------------------------------------------
# Helpers
# ------------------------------------------------------------

section() {
    echo
    echo "============================================================"
    echo "$1"
    echo "============================================================"
}

show_file() {
    local file="$1"

    if [[ ! -f "$file" ]]; then
        echo "✗ FILE NOT FOUND: $file"
        return 1
    fi

    echo
    echo "------------------------------------------------------------"
    echo "$file"
    echo "------------------------------------------------------------"
    nl -ba "$file"
}

search_literal() {
    local label="$1"
    local pattern="$2"

    echo
    echo ">>> $label"
    if grep -RInF \
        --exclude-dir=node_modules \
        --exclude-dir=dist \
        --exclude-dir=.git \
        "$pattern" "$SRC" 2>/dev/null; then
        :
    else
        echo "    NOT FOUND"
    fi
}

search_regex() {
    local label="$1"
    local pattern="$2"

    echo
    echo ">>> $label"
    if grep -RInE \
        --exclude-dir=node_modules \
        --exclude-dir=dist \
        --exclude-dir=.git \
        "$pattern" "$SRC" 2>/dev/null; then
        :
    else
        echo "    NOT FOUND"
    fi
}

# ------------------------------------------------------------
# 1. Verify files
# ------------------------------------------------------------

section "1. Required source files"

for file in "$SETTINGS" "$AUTH_API" "$AUTH_CONTEXT" "$API_CLIENT"; do
    if [[ -f "$file" ]]; then
        echo "✓ $file"
    else
        echo "✗ MISSING $file"
    fi
done

# ------------------------------------------------------------
# 2. Full SettingsPage
# ------------------------------------------------------------

section "2. ACTUAL SettingsPage.tsx"

show_file "$SETTINGS"

# ------------------------------------------------------------
# 3. Full authApi
# ------------------------------------------------------------

section "3. ACTUAL authApi.ts"

show_file "$AUTH_API"

# ------------------------------------------------------------
# 4. Full AuthContext
# ------------------------------------------------------------

section "4. ACTUAL AuthContext.tsx"

show_file "$AUTH_CONTEXT"

# ------------------------------------------------------------
# 5. API client
# ------------------------------------------------------------

section "5. ACTUAL apiClient.ts"

show_file "$API_CLIENT"

# ------------------------------------------------------------
# 6. Password-related implementation
# ------------------------------------------------------------

section "6. Password implementation discovery"

search_literal \
    "currentPassword references" \
    "currentPassword"

search_literal \
    "newPassword references" \
    "newPassword"

search_literal \
    "confirmPassword references" \
    "confirmPassword"

search_literal \
    "change-password endpoint references" \
    "/api/auth/change-password"

search_literal \
    "changePassword references" \
    "changePassword"

search_literal \
    "password references" \
    "password"

# ------------------------------------------------------------
# 7. Auth API methods
# ------------------------------------------------------------

section "7. Auth API function/method discovery"

search_regex \
    "Auth API exports/functions" \
    '(^|[[:space:]])(export|async|function)[[:space:]]+([A-Za-z0-9_]+)'

search_regex \
    "HTTP calls from authApi" \
    '(apiClient|fetch|axios)\.(get|post|patch|put|delete)'

search_regex \
    "POST calls" \
    '\.(post|request)\('

# ------------------------------------------------------------
# 8. Settings password flow
# ------------------------------------------------------------

section "8. SettingsPage password flow"

search_regex \
    "Password state declarations" \
    '(useState|useReducer).*(password|Password)'

search_regex \
    "Password form handlers" \
    '(handle|submit|save|change).*(password|Password)'

search_regex \
    "Password API calls" \
    '(changePassword|password.*api|api.*password|authApi.*password|auth.*password)'

search_regex \
    "Password error handling" \
    '(401|400|incorrect|Incorrect|current password|Current password|same password|Same password|error|Error)'

search_regex \
    "Password UI labels" \
    '(Change password|Change Password|Current password|New password|Confirm password|Save password|Update password)'

# ------------------------------------------------------------
# 9. Imports and dependencies
# ------------------------------------------------------------

section "9. SettingsPage imports"

if [[ -f "$SETTINGS" ]]; then
    sed -n '1,/^[[:space:]]*export default/p' "$SETTINGS" \
        | grep -nE '^(.*import |.*from |.*auth|.*api)' \
        || true
fi

section "10. AuthContext imports"

if [[ -f "$AUTH_CONTEXT" ]]; then
    sed -n '1,/^[[:space:]]*export default/p' "$AUTH_CONTEXT" \
        | grep -nE '^(.*import |.*from |.*auth|.*api)' \
        || true
fi

# ------------------------------------------------------------
# 10. Dynamic/static authApi imports
# ------------------------------------------------------------

section "11. authApi import graph"

search_regex \
    "All authApi imports/usages" \
    '(import.*authApi|from.*authApi|import\(.*authApi|authApi)'

# ------------------------------------------------------------
# 11. API endpoint inventory
# ------------------------------------------------------------

section "12. Frontend API endpoint inventory"

search_regex \
    "All /api/auth endpoints" \
    '/api/auth/[A-Za-z0-9_./{}-]+'

search_regex \
    "All HTTP methods around auth endpoints" \
    '(GET|POST|PATCH|PUT|DELETE|\.get\(|\.post\(|\.patch\(|\.put\(|\.delete\()'

# ------------------------------------------------------------
# 12. Exact password endpoint context
# ------------------------------------------------------------

section "13. Exact /api/auth/change-password context"

if grep -RInF \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=.git \
    "/api/auth/change-password" \
    "$SRC" 2>/dev/null; then
    :
else
    echo "NOT FOUND"
fi

# ------------------------------------------------------------
# 13. Password UI context
# ------------------------------------------------------------

section "14. Password-related source context"

grep -RInE \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=.git \
    -C 5 \
    'currentPassword|newPassword|confirmPassword|changePassword|change-password' \
    "$SRC" 2>/dev/null \
    || echo "No password-related implementation markers found."

# ------------------------------------------------------------
# 14. AuthContext dynamic import issue
# ------------------------------------------------------------

section "15. AuthContext static vs dynamic imports"

echo
echo "Static imports:"
grep -nE '^[[:space:]]*import .*authApi|^[[:space:]]*import .*from.*authApi' \
    "$AUTH_CONTEXT" 2>/dev/null \
    || echo "  None"

echo
echo "Dynamic imports:"
grep -nE 'import[[:space:]]*\([^)]*authApi|import[[:space:]]*\(' \
    "$AUTH_CONTEXT" 2>/dev/null \
    || echo "  None"

# ------------------------------------------------------------
# 15. Relevant package/test information
# ------------------------------------------------------------

section "16. Frontend package scripts"

if [[ -f "$FRONTEND/package.json" ]]; then
    node -e '
const p = require(process.argv[1]);
console.log(JSON.stringify(p.scripts || {}, null, 2));
' "$FRONTEND/package.json"
else
    echo "package.json not found"
fi

# ------------------------------------------------------------
# 16. Existing password E2E coverage
# ------------------------------------------------------------

section "17. Existing Playwright password coverage"

if [[ -d "$FRONTEND/tests" ]]; then
    grep -RInE \
        --exclude-dir=node_modules \
        'password|change-password|currentPassword|newPassword|confirmPassword' \
        "$FRONTEND/tests" 2>/dev/null \
        || echo "NO PASSWORD-CHANGE E2E TEST FOUND"
else
    echo "tests directory not found"
fi

# ------------------------------------------------------------
# 17. Final structural diagnosis
# ------------------------------------------------------------

section "18. Structural diagnosis"

echo
echo "SettingsPage:"
if [[ -f "$SETTINGS" ]]; then
    echo "  ✓ exists"
else
    echo "  ✗ missing"
fi

echo
echo "authApi:"
if [[ -f "$AUTH_API" ]]; then
    echo "  ✓ exists"
else
    echo "  ✗ missing"
fi

echo
echo "AuthContext:"
if [[ -f "$AUTH_CONTEXT" ]]; then
    echo "  ✓ exists"
else
    echo "  ✗ missing"
fi

echo
echo "Password endpoint:"
if grep -RInF \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=.git \
    "/api/auth/change-password" \
    "$SRC" >/dev/null 2>&1; then
    echo "  ✓ frontend source contains /api/auth/change-password"
else
    echo "  ✗ frontend source does NOT contain /api/auth/change-password"
fi

echo
echo "Password API operation:"
if grep -RInE \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=.git \
    'changePassword|change-password|password.*post|post.*password' \
    "$SRC" >/dev/null 2>&1; then
    echo "  ✓ password API operation appears in source"
else
    echo "  ✗ password API operation not detected"
fi

echo
echo "Password fields:"
for field in currentPassword newPassword confirmPassword; do
    if grep -RInF \
        --exclude-dir=node_modules \
        --exclude-dir=dist \
        --exclude-dir=.git \
        "$field" \
        "$SRC" >/dev/null 2>&1; then
        echo "  ✓ $field found"
    else
        echo "  ✗ $field not found"
    fi
done

echo
echo "============================================================"
echo "INSPECTION COMPLETE"
echo "============================================================"
echo
echo "No files were modified."
echo