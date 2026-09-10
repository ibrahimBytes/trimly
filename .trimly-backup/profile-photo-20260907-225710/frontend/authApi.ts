import { apiFetch } from '@/api/apiClient';

// ================================================================
// AUTH TYPES
// ================================================================

export interface AuthUser {
  id: number;
  email: string;
  fullName: string | null;
  createdAt?: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: number;
  email: string;
}

/**
 * Returned when the account does not have 2FA enabled.
 */
export interface LoginSuccessResponse
  extends AuthResponse {
  requiresTwoFactor?: false;
}

/**
 * Returned when the password is correct but
 * the account requires a second authentication factor.
 *
 * IMPORTANT:
 * challengeToken is NOT an application access token.
 * It must never be persisted as normal authentication.
 */
export interface TwoFactorChallengeResponse {
  requiresTwoFactor: true;
  challengeToken: string;
}

export type LoginResponse =
  | LoginSuccessResponse
  | TwoFactorChallengeResponse;

// ================================================================
// TWO-FACTOR TYPES
// ================================================================

/**
 * Current 2FA state for the authenticated user.
 */
export interface TwoFactorStatus {
  enabled: boolean;
}

/**
 * Response from the initial 2FA setup request.
 *
 * The backend creates a pending secret here.
 * 2FA is not enabled until setup verification succeeds.
 */
export interface TwoFactorSetupResponse {
  secret: string;
  otpauthUri: string;
}

/**
 * Returned after successfully verifying the
 * first TOTP code during enrollment.
 */
export interface TwoFactorSetupVerifyResponse {
  enabled: boolean;
  recoveryCodes: string[];
}

// ================================================================
// REGISTRATION
// ================================================================

/**
 * Register a new account.
 */
export async function register(
  email: string,
  password: string,
): Promise<AuthResponse> {
  return apiFetch<AuthResponse>(
    '/api/auth/register',
    {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
      }),
      skipAuth: true,
    },
  );
}

// ================================================================
// LOGIN
// ================================================================

/**
 * Authenticate with email + password.
 *
 * Possible results:
 *
 * 1. Normal access token:
 *    {
 *      token,
 *      tokenType,
 *      userId,
 *      email
 *    }
 *
 * 2. 2FA challenge:
 *    {
 *      requiresTwoFactor: true,
 *      challengeToken
 *    }
 *
 * The challenge token must NOT be stored as the
 * normal Trimly authentication token.
 */
export async function login(
  email: string,
  password: string,
): Promise<LoginResponse> {
  return apiFetch<LoginResponse>(
    '/api/auth/login',
    {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
      }),
      skipAuth: true,
    },
  );
}

// ================================================================
// TWO-FACTOR LOGIN
// ================================================================

/**
 * Complete a login that requires 2FA.
 *
 * The challenge token is intentionally sent in the
 * request body instead of the Authorization header.
 *
 * This endpoint is called without normal authentication
 * because the user does not have an application access
 * token yet.
 */
export async function verifyTwoFactorLogin(
  challengeToken: string,
  code: string,
): Promise<AuthResponse> {
  return apiFetch<AuthResponse>(
    '/api/auth/2fa/verify',
    {
      method: 'POST',
      body: JSON.stringify({
        challengeToken,
        code,
      }),
      skipAuth: true,
    },
  );
}

// ================================================================
// TWO-FACTOR SETUP
// ================================================================

/**
 * Start 2FA enrollment for the currently
 * authenticated user.
 *
 * This creates a pending TOTP secret.
 * It does NOT enable 2FA yet.
 */
export async function setupTwoFactor(): Promise<TwoFactorSetupResponse> {
  return apiFetch<TwoFactorSetupResponse>(
    '/api/auth/2fa/setup',
    {
      method: 'POST',
    },
  );
}

/**
 * Verify the first authenticator code during
 * 2FA enrollment.
 *
 * Only after this succeeds should the account
 * become 2FA-enabled.
 */
export async function verifyTwoFactorSetup(
  code: string,
): Promise<TwoFactorSetupVerifyResponse> {
  return apiFetch<TwoFactorSetupVerifyResponse>(
    '/api/auth/2fa/setup/verify',
    {
      method: 'POST',
      body: JSON.stringify({
        code,
      }),
    },
  );
}

// ================================================================
// TWO-FACTOR STATUS
// ================================================================

/**
 * Get the current 2FA status.
 */
export async function getTwoFactorStatus(): Promise<TwoFactorStatus> {
  return apiFetch<TwoFactorStatus>(
    '/api/auth/2fa/status',
  );
}

// ================================================================
// TWO-FACTOR DISABLE
// ================================================================

/**
 * Disable 2FA.
 *
 * The backend requires both the current password
 * and a valid authenticator code.
 */
export async function disableTwoFactor(
  currentPassword: string,
  code: string,
): Promise<void> {
  await apiFetch<void>(
    '/api/auth/2fa/disable',
    {
      method: 'POST',
      body: JSON.stringify({
        password: currentPassword,
        code,
      }),
    },
  );
}

// ================================================================
// CURRENT USER
// ================================================================

/**
 * Get the currently authenticated user.
 *
 * The token parameter is retained for compatibility
 * with AuthContext. apiFetch reads the persisted access
 * token itself.
 */
export async function getCurrentUser(
  token?: string,
): Promise<AuthUser> {
  void token;

  return apiFetch<AuthUser>(
    '/api/auth/me',
  );
}

// ================================================================
// PROFILE
// ================================================================

/**
 * Update the authenticated user's profile.
 */
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

// ================================================================
// PASSWORD
// ================================================================

/**
 * Change the authenticated user's password.
 */
export async function changePassword(
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

// ================================================================
// LOGOUT
// ================================================================

/**
 * Sign out the current authenticated session.
 */
export async function logout(): Promise<void> {
  await apiFetch<void>(
    '/api/auth/logout',
    {
      method: 'POST',
    },
  );
} 