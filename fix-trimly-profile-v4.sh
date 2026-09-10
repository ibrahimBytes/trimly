#!/usr/bin/env bash

set -euo pipefail

FRONTEND="$HOME/trimly"
BACKEND="$HOME/url-shortener"

AUTH_CONTEXT="$FRONTEND/src/auth/AuthContext.tsx"
AUTH_API="$FRONTEND/src/auth/authApi.ts"
SETTINGS="$FRONTEND/src/pages/SettingsPage.tsx"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$FRONTEND/.trimly-backup/$STAMP"

echo
echo "============================================================"
echo "TRIMLY — REAL PROFILE NAME PERSISTENCE v4"
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

# ==============================================================
# 1. Preconditions
# ==============================================================

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

for file in "$AUTH_CONTEXT" "$AUTH_API" "$SETTINGS"; do
  if [[ ! -f "$file" ]]; then
    echo "✗ Missing required file:"
    echo "  $file"
    exit 1
  fi
done

grep -q "export async function updateProfile" "$AUTH_API" \
  || {
    echo "✗ authApi.ts does not contain updateProfile()."
    exit 1
  }

grep -q "function AccountSection" "$SETTINGS" \
  || {
    echo "✗ AccountSection not found."
    exit 1
  }

grep -q "const saveProfile" "$SETTINGS" \
  || {
    echo "✗ saveProfile() not found."
    exit 1
  }

echo "✓ Required files exist."
echo "✓ authApi.updateProfile() exists."
echo "✓ AccountSection exists."
echo "✓ saveProfile() exists."

# ==============================================================
# 2. Backend verification
# ==============================================================

echo
echo "============================================================"
echo "2. Backend verification"
echo "============================================================"

grep -Rqs "updateProfile" "$BACKEND/src/main/java" \
  || {
    echo "✗ Backend updateProfile() not detected."
    exit 1
  }

grep -Rqs '@PatchMapping("/me")' "$BACKEND/src/main/java" \
  || {
    echo "✗ PATCH /api/auth/me not detected."
    exit 1
  }

grep -Rqs "ProfileUpdateRequest" "$BACKEND/src/main/java" \
  || {
    echo "✗ ProfileUpdateRequest not detected."
    exit 1
  }

echo "✓ Backend updateProfile() detected."
echo "✓ PATCH /api/auth/me detected."
echo "✓ ProfileUpdateRequest detected."

# ==============================================================
# 3. Backup
# ==============================================================

echo
echo "============================================================"
echo "3. Creating backup"
echo "============================================================"

mkdir -p "$BACKUP"

cp "$AUTH_CONTEXT" "$BACKUP/AuthContext.tsx"
cp "$AUTH_API" "$BACKUP/authApi.ts"
cp "$SETTINGS" "$BACKUP/SettingsPage.tsx"

echo "✓ AuthContext.tsx backed up"
echo "✓ authApi.ts backed up"
echo "✓ SettingsPage.tsx backed up"

# ==============================================================
# 4. Patch AuthContext
# ==============================================================

echo
echo "============================================================"
echo "4. Updating AuthContext"
echo "============================================================"

python3 - "$AUTH_CONTEXT" <<'PY'
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
# Import updateProfile API
# --------------------------------------------------------------

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
  updateProfile as updateProfileApi,
} from '@/auth/authApi';"""

if "updateProfile as updateProfileApi" not in text:
    text = replace_exact(
        text,
        old,
        new,
        "authApi import block",
    )

    print("✓ Added updateProfile API import.")
else:
    print("✓ updateProfile API import already exists.")


# --------------------------------------------------------------
# AuthContextValue
# --------------------------------------------------------------

old = """  signOut: () => Promise<void>;
}"""

new = """  signOut: () => Promise<void>;

  updateProfile: (
    fullName: string,
  ) => Promise<AuthUser>;
}"""

if "updateProfile: (" not in text:
    text = replace_exact(
        text,
        old,
        new,
        "AuthContextValue",
    )

    print("✓ Added updateProfile() to AuthContextValue.")
else:
    print("✓ AuthContextValue already exposes updateProfile().")


# --------------------------------------------------------------
# updateProfile callback
# --------------------------------------------------------------

if "const updateProfile = useCallback(" not in text:

    marker = """  /*
   * ============================================================
   * SIGN OUT
   * ============================================================
   */"""

    if text.count(marker) != 1:
        raise SystemExit(
            "✗ Could not uniquely locate SIGN OUT section."
        )

    block = """  /*
   * ============================================================
   * UPDATE PROFILE
   * ============================================================
   */

  const updateProfile = useCallback(
    async (
      fullName: string,
    ): Promise<AuthUser> => {
      const updatedUser =
        await updateProfileApi(fullName);

      setUser(updatedUser);

      return updatedUser;
    },
    [],
  );

"""

    text = text.replace(
        marker,
        block + marker,
        1,
    )

    print("✓ Added updateProfile() callback.")
else:
    print("✓ updateProfile() callback already exists.")


# --------------------------------------------------------------
# Context value
# --------------------------------------------------------------

old = """        signIn,
        signUp,
        signOut,
      }),
      [
        auth,
        user,
        isLoading,
        signIn,
        signUp,
        signOut,
      ],"""

new = """        signIn,
        signUp,
        signOut,
        updateProfile,
      }),
      [
        auth,
        user,
        isLoading,
        signIn,
        signUp,
        signOut,
        updateProfile,
      ],"""

if "        updateProfile,\n      })," not in text:
    text = replace_exact(
        text,
        old,
        new,
        "AuthContext provider value",
    )

    print("✓ Exposed updateProfile() through provider.")
else:
    print("✓ Provider already exposes updateProfile().")


path.write_text(text)
PY

echo "✓ AuthContext update complete."

# ==============================================================
# 5. Patch AccountSection
# ==============================================================

echo
echo "============================================================"
echo "5. Updating AccountSection"
echo "============================================================"

python3 - "$SETTINGS" <<'PY'
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
# Import useAuth
# --------------------------------------------------------------

if "from '@/auth/AuthContext';" not in text:

    old = """import {
  changePassword,
} from '@/auth/authApi';"""

    new = """import {
  changePassword,
} from '@/auth/authApi';

import { useAuth } from '@/auth/AuthContext';"""

    text = replace_exact(
        text,
        old,
        new,
        "auth imports",
    )

    print("✓ Added useAuth import.")
else:
    print("✓ useAuth import already exists.")


# --------------------------------------------------------------
# AccountSection state
# --------------------------------------------------------------

old = """function AccountSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const [name, setName] =
    useState('Ibrahim Shaik');

  const [email, setEmail] =
    useState('ibrahimshaik@gmail.com');"""

new = """function AccountSection({
  notify,
}: {
  notify: (message: string) => void;
}) {
  const {
    user,
    updateProfile,
  } = useAuth();

  const [name, setName] =
    useState('');

  const [email, setEmail] =
    useState('');"""

text = replace_exact(
    text,
    old,
    new,
    "AccountSection state initialization",
)

print("✓ Removed hard-coded profile values.")


# --------------------------------------------------------------
# Sync fields with authenticated user
# --------------------------------------------------------------

old = """  const [saved, setSaved] =
    useState(false);

  const fileRef =
    useRef<HTMLInputElement>(null);"""

new = """  const [saved, setSaved] =
    useState(false);

  const fileRef =
    useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user) {
      return;
    }

    setName(user.fullName ?? '');
    setEmail(user.email);
  }, [user]);"""

text = replace_exact(
    text,
    old,
    new,
    "AccountSection user synchronization",
)

print("✓ Profile fields now initialize from authenticated user.")


# --------------------------------------------------------------
# Real saveProfile()
# --------------------------------------------------------------

old = """  const saveProfile = () => {
    if (!name.trim()) {
      notify('Please enter your name.');
      return;
    }

    if (!email.trim()) {
      notify('Please enter your email.');
      return;
    }

    setSaved(true);
    notify('Profile updated successfully.');

    window.setTimeout(() => {
      setSaved(false);
    }, 1800);
  };"""

new = """  const saveProfile = async () => {
    const trimmedName = name.trim();

    if (!trimmedName) {
      notify('Please enter your name.');
      return;
    }

    try {
      const updatedUser =
        await updateProfile(trimmedName);

      setName(updatedUser.fullName ?? '');
      setEmail(updatedUser.email);

      setSaved(true);
      notify('Profile updated successfully.');

      window.setTimeout(() => {
        setSaved(false);
      }, 1800);
    } catch {
      notify('Unable to update your profile.');
    }
  };"""

text = replace_exact(
    text,
    old,
    new,
    "mock saveProfile() implementation",
)

print("✓ saveProfile() now calls the real backend.")


# --------------------------------------------------------------
# Email should not pretend to be editable
# --------------------------------------------------------------

old = """            <Field
              label="Email address"
              type="email"
              value={email}
              onChange={setEmail}
            />"""

new = """            <Field
              label="Email address"
              type="email"
              value={email}
              onChange={setEmail}
              disabled
            />"""

text = replace_exact(
    text,
    old,
    new,
    "email field",
)

print("✓ Email field is read-only.")


path.write_text(text)
PY

echo "✓ AccountSection update complete."

# ==============================================================
# 6. Source verification
# ==============================================================

echo
echo "============================================================"
echo "6. Source verification"
echo "============================================================"

grep -q "updateProfile as updateProfileApi" "$AUTH_CONTEXT"
echo "✓ AuthContext imports updateProfile API."

grep -q "updateProfile: (" "$AUTH_CONTEXT"
echo "✓ AuthContextValue exposes updateProfile()."

grep -q "const updateProfile = useCallback" "$AUTH_CONTEXT"
echo "✓ AuthContext implements updateProfile()."

grep -q "        updateProfile," "$AUTH_CONTEXT"
echo "✓ Provider exposes updateProfile()."

grep -q "const {.*user" "$SETTINGS" 2>/dev/null || \
  grep -q "user," "$SETTINGS"
echo "✓ AccountSection uses authenticated user."

grep -q "await updateProfile(trimmedName)" "$SETTINGS"
echo "✓ AccountSection calls updateProfile()."

grep -q "useEffect(() =>" "$SETTINGS"
echo "✓ AccountSection synchronizes profile state."

if grep -q "useState('Ibrahim Shaik')" "$SETTINGS"; then
  echo "✗ Hard-coded name still present."
  exit 1
fi

if grep -q "useState('ibrahimshaik@gmail.com')" "$SETTINGS"; then
  echo "✗ Hard-coded email still present."
  exit 1
fi

echo "✓ Hard-coded profile identity removed."

# ==============================================================
# 7. TypeScript / production build
# ==============================================================

echo
echo "============================================================"
echo "7. Production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Production build passed."

# ==============================================================
# 8. Final source excerpts
# ==============================================================

echo
echo "============================================================"
echo "8. Final verification"
echo "============================================================"

echo
echo "AuthContext updateProfile:"
grep -n -A22 -B5 \
  "const updateProfile = useCallback" \
  "$AUTH_CONTEXT"

echo
echo "AccountSection saveProfile:"
grep -n -A35 -B5 \
  "const saveProfile = async" \
  "$SETTINGS"

echo
echo "============================================================"
echo "PROFILE PERSISTENCE FIX v4 COMPLETE"
echo "============================================================"
echo
echo "Actual flow:"
echo
echo "  AccountSection"
echo "      ↓"
echo "  updateProfile(trimmedName)"
echo "      ↓"
echo "  PATCH /api/auth/me"
echo "      ↓"
echo "  users.full_name"
echo "      ↓"
echo "  updated AuthUser"
echo "      ↓"
echo "  AuthContext.setUser()"
echo "      ↓"
echo "  AccountSection updates"
echo
echo "After browser refresh:"
echo
echo "  GET /api/auth/me"
echo "      ↓"
echo "  users.full_name"
echo "      ↓"
echo "  AuthContext.user"
echo "      ↓"
echo "  AccountSection"
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "============================================================"
