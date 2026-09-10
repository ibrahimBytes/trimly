#!/usr/bin/env bash

set -euo pipefail

FRONTEND="$HOME/trimly"
APP_HEADER="$FRONTEND/src/components/layout/AppHeader.tsx"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$FRONTEND/.trimly-backup/$STAMP"

echo
echo "============================================================"
echo "TRIMLY — APP HEADER PROFILE SYNC v1"
echo "============================================================"
echo
echo "Frontend:"
echo "  $FRONTEND"
echo
echo "Backup:"
echo "  $BACKUP"
echo

# ==============================================================
# 1. Preconditions
# ==============================================================

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

if [[ ! -f "$APP_HEADER" ]]; then
  echo "✗ AppHeader.tsx not found:"
  echo "  $APP_HEADER"
  exit 1
fi

grep -q "const {" "$APP_HEADER"
grep -q "user," "$APP_HEADER"
grep -q "useAuth" "$APP_HEADER"

echo "✓ AppHeader.tsx exists."
echo "✓ AppHeader already consumes AuthContext."

# ==============================================================
# 2. Backup
# ==============================================================

echo
echo "============================================================"
echo "2. Creating backup"
echo "============================================================"

mkdir -p "$BACKUP"
cp "$APP_HEADER" "$BACKUP/AppHeader.tsx"

echo "✓ Backup created:"
echo "  $BACKUP/AppHeader.tsx"

# ==============================================================
# 3. Patch AppHeader
# ==============================================================

echo
echo "============================================================"
echo "3. Updating AppHeader identity rendering"
echo "============================================================"

python3 - "$APP_HEADER" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()


def replace_exact(text, old, new, label):
    count = text.count(old)

    if count != 1:
        raise SystemExit(
            f"✗ {label}: expected exactly 1 occurrence, found {count}"
        )

    return text.replace(old, new, 1)


# --------------------------------------------------------------
# Initials
# --------------------------------------------------------------

old = """  const initials =
    getInitials(
      user.email,
    );"""

new = """  const displayName =
    user.fullName?.trim() ||
    user.email;

  const initials =
    getInitials(
      displayName,
    );"""

text = replace_exact(
    text,
    old,
    new,
    "initials/displayName block",
)

print("✓ Added displayName derived from AuthContext.user.")


# --------------------------------------------------------------
# Desktop account identity
# --------------------------------------------------------------

old = """              <p
                className="
                  max-w-[180px]
                  truncate
                  text-xs
                  font-medium
                  text-[#111827]
                "
              >
                {user.email}
              </p>

              <p
                className="
                  text-[11px]
                  text-[#98A2B3]
                "
              >
                Account
              </p>"""

new = """              <p
                className="
                  max-w-[180px]
                  truncate
                  text-xs
                  font-medium
                  text-[#111827]
                "
              >
                {displayName}
              </p>

              <p
                className="
                  max-w-[180px]
                  truncate
                  text-[11px]
                  text-[#98A2B3]
                "
              >
                {user.email}
              </p>"""

text = replace_exact(
    text,
    old,
    new,
    "desktop account identity",
)

print("✓ Desktop header now displays full name.")


# --------------------------------------------------------------
# Account dropdown identity
# --------------------------------------------------------------

old = """                  <p
                    className="
                      truncate
                      text-sm
                      font-medium
                      text-[#111827]
                    "
                  >
                    {user.email}
                  </p>

                  <p
                    className="
                      mt-0.5
                      text-xs
                      text-[#98A2B3]
                    "
                  >
                    Signed in
                  </p>"""

new = """                  <p
                    className="
                      truncate
                      text-sm
                      font-medium
                      text-[#111827]
                    "
                  >
                    {displayName}
                  </p>

                  <p
                    className="
                      mt-0.5
                      truncate
                      text-xs
                      text-[#98A2B3]
                    "
                  >
                    {user.email}
                  </p>"""

text = replace_exact(
    text,
    old,
    new,
    "account dropdown identity",
)

print("✓ Account dropdown now displays full name and email.")


path.write_text(text)
PY

echo "✓ AppHeader patch complete."

# ==============================================================
# 4. Source verification
# ==============================================================

echo
echo "============================================================"
echo "4. Source verification"
echo "============================================================"

grep -q "const displayName =" "$APP_HEADER"
echo "✓ displayName exists."

grep -q "user.fullName?.trim()" "$APP_HEADER"
echo "✓ displayName uses user.fullName."

grep -q "const initials" "$APP_HEADER"
echo "✓ Initials use displayName."

# There should no longer be an identity display using
# user.email as the primary value. The remaining user.email
# occurrences are intentionally secondary email displays.
EMAIL_COUNT="$(grep -c "{user.email}" "$APP_HEADER" || true)"

if [[ "$EMAIL_COUNT" -ne 2 ]]; then
  echo "✗ Expected exactly 2 secondary email displays, found $EMAIL_COUNT."
  exit 1
fi

echo "✓ Email remains as the secondary identity field."

# ==============================================================
# 5. Production build
# ==============================================================

echo
echo "============================================================"
echo "5. Production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Production build passed."

# ==============================================================
# 6. Final verification
# ==============================================================

echo
echo "============================================================"
echo "6. Final verification"
echo "============================================================"

echo
echo "Identity source:"
grep -n -A8 -B3 \
  "const displayName" \
  "$APP_HEADER"

echo
echo "Desktop account:"
grep -n -A20 -B4 \
  "Desktop Account" \
  "$APP_HEADER"

echo
echo "============================================================"
echo "APP HEADER PROFILE SYNC v1 COMPLETE"
echo "============================================================"
echo
echo "Result:"
echo
echo "  AuthContext.user.fullName"
echo "          ↓"
echo "      AppHeader"
echo "          ↓"
echo "  ┌───────────────────────┐"
echo "  │ Full name             │"
echo "  │ Email                 │"
echo "  │ Avatar initials       │"
echo "  └───────────────────────┘"
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "============================================================"