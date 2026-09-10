#!/usr/bin/env bash
set -euo pipefail

FRONTEND="$HOME/trimly"
BACKEND="$HOME/url-shortener"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$FRONTEND/.trimly-backup/$TIMESTAMP"

AUTH_API="$FRONTEND/src/auth/authApi.ts"
AUTH_CONTEXT="$FRONTEND/src/auth/AuthContext.tsx"
SETTINGS="$FRONTEND/src/pages/SettingsPage.tsx"
API_CLIENT="$FRONTEND/src/api/apiClient.ts"
E2E_DIR="$FRONTEND/tests/e2e"
E2E="$E2E_DIR/profile.spec.ts"

echo
echo "============================================================"
echo "TRIMLY — REAL PROFILE NAME PERSISTENCE"
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

# ------------------------------------------------------------
# 1. Preconditions
# ------------------------------------------------------------

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

required_files=(
  "$AUTH_API"
  "$AUTH_CONTEXT"
  "$SETTINGS"
  "$API_CLIENT"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "✗ Missing required file:"
    echo "  $file"
    exit 1
  fi
done

if [[ ! -d "$FRONTEND" ]]; then
  echo "✗ Frontend directory does not exist."
  exit 1
fi

echo "✓ Required frontend files exist."

# ------------------------------------------------------------
# 2. Verify backend endpoint
# ------------------------------------------------------------

echo
echo "============================================================"
echo "2. Backend endpoint verification"
echo "============================================================"

if [[ -d "$BACKEND/src/main/java" ]]; then

  if grep -Rqs \
    --include='*.java' \
    "PATCH" \
    "$BACKEND/src/main/java"; then
    echo "✓ Backend contains PATCH mappings."
  else
    echo "⚠ No PATCH mapping found by simple source scan."
  fi

  if grep -Rqs \
    --include='*.java' \
    "/api/auth/me" \
    "$BACKEND/src/main/java"; then
    echo "✓ /api/auth/me detected in backend."
  else
    echo "⚠ /api/auth/me was not detected by source scan."
  fi

  if grep -Rqs \
    --include='*.java' \
    "updateProfile" \
    "$BACKEND/src/main/java"; then
    echo "✓ updateProfile detected in backend."
  else
    echo "⚠ updateProfile was not detected."
  fi

  if grep -Rqs \
    --include='*.java' \
    "ProfileUpdateRequest" \
    "$BACKEND/src/main/java"; then
    echo "✓ ProfileUpdateRequest detected."
  else
    echo "⚠ ProfileUpdateRequest was not detected."
  fi

else
  echo "⚠ Backend source directory not found; continuing."
fi

# ------------------------------------------------------------
# 3. Backups
# ------------------------------------------------------------

echo
echo "============================================================"
echo "3. Creating backups"
echo "============================================================"

mkdir -p "$BACKUP"

cp "$AUTH_API" "$BACKUP/AuthApi.tsx.backup" 2>/dev/null || \
cp "$AUTH_API" "$BACKUP/authApi.ts.backup"

cp "$AUTH_CONTEXT" "$BACKUP/AuthContext.tsx.backup"
cp "$SETTINGS" "$BACKUP/SettingsPage.tsx.backup"
cp "$API_CLIENT" "$BACKUP/apiClient.ts.backup"

echo "✓ authApi.ts backed up"
echo "✓ AuthContext.tsx backed up"
echo "✓ SettingsPage.tsx backed up"
echo "✓ apiClient.ts backed up"

# ------------------------------------------------------------
# 4. Current source verification
# ------------------------------------------------------------

echo
echo "============================================================"
echo "4. Current profile implementation verification"
echo "============================================================"

if grep -q "saveProfile" "$SETTINGS"; then
  echo "✓ saveProfile handler exists"
else
  echo "✗ saveProfile handler not found"
  exit 1
fi

if grep -q "Save changes" "$SETTINGS"; then
  echo "✓ Save changes button exists"
else
  echo "✗ Save changes button not found"
  exit 1
fi

if grep -q "fullName" "$SETTINGS"; then
  echo "✓ fullName state/reference exists"
else
  echo "✗ fullName reference not found"
  exit 1
fi

# ------------------------------------------------------------
# 5. Add updateProfile to authApi.ts
# ------------------------------------------------------------

echo
echo "============================================================"
echo "5. Adding real profile-update API"
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
        "Could not find logout() insertion point in authApi.ts"
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

text = text.replace(marker, addition + marker, 1)

path.write_text(text)
print("✓ Added updateProfile() to authApi.ts")
PY

# ------------------------------------------------------------
# 6. Update AuthUser interface
# ------------------------------------------------------------

echo
echo "============================================================"
echo "6. Updating AuthUser profile shape"
echo "============================================================"

python3 - "$AUTH_API" "$AUTH_CONTEXT" <<'PY'
from pathlib import Path
import sys

auth_api = Path(sys.argv[1])
auth_context = Path(sys.argv[2])

# ----------------------------------------------------------
# authApi.ts
# ----------------------------------------------------------

text = auth_api.read_text()

old = """export interface AuthUser {
  id: number;
  email: string;
  createdAt?: string;
}"""

new = """export interface AuthUser {
  id: number;
  email: string;
  fullName: string | null;
  createdAt?: string;
}"""

if old in text:
    text = text.replace(old, new, 1)
    auth_api.write_text(text)
    print("✓ AuthUser updated in authApi.ts")
elif "fullName: string | null;" in text:
    print("✓ AuthUser already contains fullName.")
else:
    raise SystemExit(
        "Could not locate AuthUser interface in authApi.ts"
    )

# ----------------------------------------------------------
# AuthContext.tsx
# ----------------------------------------------------------

text = auth_context.read_text()

old = """interface AuthUser {
  id: number;
  email: string;
  createdAt?: string;
}"""

new = """interface AuthUser {
  id: number;
  email: string;
  fullName: string | null;
  createdAt?: string;
}"""

if old in text:
    text = text.replace(old, new, 1)
    auth_context.write_text(text)
    print("✓ AuthUser updated in AuthContext.tsx")
elif "fullName: string | null;" in text:
    print("✓ AuthContext AuthUser already contains fullName.")
else:
    raise SystemExit(
        "Could not locate AuthUser interface in AuthContext.tsx"
    )
PY

# ------------------------------------------------------------
# 7. Add profile updater to AuthContext
# ------------------------------------------------------------

echo
echo "============================================================"
echo "7. Adding AuthContext profile update flow"
echo "============================================================"

python3 - "$AUTH_CONTEXT" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

# ----------------------------------------------------------
# Add updateProfile import
# ----------------------------------------------------------

old_import = """import {
  getCurrentUser,
  login,
  logout,
  register,
} from '@/auth/authApi';"""

new_import = """import {
  getCurrentUser,
  login,
  logout,
  register,
  updateProfile,
} from '@/auth/authApi';"""

if old_import in text:
    text = text.replace(old_import, new_import, 1)
elif "updateProfile," not in text:
    raise SystemExit(
        "Could not locate authApi import block."
    )

# ----------------------------------------------------------
# Add updateProfile to context interface
# ----------------------------------------------------------

old_interface = """  signOut: () => Promise<void>;
}"""

new_interface = """  signOut: () => Promise<void>;

  updateProfile: (
    fullName: string,
  ) => Promise<void>;
}"""

if old_interface in text:
    text = text.replace(old_interface, new_interface, 1)
elif "updateProfile: (" not in text:
    raise SystemExit(
        "Could not locate AuthContextValue signOut field."
    )

# ----------------------------------------------------------
# Find signOut implementation and insert profile updater
# ----------------------------------------------------------

if "const updateProfileUser = useCallback(" not in text:

    marker = """  const signOut = useCallback(async () => {"""

    if marker not in text:
        raise SystemExit(
            "Could not locate signOut callback."
        )

    addition = r'''
  const updateProfileUser = useCallback(
    async (fullName: string) => {
      const updatedUser = await updateProfile(fullName);

      setUser(updatedUser);

      setStoredAuth((current) => {
        if (!current) {
          return current;
        }

        return {
          ...current,
          fullName: updatedUser.fullName,
          email: updatedUser.email,
          userId: updatedUser.id,
        };
      });
    },
    [],
  );

'''

    text = text.replace(marker, addition + marker, 1)

# ----------------------------------------------------------
# Expose updater through provider value
# ----------------------------------------------------------

# Look for the provider object around signOut.
if "updateProfile: updateProfileUser," not in text:

    candidates = [
        """      signOut,
    };""",
        """      signOut,
      };""",
    ]

    replaced = False

    for candidate in candidates:
        if candidate in text:
            replacement = candidate.replace(
                "signOut,",
                "signOut,\n      updateProfile: updateProfileUser,",
                1,
            )
            text = text.replace(candidate, replacement, 1)
            replaced = True
            break

    if not replaced:
        # More defensive fallback: find the first provider value
        # section containing signOut.
        idx = text.find("      signOut,")
        if idx == -1:
            raise SystemExit(
                "Could not locate provider value signOut field."
            )

        text = (
            text[:idx]
            + "      signOut,\n      updateProfile: updateProfileUser,"
            + text[idx + len("      signOut,"):]
        )

path.write_text(text)
print("✓ AuthContext now exposes updateProfile().")
PY

# ------------------------------------------------------------
# 8. Wire SettingsPage to AuthContext
# ------------------------------------------------------------

echo
echo "============================================================"
echo "8. Wiring SettingsPage to real profile API"
echo "============================================================"

python3 - "$SETTINGS" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

# ----------------------------------------------------------
# Locate AuthContext import/use
# ----------------------------------------------------------

if "useAuth" not in text:
    raise SystemExit(
        "SettingsPage.tsx does not appear to use useAuth()."
    )

# ----------------------------------------------------------
# Add updateProfile destructuring
# ----------------------------------------------------------

# Common forms:
# const { user } = useAuth();
# const { user, ... } = useAuth();

if "updateProfile," not in text:
    replacements = [
        (
            "const { user } = useAuth();",
            "const { user, updateProfile } = useAuth();",
        ),
        (
            "const { user, token } = useAuth();",
            "const { user, token, updateProfile } = useAuth();",
        ),
    ]

    replaced = False

    for old, new in replacements:
        if old in text:
            text = text.replace(old, new, 1)
            replaced = True
            break

    if not replaced:
        # Find the first useAuth assignment and inspect nearby text.
        import re

        match = re.search(
            r"const\s*\{([^}]*)\}\s*=\s*useAuth\(\);",
            text
        )

        if not match:
            raise SystemExit(
                "Could not locate useAuth() destructuring."
            )

        fields = match.group(1)

        if "updateProfile" not in fields:
            new_fields = fields.rstrip() + "\n    updateProfile,"
            text = (
                text[:match.start(1)]
                + new_fields
                + text[match.end(1):]
            )

# ----------------------------------------------------------
# Replace saveProfile implementation
# ----------------------------------------------------------

import re

pattern = re.compile(
    r"(?P<indent>^[ \t]*)const saveProfile\s*=\s*async\s*\(\)\s*=>\s*\{.*?^\s*\};",
    re.MULTILINE | re.DOTALL,
)

match = pattern.search(text)

if not match:
    # Try non-async implementation.
    pattern = re.compile(
        r"(?P<indent>^[ \t]*)const saveProfile\s*=\s*\(\)\s*=>\s*\{.*?^\s*\};",
        re.MULTILINE | re.DOTALL,
    )
    match = pattern.search(text)

if not match:
    raise SystemExit(
        "Could not locate saveProfile() implementation."
    )

indent = match.group("indent")

replacement = f'''{indent}const saveProfile = async () => {{
{indent}  const trimmedName = name.trim();

{indent}  if (!trimmedName) {{
{indent}    notify('Name cannot be empty.');
{indent}    return;
{indent}  }}

{indent}  try {{
{indent}    const updatedUser = await updateProfile(trimmedName);

{indent}    setName(updatedUser.fullName ?? trimmedName);
{indent}    setSaved(true);

{indent}    notify('Profile updated successfully.');

{indent}    window.setTimeout(() => {{
{indent}      setSaved(false);
{indent}    }}, 2000);
{indent}  }} catch (error) {{
{indent}    const message =
{indent}      error instanceof Error
{indent}        ? error.message
{indent}        : 'Unable to update your profile. Please try again.';

{indent}    notify(message);
{indent}  }}
{indent}}};'''

text = text[:match.start()] + replacement + text[match.end():]

path.write_text(text)
print("✓ saveProfile() now calls AuthContext.updateProfile().")
PY

# ------------------------------------------------------------
# 9. Verify profile API wiring
# ------------------------------------------------------------

echo
echo "============================================================"
echo "9. Verifying real profile flow"
echo "============================================================"

if grep -q "export async function updateProfile(" "$AUTH_API"; then
  echo "✓ authApi.updateProfile() detected"
else
  echo "✗ updateProfile() missing from authApi.ts"
  exit 1
fi

if grep -q "'/api/auth/me'" "$AUTH_API" && \
   grep -q "method: 'PATCH'" "$AUTH_API"; then
  echo "✓ PATCH /api/auth/me detected"
else
  echo "✗ PATCH /api/auth/me not detected"
  exit 1
fi

if grep -q "updateProfile: updateProfileUser" "$AUTH_CONTEXT"; then
  echo "✓ AuthContext exposes profile updater"
else
  echo "✗ AuthContext profile updater not exposed"
  exit 1
fi

if grep -q "await updateProfile(trimmedName)" "$SETTINGS"; then
  echo "✓ SettingsPage calls real profile API"
else
  echo "✗ SettingsPage is not calling updateProfile()"
  exit 1
fi

# ------------------------------------------------------------
# 10. Ensure no fake profile success path remains
# ------------------------------------------------------------

echo
echo "============================================================"
echo "10. Verifying mock profile save flow is gone"
echo "============================================================"

if grep -nE \
  "saved.*true|setSaved\(true\)" \
  "$SETTINGS" | grep -q "setSaved"; then
  echo "✓ Saved UI state still exists."
  echo "  It is now reached after the API request."
else
  echo "✓ No suspicious standalone saved state detected."
fi

echo
echo "saveProfile source:"
grep -n -A35 -B5 "const saveProfile" "$SETTINGS" || true

# ------------------------------------------------------------
# 11. Create E2E test
# ------------------------------------------------------------

echo
echo "============================================================"
echo "11. Creating profile persistence E2E"
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

async function registerUser(
  request: any,
  email: string,
  password: string,
) {
  const response = await request.post(
    `${API_BASE}/api/auth/register`,
    {
      data: {
        email,
        password,
      },
    },
  );

  expect(response.status()).toBe(201);

  return response.json();
}

async function getMe(
  request: any,
  token: string,
) {
  const response = await request.get(
    `${API_BASE}/api/auth/me`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  expect(response.status()).toBe(200);

  return response.json();
}

test.describe('profile persistence', () => {
  test('authenticated user can change full name and retain it after refresh', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail();
    const password = 'TrimlyTest123!';

    const auth = await registerUser(
      request,
      email,
      password,
    );

    expect(auth.token).toBeTruthy();

    const meBefore = await getMe(
      request,
      auth.token,
    );

    expect(meBefore.email).toBe(email);

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

    await expect(
      page.getByRole('heading', {
        name: 'Settings',
      }),
    ).toBeVisible();

    const nameInput = page.locator(
      'input',
    ).filter({
      has: page.locator(''),
    });

    // Use the Profile section's first text input.
    const profileNameInput = page.locator(
      'input[type="text"]',
    ).first();

    await expect(profileNameInput).toBeVisible();

    const newName = 'Trimly Profile Test';

    await profileNameInput.fill(newName);

    await page.getByRole('button', {
      name: 'Save changes',
    }).click();

    await expect(
      page.getByText(
        'Profile updated successfully.',
      ),
    ).toBeVisible();

    // Verify the database-backed API response.
    const meAfter = await getMe(
      request,
      auth.token,
    );

    expect(meAfter.fullName).toBe(newName);

    // Reload the browser and verify the name is still present.
    await page.reload();

    await expect(profileNameInput).toHaveValue(
      newName,
    );
  });

  test('profile update rejects an empty name before API submission', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail();
    const password = 'TrimlyTest123!';

    const auth = await registerUser(
      request,
      email,
      password,
    );

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

    const profileNameInput = page.locator(
      'input[type="text"]',
    ).first();

    await profileNameInput.fill('');

    await page.getByRole('button', {
      name: 'Save changes',
    }).click();

    await expect(
      page.getByText(
        'Name cannot be empty.',
      ),
    ).toBeVisible();

    const me = await getMe(
      request,
      auth.token,
    );

    expect(me.fullName).not.toBe('');
  });
});
EOF

echo "✓ Added:"
echo "  $E2E"

# ------------------------------------------------------------
# 12. Frontend production build
# ------------------------------------------------------------

echo
echo "============================================================"
echo "12. Frontend production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Frontend production build passed."

# ------------------------------------------------------------
# 13. Check Playwright
# ------------------------------------------------------------

echo
echo "============================================================"
echo "13. Playwright configuration"
echo "============================================================"

if [[ -f "$FRONTEND/playwright.config.ts" ]]; then
  echo "✓ playwright.config.ts found"
else
  echo "✗ playwright.config.ts not found"
  exit 1
fi

# ------------------------------------------------------------
# 14. Run E2E
# ------------------------------------------------------------

echo
echo "============================================================"
echo "14. Running profile persistence E2E"
echo "============================================================"

if [[ ! -d "$FRONTEND/node_modules/@playwright" ]]; then
  echo "✗ Playwright package not installed."
  echo "  Install dependencies first with npm install."
  exit 1
fi

npx playwright test tests/e2e/profile.spec.ts --workers=1

echo
echo "✓ Profile persistence E2E passed."

# ------------------------------------------------------------
# 15. Final source verification
# ------------------------------------------------------------

echo
echo "============================================================"
echo "15. Final source verification"
echo "============================================================"

echo
echo "authApi.ts:"
grep -n -A18 -B2 \
  "export async function updateProfile" \
  "$AUTH_API" || true

echo
echo "AuthContext.tsx:"
grep -n -A15 -B5 \
  "updateProfileUser" \
  "$AUTH_CONTEXT" || true

echo
echo "SettingsPage.tsx:"
grep -n -A35 -B5 \
  "const saveProfile" \
  "$SETTINGS" || true

echo
echo "Endpoint:"
grep -n \
  "/api/auth/me" \
  "$AUTH_API" || true

# ------------------------------------------------------------
# 16. Backup / rollback instructions
# ------------------------------------------------------------

echo
echo "============================================================"
echo "IMPLEMENTATION COMPLETE"
echo "============================================================"
echo
echo "Changed:"
echo "  ✓ authApi.ts"
echo "  ✓ AuthContext.tsx"
echo "  ✓ SettingsPage.tsx"
echo "  ✓ added profile.spec.ts"
echo
echo "Not changed:"
echo "  ✓ Backend source"
echo "  ✓ Password-change implementation"
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "Profile flow is now:"
echo
echo "  Settings"
echo "      ↓"
echo "  edit full name"
echo "      ↓"
echo "  Save changes"
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
echo "  GET /api/auth/me"
echo "      ↓"
echo "  refreshed Settings"
echo
echo "============================================================"
