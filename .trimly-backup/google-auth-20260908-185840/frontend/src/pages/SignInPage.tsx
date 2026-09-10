import {
  FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  Link,
  useLocation,
  useNavigate,
} from 'react-router-dom';

import {
  ArrowLeft,
  ArrowRight,
  ShieldCheck,
} from 'lucide-react';

import { ApiError } from '@/api/apiClient';
import { useAuth } from '@/auth/AuthContext';

interface LocationState {
  from?: string;
}

type TwoFactorMode =
  | 'totp'
  | 'recovery';

export function SignInPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const {
    signIn,
    verifyTwoFactor,
    cancelTwoFactor,
    requiresTwoFactor,
  } = useAuth();

  const [email, setEmail] =
    useState('');

  const [password, setPassword] =
    useState('');

  const [code, setCode] =
    useState('');

  const [twoFactorMode, setTwoFactorMode] =
    useState<TwoFactorMode>('totp');

  const [error, setError] =
    useState<string | null>(null);

  const [isSubmitting, setIsSubmitting] =
    useState(false);

  const codeInputRef =
    useRef<HTMLInputElement>(null);

  const state =
    location.state as
      | LocationState
      | null;

  /*
   * ============================================================
   * FOCUS 2FA INPUT
   * ============================================================
   */

  useEffect(() => {
    if (
      requiresTwoFactor &&
      !isSubmitting
    ) {
      codeInputRef.current?.focus();
    }
  }, [
    requiresTwoFactor,
    isSubmitting,
  ]);

  /*
   * ============================================================
   * DESTINATION
   * ============================================================
   */

  function getDestination(): string {
    return state?.from &&
      state.from !== '/sign-in'
      ? state.from
      : '/';
  }

  /*
   * ============================================================
   * PASSWORD LOGIN
   * ============================================================
   */

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    /*
     * If the password phase has already succeeded,
     * this form should not submit again.
     */
    if (requiresTwoFactor) {
      await handleTwoFactorSubmit(
        event,
      );

      return;
    }

    const normalizedEmail =
      email.trim().toLowerCase();

    if (!normalizedEmail) {
      setError(
        'Enter your email address.',
      );

      return;
    }

    if (!password) {
      setError(
        'Enter your password.',
      );

      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      const result = await signIn(
        normalizedEmail,
        password,
      );

      /*
       * signIn() explicitly tells us whether the
       * password phase created a 2FA challenge.
       *
       * Do NOT rely on requiresTwoFactor here:
       * React state updates are asynchronous and the
       * value captured by this render may still be false.
       */
      if (result.requiresTwoFactor) {
        return;
      }

      navigate(
        getDestination(),
        {
          replace: true,
        },
      );
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.status === 401
      ) {
        setError(
          'The email or password is incorrect.',
        );
      } else if (
        err instanceof Error
      ) {
        setError(err.message);
      } else {
        setError(
          'Unable to sign in. Please try again.',
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  /*
   * ============================================================
   * 2FA VERIFICATION
   * ============================================================
   */

  async function handleTwoFactorSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    const normalizedCode =
      code.trim();

    if (!normalizedCode) {
      setError(
        twoFactorMode === 'totp'
          ? 'Enter your authentication code.'
          : 'Enter a recovery code.',
      );

      return;
    }

    if (
      twoFactorMode === 'totp' &&
      !/^\d{6}$/.test(
        normalizedCode,
      )
    ) {
      setError(
        'Enter the 6-digit authentication code.',
      );

      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      await verifyTwoFactor(
        normalizedCode,
      );

      /*
       * verifyTwoFactor() only resolves after
       * the backend returns a real application
       * access token.
       */
      navigate(
        getDestination(),
        {
          replace: true,
        },
      );
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.status === 401
      ) {
        setError(
          twoFactorMode === 'totp'
            ? 'The authentication code is incorrect or has expired.'
            : 'The recovery code is invalid or has already been used.',
        );
      } else if (
        err instanceof ApiError &&
        err.status === 400
      ) {
        setError(
          err.message,
        );
      } else if (
        err instanceof Error
      ) {
        setError(err.message);
      } else {
        setError(
          'Unable to verify your code. Please try again.',
        );
      }

      setCode('');
    } finally {
      setIsSubmitting(false);
    }
  }

  /*
   * ============================================================
   * CANCEL 2FA
   * ============================================================
   */

  function handleBackToSignIn() {
    if (isSubmitting) {
      return;
    }

    cancelTwoFactor();

    setCode('');
    setError(null);
    setTwoFactorMode('totp');
  }

  /*
   * ============================================================
   * 2FA SCREEN
   * ============================================================
   */

  if (requiresTwoFactor) {
    return (
      <main
        className="
          min-h-screen
          bg-[#F5F5F7]
          px-4
          py-8
          sm:px-6
        "
      >
        <div
          className="
            mx-auto
            flex
            min-h-[calc(100vh-4rem)]
            w-full
            max-w-[420px]
            flex-col
            justify-center
          "
        >
          {/* Brand */}
          <div className="mb-8 text-center">
            <Link
              to="/"
              className="
                inline-block
                text-[21px]
                font-semibold
                tracking-[-0.035em]
                text-[#1D1D1F]
                transition-opacity
                hover:opacity-70
              "
            >
              Trimly
            </Link>
          </div>

          {/* 2FA surface */}
          <div
            className="
              rounded-2xl
              border
              border-[#D2D2D7]
              bg-white
              px-6
              py-7
              shadow-[0_4px_18px_rgba(0,0,0,0.045)]
              sm:px-8
              sm:py-8
            "
          >
            {/* Icon */}
            <div
              className="
                mb-5
                flex
                h-10
                w-10
                items-center
                justify-center
                rounded-xl
                bg-[#167A45]/10
                text-[#167A45]
              "
              aria-hidden="true"
            >
              <ShieldCheck
                className="h-5 w-5"
                strokeWidth={2}
              />
            </div>

            {/* Heading */}
            <div>
              <h1
                className="
                  text-[25px]
                  font-semibold
                  leading-tight
                  tracking-[-0.025em]
                  text-[#1D1D1F]
                "
              >
                Verify your identity
              </h1>

              <p
                className="
                  mt-2
                  text-[13px]
                  leading-5
                  text-[#6E6E73]
                "
              >
                {twoFactorMode === 'totp'
                  ? 'Enter the 6-digit code from your authenticator app.'
                  : 'Enter one of your unused recovery codes.'}
              </p>
            </div>

            {/* Account */}
            <div
              className="
                mt-5
                rounded-lg
                bg-[#F5F5F7]
                px-3.5
                py-3
              "
            >
              <p
                className="
                  text-[11px]
                  font-medium
                  uppercase
                  tracking-[0.04em]
                  text-[#86868B]
                "
              >
                Signing in as
              </p>

              <p
                className="
                  mt-0.5
                  truncate
                  text-[13px]
                  text-[#1D1D1F]
                "
              >
                {email.trim().toLowerCase()}
              </p>
            </div>

            {/* 2FA Form */}
            <form
              onSubmit={
                handleTwoFactorSubmit
              }
              noValidate
              className="mt-6 space-y-5"
            >
              {/* Error */}
              {error && (
                <div
                  role="alert"
                  className="
                    rounded-lg
                    border
                    border-[#F0C7C7]
                    bg-[#FFF5F5]
                    px-3.5
                    py-3
                    text-[12px]
                    leading-5
                    text-[#A33A3A]
                  "
                >
                  {error}
                </div>
              )}

              {/* Code */}
              <div>
                <label
                  htmlFor="two-factor-code"
                  className="
                    mb-1.5
                    block
                    text-[12px]
                    font-medium
                    text-[#424245]
                  "
                >
                  {twoFactorMode ===
                  'totp'
                    ? 'Authentication code'
                    : 'Recovery code'}
                </label>

                <input
                  ref={codeInputRef}
                  id="two-factor-code"
                  name="twoFactorCode"
                  type={
                    twoFactorMode ===
                    'totp'
                      ? 'text'
                      : 'text'
                  }
                  inputMode={
                    twoFactorMode ===
                    'totp'
                      ? 'numeric'
                      : 'text'
                  }
                  autoComplete="one-time-code"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  maxLength={
                    twoFactorMode ===
                    'totp'
                      ? 6
                      : 64
                  }
                  value={code}
                  onChange={(event) => {
                    const value =
                      event.target.value;

                    if (
                      twoFactorMode ===
                      'totp'
                    ) {
                      setCode(
                        value
                          .replace(
                            /\D/g,
                            '',
                          )
                          .slice(0, 6),
                      );
                    } else {
                      setCode(
                        value
                          .trim()
                          .toUpperCase(),
                      );
                    }

                    if (error) {
                      setError(null);
                    }
                  }}
                  disabled={isSubmitting}
                  placeholder={
                    twoFactorMode ===
                    'totp'
                      ? '000000'
                      : 'XXXX-XXXX-XXXX'
                  }
                  className="
                    h-12
                    w-full
                    rounded-lg
                    border
                    border-[#D2D2D7]
                    bg-white
                    px-3
                    text-center
                    text-[16px]
                    font-medium
                    tracking-[0.18em]
                    text-[#1D1D1F]
                    outline-none
                    placeholder:text-[#A1A1A6]
                    placeholder:tracking-[0.18em]
                    transition
                    focus:border-[#167A45]
                    focus:ring-2
                    focus:ring-[#167A45]/10
                    disabled:cursor-not-allowed
                    disabled:bg-[#F5F5F7]
                    disabled:text-[#86868B]
                  "
                />
              </div>

              {/* Submit */}
              <button
                type="submit"
                disabled={
                  isSubmitting ||
                  !code.trim()
                }
                className="
                  flex
                  h-11
                  w-full
                  items-center
                  justify-center
                  gap-2
                  rounded-lg
                  bg-[#167A45]
                  px-4
                  text-[13px]
                  font-semibold
                  text-white
                  shadow-[0_1px_2px_rgba(0,0,0,0.08)]
                  transition
                  hover:bg-[#12683B]
                  active:bg-[#0F5A32]
                  disabled:cursor-not-allowed
                  disabled:opacity-55
                "
              >
                {isSubmitting
                  ? 'Verifying…'
                  : 'Verify and sign in'}

                {!isSubmitting && (
                  <ArrowRight
                    className="h-3.5 w-3.5"
                    strokeWidth={2}
                    aria-hidden="true"
                  />
                )}
              </button>
            </form>

            {/* Recovery option */}
            <div
              className="
                mt-5
                text-center
              "
            >
              <button
                type="button"
                disabled={isSubmitting}
                onClick={() => {
                  setTwoFactorMode(
                    (current) =>
                      current ===
                      'totp'
                        ? 'recovery'
                        : 'totp',
                  );

                  setCode('');
                  setError(null);
                }}
                className="
                  text-[12px]
                  font-medium
                  text-[#167A45]
                  transition-colors
                  hover:text-[#12683B]
                  hover:underline
                  underline-offset-2
                  disabled:cursor-not-allowed
                  disabled:opacity-50
                "
              >
                {twoFactorMode ===
                'totp'
                  ? 'Use a recovery code instead'
                  : 'Use authenticator code instead'}
              </button>
            </div>

            {/* Back */}
            <div
              className="
                mt-5
                border-t
                border-[#E8E8ED]
                pt-5
              "
            >
              <button
                type="button"
                disabled={isSubmitting}
                onClick={
                  handleBackToSignIn
                }
                className="
                  mx-auto
                  flex
                  items-center
                  gap-1.5
                  text-[12px]
                  font-medium
                  text-[#6E6E73]
                  transition-colors
                  hover:text-[#1D1D1F]
                  disabled:cursor-not-allowed
                  disabled:opacity-50
                "
              >
                <ArrowLeft
                  className="h-3.5 w-3.5"
                  strokeWidth={2}
                  aria-hidden="true"
                />

                Back to sign in
              </button>
            </div>
          </div>

          {/* Footer */}
          <p
            className="
              mt-6
              text-center
              text-[10px]
              text-[#A1A1A6]
            "
          >
            Simple links. Clear analytics.
          </p>
        </div>
      </main>
    );
  }

  /*
   * ============================================================
   * NORMAL SIGN-IN SCREEN
   * ============================================================
   */

  return (
    <main
      className="
        min-h-screen
        bg-[#F5F5F7]
        px-4
        py-8
        sm:px-6
      "
    >
      <div
        className="
          mx-auto
          flex
          min-h-[calc(100vh-4rem)]
          w-full
          max-w-[420px]
          flex-col
          justify-center
        "
      >
        {/* Brand */}
        <div className="mb-8 text-center">
          <Link
            to="/"
            className="
              inline-block
              text-[21px]
              font-semibold
              tracking-[-0.035em]
              text-[#1D1D1F]
              transition-opacity
              hover:opacity-70
            "
          >
            Trimly
          </Link>
        </div>

        {/* Sign-in surface */}
        <div
          className="
            rounded-2xl
            border
            border-[#D2D2D7]
            bg-white
            px-6
            py-7
            shadow-[0_4px_18px_rgba(0,0,0,0.045)]
            sm:px-8
            sm:py-8
          "
        >
          {/* Heading */}
          <div>
            <h1
              className="
                text-[25px]
                font-semibold
                leading-tight
                tracking-[-0.025em]
                text-[#1D1D1F]
              "
            >
              Welcome back
            </h1>

            <p
              className="
                mt-2
                text-[13px]
                leading-5
                text-[#6E6E73]
              "
            >
              Sign in to manage your links and
              view analytics.
            </p>
          </div>

          {/* Form */}
          <form
            onSubmit={handleSubmit}
            noValidate
            className="mt-7 space-y-5"
          >
            {/* Error */}
            {error && (
              <div
                role="alert"
                className="
                  rounded-lg
                  border
                  border-[#F0C7C7]
                  bg-[#FFF5F5]
                  px-3.5
                  py-3
                  text-[12px]
                  leading-5
                  text-[#A33A3A]
                "
              >
                {error}
              </div>
            )}

            {/* Email */}
            <div>
              <label
                htmlFor="email"
                className="
                  mb-1.5
                  block
                  text-[12px]
                  font-medium
                  text-[#424245]
                "
              >
                Email
              </label>

              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) =>
                  setEmail(
                    event.target.value,
                  )
                }
                disabled={isSubmitting}
                placeholder="you@example.com"
                className="
                  h-11
                  w-full
                  rounded-lg
                  border
                  border-[#D2D2D7]
                  bg-white
                  px-3
                  text-[13px]
                  text-[#1D1D1F]
                  outline-none
                  placeholder:text-[#A1A1A6]
                  transition
                  focus:border-[#167A45]
                  focus:ring-2
                  focus:ring-[#167A45]/10
                  disabled:cursor-not-allowed
                  disabled:bg-[#F5F5F7]
                  disabled:text-[#86868B]
                "
              />
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="password"
                className="
                  mb-1.5
                  block
                  text-[12px]
                  font-medium
                  text-[#424245]
                "
              >
                Password
              </label>

              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) =>
                  setPassword(
                    event.target.value,
                  )
                }
                disabled={isSubmitting}
                placeholder="Enter your password"
                className="
                  h-11
                  w-full
                  rounded-lg
                  border
                  border-[#D2D2D7]
                  bg-white
                  px-3
                  text-[13px]
                  text-[#1D1D1F]
                  outline-none
                  placeholder:text-[#A1A1A6]
                  transition
                  focus:border-[#167A45]
                  focus:ring-2
                  focus:ring-[#167A45]/10
                  disabled:cursor-not-allowed
                  disabled:bg-[#F5F5F7]
                  disabled:text-[#86868B]
                "
              />
            </div>

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting}
              className="
                flex
                h-11
                w-full
                items-center
                justify-center
                gap-2
                rounded-lg
                bg-[#167A45]
                px-4
                text-[13px]
                font-semibold
                text-white
                shadow-[0_1px_2px_rgba(0,0,0,0.08)]
                transition
                hover:bg-[#12683B]
                active:bg-[#0F5A32]
                disabled:cursor-not-allowed
                disabled:opacity-55
              "
            >
              {isSubmitting
                ? 'Signing in…'
                : 'Sign in'}

              {!isSubmitting && (
                <ArrowRight
                  className="h-3.5 w-3.5"
                  strokeWidth={2}
                  aria-hidden="true"
                />
              )}
            </button>
          </form>

          {/* Sign up */}
          <div
            className="
              mt-6
              border-t
              border-[#E8E8ED]
              pt-5
              text-center
            "
          >
            <p className="text-[12px] text-[#86868B]">
              Don't have an account?{' '}

              <Link
                to="/sign-up"
                className="
                  font-medium
                  text-[#167A45]
                  transition-colors
                  hover:text-[#12683B]
                  hover:underline
                  underline-offset-2
                "
              >
                Create one
              </Link>
            </p>
          </div>
        </div>

        {/* Footer */}
        <p
          className="
            mt-6
            text-center
            text-[10px]
            text-[#A1A1A6]
          "
        >
          Simple links. Clear analytics.
        </p>
      </div>
    </main>
  );
} 