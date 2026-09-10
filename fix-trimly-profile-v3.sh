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
echo "TRIMLY — REAL PROFILE NAME PERSISTENCE v3"
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

# ------------------------------------------------------------------
# Preconditions
# ------------------------------------------------------------------

echo "============================================================"
echo "1. Preconditions"
echo "============================================================"

for file in "$AUTH_CONTEXT" "$AUTH_API" "$SETTINGS"; do
  if [[ ! -f "$file" ]]; then
    echo "✗ Missing required file: $file"
    exit 1
  fi
done

if ! grep -q "updateProfile" "$AUTH_API"; then
  echo "✗ authApi.ts does not contain updateProfile()."
  exit 1
fi

if ! grep -q "interface AuthUser" "$AUTH_CONTEXT"; then
  echo "✗ AuthContext.tsx does not contain AuthUser."
  exit 1
fi

if ! grep -q "function AccountSection" "$SETTINGS"; then
  echo "✗ SettingsPage.tsx does not contain AccountSection."
  exit 1
fi

if ! grep -q "const saveProfile" "$SETTINGS"; then
  echo "✗ AccountSection saveProfile() was not found."
  exit 1
fi

echo "✓ Required files exist."
echo "✓ updateProfile() exists."
echo "✓ AuthUser exists."
echo "✓ AccountSection exists."
echo "✓ saveProfile() exists."

# ------------------------------------------------------------------
# Backend verification
# ------------------------------------------------------------------

echo
echo "============================================================"
echo "2. Backend verification"
echo "============================================================"

if grep -Rqs "updateProfile" "$BACKEND/src/main/java"; then
  echo "✓ Backend updateProfile() detected."
else
  echo "✗ Backend updateProfile() not detected."
  exit 1
fi

if grep -Rqs '@PatchMapping("/me")' "$BACKEND/src/main/java"; then
  echo "✓ PATCH /api/auth/me detected."
else
  echo "✗ PATCH /api/auth/me not detected."
  exit 1
fi

if grep -Rqs "ProfileUpdateRequest" "$BACKEND/src/main/java"; then
  echo "✓ ProfileUpdateRequest detected."
else
  echo "✗ ProfileUpdateRequest not detected."
  exit 1
fi

# ------------------------------------------------------------------
# Backup
# ------------------------------------------------------------------

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

# ------------------------------------------------------------------
# Python patch helper
# ------------------------------------------------------------------

python3 - "$AUTH_CONTEXT" "$AUTH_API" "$SETTINGS" <<'PY'
from pathlib import Path
import sys

auth_context_path = Path(sys.argv[1])
auth_api_path = Path(sys.argv[2])
settings_path = Path(sys.argv[3])

auth_context = auth_context_path.read_text()
auth_api = auth_api_path.read_text()
settings = settings_path.read_text()


def require_once(text, old, description):
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"✗ Expected exactly one occurrence of {description}, "
            f"found {count}."
        )


# ================================================================
# AUTH API
# ================================================================

# updateProfile already exists from v2.
# Do not modify it if it is already correct.

expected_update_profile = """export async function updateProfile(
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
}"""

if expected_update_profile not in auth_api:
    raise SystemExit(
        "✗ Existing updateProfile() does not match the expected "
        "implementation. Refusing to overwrite it."
    )

print("✓ authApi.ts updateProfile() verified.")


# ================================================================
# AUTH CONTEXT
# ================================================================

# 1. Import updateProfile.
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

require_once(
    auth_context,
    old,
    "AuthContext authApi import block",
)

auth_context = auth_context.replace(old, new)


# 2. AuthUser already has fullName.
expected_auth_user = """interface AuthUser {
  id: number;
  email: string;
  fullName: string | null;
  createdAt?: string;
}"""

if expected_auth_user not in auth_context:
    raise SystemExit(
        "✗ AuthContext AuthUser is not in the expected state."
    )

print("✓ AuthUser.fullName verified.")


# 3. Fix readStoredAuth() return object.
old = """    return {
      token: parsed.token,
      tokenType:
        typeof parsed.tokenType === 'string'
          ? parsed.tokenType
          : 'Bearer',
      userId: parsed.userId,
      email: parsed.email,
    };"""

new = """    return {
      token: parsed.token,
      tokenType:
        typeof parsed.tokenType === 'string'
          ? parsed.tokenType
          : 'Bearer',
      userId: parsed.userId,
      email: parsed.email,
      fullName:
        typeof parsed.fullName === 'string'
          ? parsed.fullName
          : null,
    };"""

require_once(
    auth_context,
    old,
    "readStoredAuth() return object",
)

auth_context = auth_context.replace(old, new)


# 4. Add updateProfile to AuthContextValue.
old = """  signOut: () => Promise<void>;
}"""

new = """  signOut: () => Promise<void>;

  updateProfile: (
    fullName: string,
  ) => Promise<AuthUser>;
}"""

require_once(
    auth_context,
    old,
    "AuthContextValue signOut declaration",
)

auth_context = auth_context.replace(old, new)


# 5. Add fullName to signIn StoredAuth.
old = """      const storedAuth: StoredAuth = {
        token: response.token,
        tokenType:
          response.tokenType || 'Bearer',
        userId: response.userId,
        email: response.email,
      };

      storeAuth(storedAuth);

      setAuth(storedAuth);"""

new = """      const storedAuth: StoredAuth = {
        token: response.token,
        tokenType:
          response.tokenType || 'Bearer',
        userId: response.userId,
        email: response.email,
        fullName: null,
      };

      storeAuth(storedAuth);

      setAuth(storedAuth);"""

require_once(
    auth_context,
    old,
    "signIn StoredAuth object",
)

auth_context = auth_context.replace(old, new)


# 6. Add fullName to signUp StoredAuth.
old = """      const storedAuth: StoredAuth = {
        token: response.token,
        tokenType:
          response.tokenType || 'Bearer',
        userId: response.userId,
        email: response.email,
      };

      storeAuth(storedAuth);

      setAuth(storedAuth);"""

new = """      const storedAuth: StoredAuth = {
        token: response.token,
        tokenType:
          response.tokenType || 'Bearer',
        userId: response.userId,
        email: response.email,
        fullName: null,
      };

      storeAuth(storedAuth);

      setAuth(storedAuth);"""

require_once(
    auth_context,
    old,
    "signUp StoredAuth object",
)

auth_context = auth_context.replace(old, new)


# 7. Update signIn/signUp after /me so localStorage gets
# canonical fullName too.
old = """      const currentUser =
        await getCurrentUser(
          response.token,
        );

      setUser(currentUser);
    },
    [],
  );

  /*
   * ============================================================
   * SIGN UP"""

new = """      const currentUser =
        await getCurrentUser(
          response.token,
        );

      setUser(currentUser);

      const canonicalAuth: StoredAuth = {
        ...storedAuth,
        fullName: currentUser.fullName,
      };

      storeAuth(canonicalAuth);
      setAuth(canonicalAuth);
    },
    [],
  );

  /*
   * ============================================================
   * SIGN UP"""

require_once(
    auth_context,
    old,
    "signIn canonical user block",
)

auth_context = auth_context.replace(old, new)


old = """      const currentUser =
        await getCurrentUser(
          response.token,
        );

      setUser(currentUser);
    },
    [],
  );

  /*
   * ============================================================
   * SIGN OUT"""

new = """      const currentUser =
        await getCurrentUser(
          response.token,
        );

      setUser(currentUser);

      const canonicalAuth: StoredAuth = {
        ...storedAuth,
        fullName: currentUser.fullName,
      };

      storeAuth(canonicalAuth);
      setAuth(canonicalAuth);
    },
    [],
  );

  /*
   * ============================================================
   * SIGN OUT"""

require_once(
    auth_context,
    old,
    "signUp canonical user block",
)

auth_context = auth_context.replace(old, new)


# 8. Add updateProfile callback immediately before SIGN OUT.
marker = """  /*
   * ============================================================
   * SIGN OUT
   * ============================================================
   */"""

if auth_context.count(marker) != 1:
    raise SystemExit(
        "✗ Could not uniquely locate SIGN OUT section."
    )

update_profile_block = """  /*
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

      if (auth) {
        const updatedAuth: StoredAuth = {
          ...auth,
          email: updatedUser.email,
          fullName: updatedUser.fullName,
        };

        storeAuth(updatedAuth);
        setAuth(updatedAuth);
      }

      return updatedUser;
    },
    [auth],
  );

"""

auth_context = auth_context.replace(
    marker,
    update_profile_block + marker,
    1,
)


# 9. Expose updateProfile in context value.
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

require_once(
    auth_context,
    old,
    "AuthContext value object",
)

auth_context = auth_context.replace(old, new)

auth_context_path.write_text(auth_context)

print("✓ AuthContext.tsx updated.")


# ================================================================
# SETTINGS PAGE
# ================================================================

# 1. Import useAuth.
old = """import {
  changePassword,
} from '@/auth/authApi';"""

new = """import {
  changePassword,
} from '@/auth/authApi';

import { useAuth } from '@/auth/AuthContext';"""

require_once(
    settings,
    old,
    "SettingsPage auth import block",
)

settings = settings.replace(old, new)


# 2. Replace AccountSection state initialization.
old = """  const [name, setName] =
    useState('Ibrahim Shaik');

  const [email, setEmail] =
    useState('ibrahimshaik@gmail.com');"""

new = """  const { user, updateProfile } = useAuth();

  const [name, setName] =
    useState('');

  const [email, setEmail] =
    useState('');"""

require_once(
    settings,
    old,
    "AccountSection name/email state",
)

settings = settings.replace(old, new)


# 3. Initialize local fields from canonical auth user.
marker = """  const [saved, setSaved] =
    useState(false);

  const fileRef =
    useRef<HTMLInputElement>(null);"""

replacement = """  const [saved, setSaved] =
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

require_once(
    settings,
    marker,
    "AccountSection saved/fileRef block",
)

settings = settings.replace(marker, replacement)


# 4. Replace mock saveProfile with real async API flow.
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
    } catch (error) {
      if (
        error instanceof Error &&
        error.message
      ) {
        notify(error.message);
      } else {
        notify('Unable to update your profile.');
      }
    }
  };"""

require_once(
    settings,
    old,
    "mock saveProfile() implementation",
)

settings = settings.replace(old, new)


# 5. Email is backend-controlled/read-only.
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

require_once(
    settings,
    old,
    "email Field",
)

settings = settings.replace(old, new)


settings_path.write_text(settings)

print("✓ SettingsPage.tsx updated.")
PY

# ------------------------------------------------------------------
# Static verification
# ------------------------------------------------------------------

echo
echo "============================================================"
echo "4. Static verification"
echo "============================================================"

grep -q "updateProfile as updateProfileApi" "$AUTH_CONTEXT"
echo "✓ AuthContext imports updateProfile API."

grep -q "updateProfile: (" "$AUTH_CONTEXT"
echo "✓ AuthContext exposes updateProfile()."

grep -q "const updateProfile = useCallback" "$AUTH_CONTEXT"
echo "✓ AuthContext implements updateProfile()."

grep -q "updateProfile," "$AUTH_CONTEXT"
echo "✓ AuthContext provides updateProfile()."

grep -q "const { user, updateProfile } = useAuth();" "$SETTINGS"
echo "✓ AccountSection consumes authenticated user/profile updater."

grep -q "await updateProfile(trimmedName)" "$SETTINGS"
echo "✓ AccountSection calls real profile API."

grep -q "disabled" "$SETTINGS"
echo "✓ Email field is read-only."

# Make sure the old hard-coded profile is gone.
if grep -q "useState('Ibrahim Shaik')" "$SETTINGS"; then
  echo "✗ Hard-coded profile name still exists."
  exit 1
fi

if grep -q "useState('ibrahimshaik@gmail.com')" "$SETTINGS"; then
  echo "✗ Hard-coded profile email still exists."
  exit 1
fi

echo "✓ Hard-coded profile identity removed."

# ------------------------------------------------------------------
# Build
# ------------------------------------------------------------------

echo
echo "============================================================"
echo "5. Production build"
echo "============================================================"

cd "$FRONTEND"

npm run build

echo
echo "✓ Production build passed."

# ------------------------------------------------------------------
# Final verification
# ------------------------------------------------------------------

echo
echo "============================================================"
echo "6. Final source verification"
echo "============================================================"

echo
echo "AuthContext updateProfile:"
grep -n -A35 -B5 "const updateProfile = useCallback" \
  "$AUTH_CONTEXT"

echo
echo "AccountSection saveProfile:"
grep -n -A45 -B5 "const saveProfile = async" \
  "$SETTINGS"

echo
echo "============================================================"
echo "PROFILE PERSISTENCE FIX COMPLETE"
echo "============================================================"
echo
echo "Flow:"
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
echo "  React state + localStorage"
echo "      ↓"
echo "  GET /api/auth/me after refresh"
echo
echo "Backup:"
echo "  $BACKUP"
echo
echo "============================================================"
