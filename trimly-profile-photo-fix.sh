#!/usr/bin/env bash
set -euo pipefail

echo "============================================================"
echo "Trimly — Profile Photo Corrective Fix"
echo "============================================================"
echo

FRONTEND="$HOME/trimly"
BACKEND="$HOME/url-shortener"

if [[ ! -d "$FRONTEND" ]]; then
  echo "ERROR: Frontend project not found: $FRONTEND"
  exit 1
fi

if [[ ! -d "$BACKEND" ]]; then
  echo "ERROR: Backend project not found: $BACKEND"
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
backup="$BACKEND/.trimly-backup/profile-photo-fix-$timestamp"
mkdir -p "$backup"

backup_file() {
  local file="$1"
  local rel
  if [[ "$file" == "$FRONTEND/"* ]]; then
    rel="${file#$FRONTEND/}"
  elif [[ "$file" == "$BACKEND/"* ]]; then
    rel="${file#$BACKEND/}"
  else
    rel="$(basename "$file")"
  fi

  mkdir -p "$backup/$(dirname "$rel")"
  cp -p "$file" "$backup/$rel"
}

echo "[1/5] Backing up corrective-fix targets..."

for file in \
  "$FRONTEND/src/api/apiClient.ts" \
  "$FRONTEND/src/components/layout/AppHeader.tsx"
do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: Required frontend file not found: $file"
    exit 1
  fi
  backup_file "$file"
done

echo "Backup: $backup"
echo

echo "[2/5] Fixing FormData handling in apiClient.ts..."

python3 - "$FRONTEND/src/api/apiClient.ts" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

old = """  if (
    requestOptions.body &&
    !headers.has(
      'Content-Type',
    )
  ) {
    headers.set(
      'Content-Type',
      'application/json',
    );
  }
"""

new = """  /*
   * Let the browser set Content-Type for FormData.
   *
   * Fetch must add the multipart boundary automatically.
   * For JSON/string bodies we keep the existing default.
   */
  if (
    requestOptions.body &&
    !headers.has('Content-Type') &&
    !(requestOptions.body instanceof FormData)
  ) {
    headers.set(
      'Content-Type',
      'application/json',
    );
  }
"""

if old not in text:
    if "requestOptions.body instanceof FormData" in text:
        print("apiClient: FormData handling already fixed; no change needed.")
        raise SystemExit(0)

    print("ERROR: Expected apiFetch Content-Type block was not found.")
    raise SystemExit(1)

path.write_text(text.replace(old, new, 1))
print("apiClient: FormData handling fixed.")
PY

echo

echo "[3/5] Fixing AppHeader avatar rendering..."

python3 - "$FRONTEND/src/components/layout/AppHeader.tsx" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

old = """              {initials}
"""

new = """              {user?.profileImageUrl ? (
                <img
                  src={
                    user.profileImageUrl.startsWith('http')
                      ? user.profileImageUrl
                      : `${(
                          import.meta.env.VITE_API_BASE_URL ??
                          'http://localhost:8080'
                        )}${user.profileImageUrl}`
                  }
                  alt=""
                  className="h-full w-full object-cover"
                />
              ) : (
                initials
              )}
"""

if old not in text:
    if "user?.profileImageUrl" in text:
        print("AppHeader: profile-image rendering already present; no change needed.")
        raise SystemExit(0)

    print("ERROR: Expected avatar initials expression was not found.")
    raise SystemExit(1)

path.write_text(text.replace(old, new, 1))
print("AppHeader: persisted profile photo is now rendered with initials fallback.")
PY

echo

echo "[4/5] Verifying the resulting source..."

grep -n -A18 -B5 "instanceof FormData" \
  "$FRONTEND/src/api/apiClient.ts"

echo
grep -n -A24 -B8 "profileImageUrl" \
  "$FRONTEND/src/components/layout/AppHeader.tsx"

echo

echo "[5/5] Running validation..."

cd "$FRONTEND"

if npm run build; then
  echo
  echo "Frontend build: PASS"
else
  echo
  echo "Frontend build: FAILED"
  echo
  echo "The corrective changes have NOT been rolled back."
  echo "Backup is available at:"
  echo "$backup"
  exit 1
fi

cd "$BACKEND"

if [[ -x "./mvnw" ]]; then
  ./mvnw test
elif command -v mvn >/dev/null 2>&1; then
  mvn test
else
  echo "ERROR: Neither ./mvnw nor mvn was found."
  echo "Backup is available at:"
  echo "$backup"
  exit 1
fi

echo
echo "============================================================"
echo "PROFILE PHOTO CORRECTIVE FIX COMPLETE"
echo "============================================================"
echo
echo "Fixed:"
echo "  • FormData upload Content-Type handling"
echo "  • AppHeader persisted profile-photo rendering"
echo "  • Initials fallback when no photo exists"
echo
echo "Backup:"
echo "  $backup"
echo
echo "IMPORTANT:"
echo "  This script does not modify JWT_SECRET."
echo "  This script does not generate or replace authentication secrets."
echo
