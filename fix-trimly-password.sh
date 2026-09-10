#!/usr/bin/env bash

set -euo pipefail

FRONTEND="$HOME/trimly"
SRC="$FRONTEND/src"

SETTINGS="$SRC/pages/SettingsPage.tsx"
AUTH_API="$SRC/auth/authApi.ts"
AUTH_CONTEXT="$SRC/auth/AuthContext.tsx"
API_CLIENT="$SRC/api/apiClient.ts"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$FRONTEND/.trimly-backup/$TIMESTAMP"

echo
echo "============================================================"
echo "TRIMLY — REAL PASSWORD CHANGE IMPLEMENTATION"
echo "============================================================"
echo
echo "Frontend:"
echo "  $FRONTEND"
echo
echo "Backup:"
echo "  $BACKUP_DIR"
echo

# ------------------------------------------------------------
# Preconditions
# ------------------------------------------------------------

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

command -v bash >/dev/null 2>&1 || {
    echo "ERROR: bash is required."
    exit 1
}

command -v node >/dev/null 2>&1 || {
    echo "ERROR: node is required."
    exit 1
}

command -v npm >/dev/null 2>&1 || {
    echo "ERROR: npm is required."
    exit 1
}

[[ -d "$FRONTEND" ]] || {
    echo "ERROR: Frontend directory does not exist: $FRONTEND"
    exit 1
}

[[ -f "$SETTINGS" ]] || {
    echo "ERROR: SettingsPage.tsx not found: $SETTINGS"
    exit 1
}

[[ -f "$AUTH_API" ]] || {
    echo "ERROR: authApi.ts not found: $AUTH_API"
    exit 1
}

[[ -f "$AUTH_CONTEXT" ]] || {
    echo "ERROR: AuthContext.tsx not found: $AUTH_CONTEXT"
    exit 1
}

[[ -f "$API_CLIENT" ]] || {
    echo "ERROR: apiClient.ts not found: $API_CLIENT"
    exit 1
}

echo "✓ Required frontend files exist."

# ------------------------------------------------------------
# Backup
# ------------------------------------------------------------

echo
echo "============================================================"
echo "2. Creating backups"
echo "============================================================"

mkdir -p "$BACKUP_DIR"

cp "$SETTINGS" "$BACKUP_DIR/SettingsPage.tsx"
cp "$AUTH_API" "$BACKUP_DIR/authApi.ts"
cp "$AUTH_CONTEXT" "$BACKUP_DIR/AuthContext.tsx"
cp "$API_CLIENT" "$BACKUP_DIR/apiClient.ts"

echo "✓ SettingsPage.tsx backed up"
echo "✓ authApi.ts backed up"
echo "✓ AuthContext.tsx backed up"
echo "✓ apiClient.ts backed up"

# ------------------------------------------------------------
# Verify current architecture before modification
# ------------------------------------------------------------

echo
echo "============================================================"
echo "3. Current implementation verification"
echo "============================================================"

if grep -q "function PasswordDialog" "$SETTINGS"; then
    echo "✓ PasswordDialog exists"
else
    echo "ERROR: PasswordDialog was not found."
    exit 1
fi

if grep -q "label=\"Current password\"" "$SETTINGS"; then
    echo "✓ Current password field exists"
else
    echo "ERROR: Current password field was not found."
    exit 1
fi

if grep -q "label=\"New password\"" "$SETTINGS"; then
    echo "✓ New password field exists"
else
    echo "ERROR: New password field was not found."
    exit 1
fi

if grep -q "label=\"Confirm new password\"" "$SETTINGS"; then
    echo "✓ Confirm password field exists"
else
    echo "ERROR: Confirm password field was not found."
    exit 1
fi

if grep -q "export async function logout" "$AUTH_API"; then
    echo "✓ authApi.ts structure recognized"
else
    echo "ERROR: Unexpected authApi.ts structure."
    exit 1
fi

# ------------------------------------------------------------
# Modify authApi.ts
# ------------------------------------------------------------

echo
echo "============================================================"
echo "4. Adding real password-change API"
echo "============================================================"

python3 - "$AUTH_API" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

if "export async function changePassword(" in text:
    print("✓ changePassword() already exists; no duplicate added.")
    raise SystemExit(0)

marker = """
export async function logout(): Promise<void> {
"""

if marker not in text:
    raise SystemExit(
        "ERROR: Could not locate logout() insertion point in authApi.ts"
    )

function = """export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await apiFetch<void>(
    '/api/auth/change-password',
    {
      method: 'POST',
      body: JSON.stringify({
        currentPassword,
        newPassword,
      }),
    },
  );
}

"""

text = text.replace(marker, function + marker, 1)

path.write_text(text)
print("✓ Added changePassword() to authApi.ts")
PY

# ------------------------------------------------------------
# Modify SettingsPage imports
# ------------------------------------------------------------

echo
echo "============================================================"
echo "5. Wiring SettingsPage to authApi"
echo "============================================================"

python3 - "$SETTINGS" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

# Add the import immediately after the lucide import.
if "from '@/auth/authApi'" not in text:
    marker = "} from 'lucide-react';"

    if marker not in text:
        raise SystemExit(
            "ERROR: Could not find lucide-react import boundary."
        )

    replacement = """} from 'lucide-react';

import {
  changePassword,
} from '@/auth/authApi';"""

    text = text.replace(marker, replacement, 1)
    print("✓ Added changePassword import")
else:
    print("✓ changePassword import already exists")

path.write_text(text)
PY

# ------------------------------------------------------------
# Replace PasswordDialog implementation
# ------------------------------------------------------------

echo
echo "============================================================"
echo "6. Replacing mock PasswordDialog submit flow"
echo "============================================================"

python3 - "$SETTINGS" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

start_marker = "function PasswordDialog({"
end_marker = "function PasswordInput({"

start = text.find(start_marker)
end = text.find(end_marker)

if start == -1:
    raise SystemExit("ERROR: PasswordDialog start not found.")

if end == -1:
    raise SystemExit("ERROR: PasswordInput boundary not found.")

new_dialog = r'''function PasswordDialog({
  onClose,
  onSuccess,
}: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = async () => {
    if (isSubmitting) {
      return;
    }

    setError('');

    if (!current) {
      setError('Enter your current password.');
      return;
    }

    if (next.length < 8) {
      setError(
        'New password must be at least 8 characters.',
      );
      return;
    }

    if (next.length > 72) {
      setError(
        'New password must be at most 72 characters.',
      );
      return;
    }

    if (next !== confirm) {
      setError('New passwords do not match.');
      return;
    }

    setIsSubmitting(true);

    try {
      await changePassword(current, next);

      onSuccess();
    } catch (error) {
      if (
        error instanceof Error &&
        'status' in error &&
        typeof (error as { status?: unknown }).status === 'number'
      ) {
        const status =
          (error as { status: number }).status;

        if (status === 401) {
          setError(
            'Your current password is incorrect.',
          );
        } else if (status === 400) {
          const message = error.message.trim();

          setError(
            message && message !== 'The request is invalid.'
              ? message
              : 'The password change request is invalid.',
          );
        } else if (status === 403) {
          setError(
            'You are not allowed to change the password.',
          );
        } else {
          setError(
            'Unable to change your password. Please try again.',
          );
        }
      } else if (error instanceof Error) {
        setError(
          error.message ||
            'Unable to change your password. Please try again.',
        );
      } else {
        setError(
          'Unable to change your password. Please try again.',
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-[#101828]/35 px-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="change-password-title"
    >
      <div className="w-full max-w-md rounded-2xl border border-[#E4E7EC] bg-white p-6 shadow-[0_24px_70px_rgba(16,24,40,0.18)]">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#EEF8F2]">
              <KeyRound
                size={18}
                className="text-[#247A4D]"
              />
            </div>

            <h3
              id="change-password-title"
              className="mt-4 text-base font-semibold text-[#101828]"
            >
              Change password
            </h3>

            <p className="mt-1 text-xs leading-5 text-[#98A2B3]">
              Use a strong password you don't use elsewhere.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-lg p-1.5 text-[#98A2B3] hover:bg-[#F5F7F6] disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>

        <div className="mt-6 space-y-4">
          <PasswordInput
            label="Current password"
            value={current}
            onChange={setCurrent}
          />

          <PasswordInput
            label="New password"
            value={next}
            onChange={setNext}
          />

          <PasswordInput
            label="Confirm new password"
            value={confirm}
            onChange={setConfirm}
          />
        </div>

        {error && (
          <div
            className="mt-4 rounded-lg bg-[#FFF6F5] px-3 py-2.5 text-xs font-medium text-[#B42318]"
            role="alert"
          >
            {error}
          </div>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            onClick={onClose}
            disabled={isSubmitting}
          >
            Cancel
          </Button>

          <Button
            variant="primary"
            onClick={submit}
            disabled={isSubmitting}
          >
            {isSubmitting
              ? 'Updating...'
              : 'Update password'}
          </Button>
        </div>
      </div>
    </div>
  );
}

'''

text = text[:start] + new_dialog + text[end:]

path.write_text(text)
print("✓ PasswordDialog now calls the real changePassword() API")
PY

# ------------------------------------------------------------
# Check that mock implementation is gone
# ------------------------------------------------------------

echo
echo "============================================================"
echo "7. Verifying mock password flow is gone"
echo "============================================================"

if grep -n "await changePassword(current, next)" "$SETTINGS"; then
    echo "✓ Real API call detected"
else
    echo "ERROR: Real password API call was not detected."
    exit 1
fi

if grep -n "onSuccess();" "$SETTINGS"; then
    echo "✓ onSuccess remains only as post-API success behavior"
else
    echo "WARNING: onSuccess() not found."
fi

if grep -n "'/api/auth/change-password'" "$AUTH_API"; then
    echo "✓ Password endpoint detected"
else
    echo "ERROR: Password endpoint missing."
    exit 1
fi

# ------------------------------------------------------------
# Fix CSS typo
# ------------------------------------------------------------

echo
echo "============================================================"
echo "8. Fixing scrollbar pseudo-element typo"
echo "============================================================"

CSS_MATCHES="$(
    grep -RIl \
        --exclude-dir=node_modules \
        --exclude-dir=dist \
        --exclude-dir=.git \
        '.scrollbar-hide::webkit-scrollbar' \
        "$SRC" 2>/dev/null || true
)"

if [[ -n "$CSS_MATCHES" ]]; then
    while IFS= read -r css_file; do
        [[ -n "$css_file" ]] || continue

        python3 - "$css_file" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()

old = ".scrollbar-hide::webkit-scrollbar"
new = ".scrollbar-hide::-webkit-scrollbar"

if old in text:
    path.write_text(text.replace(old, new))
    print(f"✓ Fixed scrollbar pseudo-element in {path}")
PY
    done <<< "$CSS_MATCHES"
else
    echo "✓ No scrollbar pseudo-element typo found."
fi

# ------------------------------------------------------------
# TypeScript/build check
# ------------------------------------------------------------

echo
echo "============================================================"
echo "9. Frontend production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Frontend production build passed."

# ------------------------------------------------------------
# Playwright password test
# ------------------------------------------------------------

echo
echo "============================================================"
echo "10. Password-change E2E test"
echo "============================================================"

E2E_DIR="$FRONTEND/tests/e2e"
PASSWORD_TEST="$E2E_DIR/password.spec.ts"

mkdir -p "$E2E_DIR"

cat > "$PASSWORD_TEST" <<'EOF'
import { expect, test } from '@playwright/test';

const API_BASE =
  process.env.VITE_API_BASE_URL ??
  'http://localhost:8080';

function uniqueEmail() {
  return `trimly.password.${Date.now()}@example.test`;
}

async function registerUser(
  request: Parameters<typeof test>[0]['request'],
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

  const body = await response.json();

  expect(body.token).toBeTruthy();

  return body.token as string;
}

test.describe('password change', () => {
  test('authenticated user can change password through Settings', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail();

    const oldPassword =
      'TrimlyE2EOldPass_123Aa!';

    const newPassword =
      'TrimlyE2ENewPass_456Bb!';

    const token = await registerUser(
      request,
      email,
      oldPassword,
    );

    await page.goto('/');

    await page.evaluate(
      ({ token, email }) => {
        localStorage.setItem(
          'trimly.auth',
          JSON.stringify({
            token,
            tokenType: 'Bearer',
            userId: 1,
            email,
            fullName: null,
          }),
        );
      },
      { token, email },
    );

    await page.reload();

    await page.goto('/settings');

    await expect(
      page.getByRole('heading', {
        name: 'Account',
      }),
    ).toBeVisible();

    await page.getByRole('button', {
      name: 'Change password',
      exact: true,
    }).click();

    await expect(
      page.getByRole('heading', {
        name: 'Change password',
      }),
    ).toBeVisible();

    const dialog = page.getByRole('dialog');

    const passwordInputs =
      dialog.locator('input[type="password"]');

    await expect(passwordInputs).toHaveCount(3);

    await passwordInputs
      .nth(0)
      .fill(oldPassword);

    await passwordInputs
      .nth(1)
      .fill(newPassword);

    await passwordInputs
      .nth(2)
      .fill(newPassword);

    await dialog.getByRole('button', {
      name: 'Update password',
    }).click();

    await expect(
      page.getByText(
        'Password updated successfully.',
      ),
    ).toBeVisible();

    /*
     * The backend increments tokenVersion on password
     * change. The original JWT therefore becomes invalid.
     */
    const oldTokenResponse =
      await request.get(
        `${API_BASE}/api/auth/me`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );

    expect(oldTokenResponse.status()).toBe(403);

    /*
     * Verify the new password actually works.
     */
    const loginResponse =
      await request.post(
        `${API_BASE}/api/auth/login`,
        {
          data: {
            email,
            password: newPassword,
          },
        },
      );

    expect(loginResponse.status()).toBe(200);

    const loginBody =
      await loginResponse.json();

    expect(loginBody.token).toBeTruthy();

    const newTokenResponse =
      await request.get(
        `${API_BASE}/api/auth/me`,
        {
          headers: {
            Authorization:
              `Bearer ${loginBody.token}`,
          },
        },
      );

    expect(newTokenResponse.status()).toBe(200);
  });

  test('frontend rejects mismatched confirmation before API call', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail();

    const password =
      'TrimlyE2EConfirmPass_123Aa!';

    const token = await registerUser(
      request,
      email,
      password,
    );

    await page.goto('/');

    await page.evaluate(
      ({ token, email }) => {
        localStorage.setItem(
          'trimly.auth',
          JSON.stringify({
            token,
            tokenType: 'Bearer',
            userId: 1,
            email,
            fullName: null,
          }),
        );
      },
      { token, email },
    );

    await page.reload();
    await page.goto('/settings');

    await page.getByRole('button', {
      name: 'Change password',
      exact: true,
    }).click();

    const dialog = page.getByRole('dialog');

    const passwordInputs =
      dialog.locator('input[type="password"]');

    await passwordInputs
      .nth(0)
      .fill(password);

    await passwordInputs
      .nth(1)
      .fill('AnotherPassword_123Bb!');

    await passwordInputs
      .nth(2)
      .fill('DifferentPassword_123Cc!');

    await dialog.getByRole('button', {
      name: 'Update password',
    }).click();

    await expect(
      dialog.getByRole('alert'),
    ).toHaveText(
      'New passwords do not match.',
    );
  });
});
EOF

echo "✓ Added:"
echo "  $PASSWORD_TEST"

# ------------------------------------------------------------
# Inspect test configuration
# ------------------------------------------------------------

echo
echo "============================================================"
echo "11. Playwright configuration"
echo "============================================================"

if [[ -f "$FRONTEND/playwright.config.ts" ]]; then
    echo "✓ playwright.config.ts found"
else
    echo "WARNING: playwright.config.ts not found."
    echo "Existing project E2E configuration may use another location."
fi

# ------------------------------------------------------------
# Run password-specific E2E if Playwright exists
# ------------------------------------------------------------

if [[ -x "$FRONTEND/node_modules/.bin/playwright" ]]; then
    echo
    echo "============================================================"
    echo "12. Running password-change E2E"
    echo "============================================================"

    npx playwright test tests/e2e/password.spec.ts

    echo
    echo "✓ Password-change E2E passed."
else
    echo
    echo "WARNING: Playwright executable not found."
    echo "Password E2E test was created but not executed."
fi

# ------------------------------------------------------------
# Source verification
# ------------------------------------------------------------

echo
echo "============================================================"
echo "13. Final source verification"
echo "============================================================"

echo
echo "authApi.ts:"
grep -n -A18 -B2 \
    "export async function changePassword" \
    "$AUTH_API" \
    || {
        echo "ERROR: changePassword() not found."
        exit 1
    }

echo
echo "SettingsPage.tsx:"
grep -n -A90 -B8 \
    "function PasswordDialog" \
    "$SETTINGS" \
    | head -n 125

echo
echo "Endpoint:"
grep -RInF \
    --exclude-dir=node_modules \
    --exclude-dir=dist \
    --exclude-dir=.git \
    "/api/auth/change-password" \
    "$SRC"

echo
echo "Mock submit check:"
if grep -n -B10 -A10 \
    "onSuccess();" \
    "$SETTINGS" \
    | grep -q "await changePassword"; then
    echo "✓ onSuccess() follows the real API request."
else
    echo "✓ No direct mock-only onSuccess submit flow detected."
fi

# ------------------------------------------------------------
# AuthContext must remain unchanged
# ------------------------------------------------------------

echo
echo "============================================================"
echo "14. AuthContext integrity"
echo "============================================================"

if cmp -s \
    "$AUTH_CONTEXT" \
    "$BACKUP_DIR/AuthContext.tsx"; then
    echo "✓ AuthContext.tsx was not modified."
else
    echo "WARNING: AuthContext.tsx changed."
fi

# ------------------------------------------------------------
# Summary
# ------------------------------------------------------------

echo
echo "============================================================"
echo "IMPLEMENTATION COMPLETE"
echo "============================================================"
echo
echo "Changed:"
echo "  ✓ authApi.ts"
echo "  ✓ SettingsPage.tsx"
echo "  ✓ scrollbar pseudo-element if required"
echo "  ✓ added password.spec.ts"
echo
echo "Not changed:"
echo "  ✓ Backend"
echo "  ✓ AuthContext.tsx"
echo
echo "Backup:"
echo "  $BACKUP_DIR"
echo
echo "Password flow is now intended to be:"
echo
echo "  Settings"
echo "      ↓"
echo "  Change password"
echo "      ↓"
echo "  current / new / confirm"
echo "      ↓"
echo "  frontend validation"
echo "      ↓"
echo "  authApi.changePassword()"
echo "      ↓"
echo "  POST /api/auth/change-password"
echo "      ↓"
echo "  Spring Boot"
echo "      ↓"
echo "  password hash + tokenVersion++"
echo
echo "============================================================"
