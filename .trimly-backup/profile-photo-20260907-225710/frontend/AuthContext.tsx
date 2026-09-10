import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import {
  getCurrentUser,
  login,
  logout,
  register,
  updateProfile as updateProfileApi,
  verifyTwoFactorLogin,
  type AuthUser,
  type LoginResponse,
} from '@/auth/authApi';

import {
  ApiError,
  clearStoredAuth,
} from '@/api/apiClient';

const AUTH_STORAGE_KEY =
  'trimly.auth';

interface StoredAuth {
  token: string;
  tokenType: string;
  userId: number;
  email: string;
}

export interface TwoFactorChallenge {
  challengeToken: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  /**
   * True when password authentication succeeded
   * but the account still requires 2FA verification.
   */
  requiresTwoFactor: boolean;

  /**
   * Temporary 2FA login challenge.
   *
   * This is deliberately kept outside localStorage.
   */
  twoFactorChallenge: TwoFactorChallenge | null;

  signIn: (
    email: string,
    password: string,
  ) => Promise<{
    requiresTwoFactor: boolean;
  }>;

  verifyTwoFactor: (
    code: string,
  ) => Promise<void>;

  cancelTwoFactor: () => void;

  signUp: (
    email: string,
    password: string,
  ) => Promise<void>;

  signOut: () => Promise<void>;

  updateProfile: (
    fullName: string,
  ) => Promise<AuthUser>;
}

const AuthContext =
  createContext<AuthContextValue | undefined>(
    undefined,
  );

/*
 * ============================================================
 * LOCAL AUTH STORAGE
 * ============================================================
 */

function readStoredAuth(): StoredAuth | null {
  try {
    const raw =
      localStorage.getItem(
        AUTH_STORAGE_KEY,
      );

    if (!raw) {
      return null;
    }

    const parsed =
      JSON.parse(raw) as Partial<StoredAuth>;

    if (
      typeof parsed.token !== 'string' ||
      !parsed.token ||
      typeof parsed.email !== 'string' ||
      !parsed.email ||
      typeof parsed.userId !== 'number' ||
      !Number.isFinite(parsed.userId)
    ) {
      clearStoredAuth();

      return null;
    }

    return {
      token: parsed.token,
      tokenType:
        typeof parsed.tokenType === 'string' &&
        parsed.tokenType
          ? parsed.tokenType
          : 'Bearer',
      userId: parsed.userId,
      email: parsed.email,
    };
  } catch {
    clearStoredAuth();

    return null;
  }
}

function storeAuth(
  auth: StoredAuth,
): void {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify(auth),
  );
}

/*
 * ============================================================
 * PROVIDER
 * ============================================================
 */

export function AuthProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [auth, setAuth] =
    useState<StoredAuth | null>(null);

  const [user, setUser] =
    useState<AuthUser | null>(null);

  const [isLoading, setIsLoading] =
    useState(true);

  /*
   * The 2FA challenge is intentionally held only
   * in React memory.
   *
   * It is NOT written to localStorage.
   */
  const [
    twoFactorChallenge,
    setTwoFactorChallenge,
  ] =
    useState<TwoFactorChallenge | null>(
      null,
    );

  /*
   * ============================================================
   * RESTORE SESSION
   * ============================================================
   */

  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      const storedAuth =
        readStoredAuth();

      if (!storedAuth) {
        if (!cancelled) {
          setIsLoading(false);
        }

        return;
      }

      setAuth(storedAuth);

      try {
        const currentUser =
          await getCurrentUser(
            storedAuth.token,
          );

        if (cancelled) {
          return;
        }

        setUser(currentUser);
      } catch (error) {
        if (cancelled) {
          return;
        }

        /*
         * apiFetch already handles 401 by clearing
         * stored authentication and dispatching the
         * global unauthorized event.
         *
         * We also clear the local React state here.
         */
        if (
          error instanceof ApiError &&
          error.status === 401
        ) {
          clearStoredAuth();
          setAuth(null);
          setUser(null);
        } else {
          /*
           * If the session cannot be restored,
           * don't leave the UI believing the user
           * is authenticated.
           */
          clearStoredAuth();
          setAuth(null);
          setUser(null);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void restoreSession();

    return () => {
      cancelled = true;
    };
  }, []);

  /*
   * ============================================================
   * GLOBAL UNAUTHORIZED EVENT
   * ============================================================
   */

  useEffect(() => {
    const handleUnauthorized =
      () => {
        clearStoredAuth();

        setAuth(null);
        setUser(null);

        /*
         * A normal session ending must also
         * terminate any outstanding 2FA state.
         */
        setTwoFactorChallenge(null);
      };

    window.addEventListener(
      'trimly:unauthorized',
      handleUnauthorized,
    );

    return () => {
      window.removeEventListener(
        'trimly:unauthorized',
        handleUnauthorized,
      );
    };
  }, []);

  /*
   * ============================================================
   * COMPLETE NORMAL AUTHENTICATION
   * ============================================================
   */

  const completeAuthentication =
    useCallback(
      async (
        response: {
          token: string;
          tokenType: string;
          userId: number;
          email: string;
        },
      ) => {
        const storedAuth: StoredAuth =
          {
            token: response.token,
            tokenType:
              response.tokenType ||
              'Bearer',
            userId:
              response.userId,
            email:
              response.email,
          };

        storeAuth(storedAuth);

        setAuth(storedAuth);

        const currentUser =
          await getCurrentUser(
            response.token,
          );

        setUser(currentUser);

        /*
         * Once a real access token has been
         * obtained, there must be no remaining
         * 2FA challenge.
         */
        setTwoFactorChallenge(
          null,
        );
      },
      [],
    );

  /*
   * ============================================================
   * SIGN IN
   * ============================================================
   */

  const signIn = useCallback(
    async (
      email: string,
      password: string,
    ) => {
      /*
       * Make sure an old challenge cannot
       * survive into a new login attempt.
       */
      setTwoFactorChallenge(null);

      const response: LoginResponse =
        await login(
          email,
          password,
        );

      /*
       * --------------------------------------------------------
       * 2FA REQUIRED
       * --------------------------------------------------------
       */

      if (
        response.requiresTwoFactor ===
        true
      ) {
        /*
         * CRITICAL:
         *
         * Do NOT call storeAuth().
         *
         * challengeToken is not an access token.
         */
        setAuth(null);
        setUser(null);

        setTwoFactorChallenge({
          challengeToken:
            response.challengeToken,
        });

        return {
          requiresTwoFactor: true,
        };
      }

      /*
       * --------------------------------------------------------
       * NORMAL LOGIN
       * --------------------------------------------------------
       */

      await completeAuthentication(
        response,
      );

      return {
        requiresTwoFactor: false,
      };
    },
    [
      completeAuthentication,
    ],
  );

  /*
   * ============================================================
   * VERIFY 2FA DURING LOGIN
   * ============================================================
   */

  const verifyTwoFactor =
    useCallback(
      async (
        code: string,
      ) => {
        if (
          !twoFactorChallenge
        ) {
          throw new ApiError(
            'Your two-factor authentication session has expired. Please sign in again.',
            400,
          );
        }

        const response =
          await verifyTwoFactorLogin(
            twoFactorChallenge.challengeToken,
            code,
          );

        /*
         * Only here do we receive and persist
         * the real application access token.
         */
        await completeAuthentication(
          response,
        );
      },
      [
        twoFactorChallenge,
        completeAuthentication,
      ],
    );

  /*
   * ============================================================
   * CANCEL 2FA
   * ============================================================
   */

  const cancelTwoFactor =
    useCallback(() => {
      /*
       * There is no authenticated session
       * at this point.
       *
       * Simply discard the in-memory
       * challenge and return to login.
       */
      setTwoFactorChallenge(null);
    }, []);

  /*
   * ============================================================
   * SIGN UP
   * ============================================================
   */

  const signUp = useCallback(
    async (
      email: string,
      password: string,
    ) => {
      const response =
        await register(
          email,
          password,
        );

      /*
       * Registration currently returns
       * a normal access token.
       */
      await completeAuthentication(
        response,
      );
    },
    [
      completeAuthentication,
    ],
  );

  /*
   * ============================================================
   * UPDATE PROFILE
   * ============================================================
   */

  const updateProfile =
    useCallback(
      async (
        fullName: string,
      ): Promise<AuthUser> => {
        const updatedUser =
          await updateProfileApi(
            fullName,
          );

        setUser(updatedUser);

        return updatedUser;
      },
      [],
    );

  /*
   * ============================================================
   * SIGN OUT
   * ============================================================
   */

  const signOut =
    useCallback(
      async () => {
        try {
          await logout();
        } catch {
          /*
           * Logout should still clear the local
           * session if the backend is unreachable
           * or the JWT has already expired/revoked.
           */
        } finally {
          clearStoredAuth();

          setAuth(null);
          setUser(null);
          setTwoFactorChallenge(null);
        }
      },
      [],
    );

  /*
   * ============================================================
   * CONTEXT VALUE
   * ============================================================
   */

  const value =
    useMemo<AuthContextValue>(
      () => ({
        user,
        token:
          auth?.token ?? null,

        isAuthenticated:
          auth !== null &&
          user !== null,

        isLoading,

        requiresTwoFactor:
          twoFactorChallenge !== null,

        twoFactorChallenge,

        signIn,
        verifyTwoFactor,
        cancelTwoFactor,

        signUp,
        signOut,
        updateProfile,
      }),
      [
        auth,
        user,
        isLoading,
        twoFactorChallenge,
        signIn,
        verifyTwoFactor,
        cancelTwoFactor,
        signUp,
        signOut,
        updateProfile,
      ],
    );

  return (
    <AuthContext.Provider
      value={value}
    >
      {children}
    </AuthContext.Provider>
  );
}

/*
 * ============================================================
 * HOOK
 * ============================================================
 */

export function useAuth() {
  const context =
    useContext(AuthContext);

  if (!context) {
    throw new Error(
      'useAuth must be used inside AuthProvider',
    );
  }

  return context;
}  