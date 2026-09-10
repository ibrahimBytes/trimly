#!/usr/bin/env bash
set -euo pipefail

FRONTEND="$HOME/trimly"
BACKEND="$HOME/url-shortener"

AUTH_API="$FRONTEND/src/auth/authApi.ts"
AUTH_CONTEXT="$FRONTEND/src/auth/AuthContext.tsx"
SETTINGS="$FRONTEND/src/pages/SettingsPage.tsx"
E2E_DIR="$FRONTEND/tests/e2e"
E2E="$E2E_DIR/profile.spec.ts"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$FRONTEND/.trimly-backup/$TIMESTAMP"

echo
echo "============================================================"
echo "TRIMLY — REAL PROFILE NAME PERSISTENCE v2"
echo "============================================================"
echo
echo "Frontend:"
echo "  $FRONTEND"
echo
echo "Backend:"
echo "  $BACKEND"
echo
echo "Backup:"
echo "  $BACKUP"
echo

# ============================================================
# 1. Preconditions
# ============================================================

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

[[ -d "$FRONTEND" ]] || {
  echo "✗ Frontend directory not found."
  exit 1
}

[[ -f "$AUTH_API" ]] || {
  echo "✗ authApi.ts not found."
  exit 1
}

[[ -f "$AUTH_CONTEXT" ]] || {
  echo "✗ AuthContext.tsx not found."
  exit 1
}

[[ -f "$SETTINGS" ]] || {
  echo "✗ SettingsPage.tsx not found."
  exit 1
}

echo "✓ Required frontend files exist."

# ============================================================
# 2. Verify backend
# ============================================================

echo
echo "============================================================"
echo "2. Backend verification"
echo "============================================================"

if grep -Rqs --include='*.java' "updateProfile" \
    "$BACKEND/src/main/java"; then
  echo "✓ Backend updateProfile() detected."
else
  echo "✗ Backend updateProfile() not found."
  exit 1
fi

if grep -Rqs --include='*.java' "/api/auth/me" \
    "$BACKEND/src/main/java"; then
  echo "✓ /api/auth/me detected."
else
  echo "✗ /api/auth/me not found."
  exit 1
fi

if grep -Rqs --include='*.java' "ProfileUpdateRequest" \
    "$BACKEND/src/main/java"; then
  echo "✓ ProfileUpdateRequest detected."
else
  echo "✗ ProfileUpdateRequest not found."
  exit 1
fi

# ============================================================
# 3. Backup
# ============================================================

echo
echo "============================================================"
echo "3. Creating backup"
echo "============================================================"

mkdir -p "$BACKUP"

cp "$AUTH_API" "$BACKUP/authApi.ts"
cp "$AUTH_CONTEXT" "$BACKUP/AuthContext.tsx"
cp "$SETTINGS" "$BACKUP/SettingsPage.tsx"

echo "✓ authApi.ts backed up"
echo "✓ AuthContext.tsx backed up"
echo "✓ SettingsPage.tsx backed up"

# ============================================================
# 4. Add/update AuthUser model
# ============================================================

echo
echo "============================================================"
echo "4. Updating AuthUser model"
echo "============================================================"

python3 - "$AUTH_API" "$AUTH_CONTEXT" <<'PY'
from pathlib import Path
import sys
import re

auth_api = Path(sys.argv[1])
auth_context = Path(sys.argv[2])

# ------------------------------------------------------------
# authApi.ts
# ------------------------------------------------------------

text = auth_api.read_text()

match = re.search(
    r"export interface AuthUser\s*\{.*?\n\}",
    text,
    re.DOTALL,
)

if not match:
    raise SystemExit(
        "Could not locate AuthUser interface in authApi.ts"
    )

block = match.group(0)

if "fullName:" not in block:
    block = block.replace(
        "  email: string;",
        "  email: string;\n  fullName: string | null;",
        1,
    )

text = text[:match.start()] + block + text[match.end():]

auth_api.write_text(text)

print("✓ authApi.ts AuthUser contains fullName.")

# ------------------------------------------------------------
# AuthContext.tsx
# ------------------------------------------------------------

text = auth_context.read_text()

match = re.search(
    r"interface AuthUser\s*\{.*?\n\}",
    text,
    re.DOTALL,
)

if not match:
    raise SystemExit(
        "Could not locate AuthUser interface in AuthContext.tsx"
    )

block = match.group(0)

if "fullName:" not in block:
    block = block.replace(
        "  email: string;",
        "  email: string;\n  fullName: string | null;",
        1,
    )

text = text[:match.start()] + block + text[match.end():]

auth_context.write_text(text)

print("✓ AuthContext.tsx AuthUser contains fullName.")
PY

# ============================================================
# 5. Add updateProfile API
# ============================================================

echo
echo "============================================================"
echo "5. Adding updateProfile() API"
echo "============================================================"

python3 - "$AUTH_API" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

if "export async function updateProfile(" in text:
    print("✓ updateProfile() already exists.")
    raise SystemExit(0)

marker = "\nexport async function logout(): Promise<void> {"

if marker not in text:
    raise SystemExit(
        "Could not locate logout() in authApi.ts"
    )

addition = r'''
export async function updateProfile(
  fullName: string,
): Promise<AuthUser> {
  return apiFetch<AuthUser>(
    '/api/auth/me',
    {
      method: 'PATCH',
      body: JSON.stringify({
        fullName,
      }),
    },
  );
}
'''

text = text.replace(
    marker,
    addition + marker,
    1,
)

path.write_text(text)

print("✓ Added updateProfile()")
PY

# ============================================================
# 6. Update AuthContext
# ============================================================

echo
echo "============================================================"
echo "6. Wiring AuthContext profile persistence"
echo "============================================================"

python3 - "$AUTH_CONTEXT" <<'PY'
from pathlib import Path
import sys
import re

path = Path(sys.argv[1])
text = path.read_text()

# ------------------------------------------------------------
# Import updateProfile
# ------------------------------------------------------------

if "updateProfile," not in text:

    old = """import {
  getCurrentUser,
  login,
  logout,
  register,
} from '@/auth/authApi';"""

    new = """import {
  getCurrentUser,
  login,
  logout,
  register,
  updateProfile,
} from '@/auth/authApi';"""

    if old in text:
        text = text.replace(old, new, 1)
    else:
        raise SystemExit(
            "Could not locate authApi import block."
        )

# ------------------------------------------------------------
# Context interface
# ------------------------------------------------------------

if "updateProfile: (" not in text:

    marker = "  signOut: () => Promise<void>;"

    if marker not in text:
        raise SystemExit(
            "Could not locate signOut in AuthContextValue."
        )

    replacement = """  signOut: () => Promise<void>;

  updateProfile: (
    fullName: string,
  ) => Promise<void>;"""

    text = text.replace(
        marker,
        replacement,
        1,
    )

# ------------------------------------------------------------
# Add updater
# ------------------------------------------------------------

if "const updateProfileUser = useCallback(" not in text:

    marker = "  const signOut = useCallback(async () => {"

    if marker not in text:
        raise SystemExit(
            "Could not locate signOut callback."
        )

    addition = r'''  const updateProfileUser = useCallback(
    async (fullName: string) => {
      const updatedUser = await updateProfile(fullName);

      setUser(updatedUser);

      setStoredAuth((current) => {
        if (!current) {
          return current;
        }

        return {
          ...current,
          userId: updatedUser.id,
          email: updatedUser.email,
          fullName: updatedUser.fullName,
        };
      });
    },
    [],
  );

'''

    text = text.replace(
        marker,
        addition + marker,
        1,
    )

# ------------------------------------------------------------
# Provider value
# ------------------------------------------------------------

if "updateProfile: updateProfileUser" not in text:

    marker = "      signOut,"

    if marker not in text:
        raise SystemExit(
            "Could not locate signOut in provider value."
        )

    text = text.replace(
        marker,
        """      signOut,
      updateProfile: updateProfileUser,""",
        1,
    )

path.write_text(text)

print("✓ AuthContext profile updater wired.")
PY

# ============================================================
# 7. Replace mock saveProfile
# ============================================================

echo
echo "============================================================"
echo "7. Replacing mock saveProfile()"
echo "============================================================"

python3 - "$SETTINGS" <<'PY'
from pathlib import Path
import sys
import re

path = Path(sys.argv[1])
text = path.read_text()

# ------------------------------------------------------------
# Find useAuth destructuring
# ------------------------------------------------------------

match = re.search(
    r"const\s*\{([^}]*)\}\s*=\s*useAuth\(\);",
    text,
    re.DOTALL,
)

if not match:
    raise SystemExit(
        "Could not find useAuth() destructuring in SettingsPage."
    )

fields = match.group(1)

if "updateProfile" not in fields:

    # Preserve existing fields and append updater.
    clean = fields.rstrip()

    if clean and not clean.endswith(","):
        clean += ","

    clean += "\n    updateProfile,"

    text = (
        text[:match.start(1)]
        + clean
        + text[match.end(1):]
    )

# ------------------------------------------------------------
# Replace saveProfile
# ------------------------------------------------------------

pattern = re.compile(
    r"(?P<indent>^[ \t]*)const saveProfile\s*=\s*\(\)\s*=>\s*\{.*?^\s*\};",
    re.MULTILINE | re.DOTALL,
)

match = pattern.search(text)

if not match:
    raise SystemExit(
        "Could not locate current saveProfile() implementation."
    )

indent = match.group("indent")

replacement = f'''{indent}const saveProfile = async () => {{
{indent}  const trimmedName = name.trim();

{indent}  if (!trimmedName) {{
{indent}    notify('Please enter your name.');
{indent}    return;
{indent}  }}

{indent}  try {{
{indent}    const updatedUser = await updateProfile(
{indent}      trimmedName,
{indent}    );

{indent}    setName(updatedUser.fullName ?? trimmedName);
{indent}    setEmail(updatedUser.email);

{indent}    setSaved(true);
{indent}    notify('Profile updated successfully.');

{indent}    window.setTimeout(() => {{
{indent}      setSaved(false);
{indent}    }}, 1800);
{indent}  }} catch (error) {{
{indent}    notify(
{indent}      error instanceof Error
{indent}        ? error.message
{indent}        : 'Unable to update your profile. Please try again.',
{indent}    );
{indent}  }}
{indent}}};'''

text = (
    text[:match.start()]
    + replacement
    + text[match.end():]
)

path.write_text(text)

print("✓ saveProfile() now performs real PATCH request.")
PY

# ============================================================
# 8. Verify no mock save remains
# ============================================================

echo
echo "============================================================"
echo "8. Verifying profile save implementation"
echo "============================================================"

if grep -q \
  "await updateProfile(" \
  "$SETTINGS"; then
  echo "✓ SettingsPage calls updateProfile()."
else
  echo "✗ updateProfile() call not found."
  exit 1
fi

if grep -q \
  "'/api/auth/me'" \
  "$AUTH_API"; then
  echo "✓ /api/auth/me detected."
else
  echo "✗ Profile endpoint missing."
  exit 1
fi

if grep -q \
  "method: 'PATCH'" \
  "$AUTH_API"; then
  echo "✓ PATCH method detected."
else
  echo "✗ PATCH method missing."
  exit 1
fi

echo
echo "Current saveProfile():"
grep -n -A45 -B5 \
  "const saveProfile" \
  "$SETTINGS"

# ============================================================
# 9. Build
# ============================================================

echo
echo "============================================================"
echo "9. Frontend production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Frontend production build passed."

# ============================================================
# 10. E2E test
# ============================================================

echo
echo "============================================================"
echo "10. Creating profile E2E"
echo "============================================================"

mkdir -p "$E2E_DIR"

cat > "$E2E" <<'EOF'
import { test, expect } from '@playwright/test';

const API_BASE =
  process.env.E2E_API_BASE_URL ??
  'http://localhost:8080'\;

const FRONTEND_BASE =
  process.env.E2E_BASE_URL ??
  'http://localhost:8443'\;

function uniqueEmail() {
  return `trimly-profile-${Date.now()}-${Math.random()
    .toString(36)
    .slice(2)}@example.com`;
}

test('full name persists after save and refresh', async ({
  page,
  request,
}) => {
  const email = uniqueEmail();
  const password = 'TrimlyTest123!';

  const registerResponse = await request.post(
    `${API_BASE}/api/auth/register`,
    {
      data: {
        email,
        password,
      },
    },
  );

  expect(registerResponse.status()).toBe(201);

  const auth = await registerResponse.json();

  expect(auth.token).toBeTruthy();

  await page.goto(FRONTEND_BASE);

  await page.evaluate(
    ({ auth }) => {
      localStorage.setItem(
        'trimly.auth',
        JSON.stringify({
          token: auth.token,
          tokenType: auth.tokenType ?? 'Bearer',
          userId: auth.userId,
          email: auth.email,
          fullName: auth.fullName ?? null,
        }),
      );
    },
    { auth },
  );

  await page.goto(`${FRONTEND_BASE}/settings`);

  const nameInput = page
    .locator('input[type="text"]')
    .first();

  await expect(nameInput).toBeVisible();

  const newName = 'Trimly Persistent User';

  await nameInput.fill(newName);

  await page.getByRole('button', {
    name: 'Save changes',
  }).click();

  await expect(
    page.getByText(
      'Profile updated successfully.',
    ),
  ).toBeVisible();

  // Verify backend immediately.
  const meResponse = await request.get(
    `${API_BASE}/api/auth/me`,
    {
      headers: {
        Authorization: `Bearer ${auth.token}`,
      },
    },
  );

  expect(meResponse.status()).toBe(200);

  const me = await meResponse.json();

  expect(me.fullName).toBe(newName);

  // Reload the actual browser page.
  await page.reload();

  const reloadedNameInput = page
    .locator('input[type="text"]')
    .first();

  await expect(
    reloadedNameInput,
  ).toHaveValue(newName);
});
EOF

echo "✓ Added:"
echo "  $E2E"

# ============================================================
# 11. Run E2E
# ============================================================

echo
echo "============================================================"
echo "11. Running profile E2E"
echo "============================================================"

npx playwright test \
  tests/e2e/profile.spec.ts \
  --workers=1

echo
echo "✓ Profile E2E passed."

# ============================================================
# 12. Final verification
# ============================================================

echo
echo "============================================================"
echo "12. Final verification"
echo "============================================================"

echo
echo "authApi.ts:"
grep -n -A18 -B2 \
  "export async function updateProfile" \
  "$AUTH_API"

echo
echo "AuthContext.tsx:"
grep -n -A25 -B5 \
  "updateProfileUser" \
  "$AUTH_CONTEXT"

echo
echo "SettingsPage.tsx:"
grep -n -A45 -B5 \
  "const saveProfile" \
  "$SETTINGS"

echo
echo "============================================================"
echo "IMPLEMENTATION COMPLETE"
echo "============================================================"
echo
echo "Profile persistence is now:"
echo
echo "  SettingsPage"
echo "      ↓"
echo "  name state"
echo "      ↓"
echo "  saveProfile()"
echo "      ↓"
echo "  AuthContext.updateProfile()"
echo "      ↓"
echo "  authApi.updateProfile()"
echo "      ↓"
echo "  PATCH /api/auth/me"
echo "      ↓"
echo "  Spring Boot"
echo "      ↓"
echo "  User.fullName"
echo "      ↓"
echo "  PostgreSQL"
echo "      ↓"
echo "  updated AuthUser"
echo "      ↓"
echo "  localStorage + React state"
echo "      ↓"
echo "  page refresh"
echo "      ↓"
echo "  GET /api/auth/me"
echo "      ↓"
echo "  persisted full name"
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "============================================================"
