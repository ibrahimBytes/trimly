import {
  FormEvent,
  useState,
} from 'react';

import {
  Link,
  useNavigate,
} from 'react-router-dom';

import { ArrowRight } from 'lucide-react';

import { ApiError } from '@/api/apiClient';
import { useAuth } from '@/auth/AuthContext';

export function SignUpPage() {
  const navigate = useNavigate();

  const { signUp } = useAuth();

  const [email, setEmail] =
    useState('');

  const [password, setPassword] =
    useState('');

  const [confirmPassword, setConfirmPassword] =
    useState('');

  const [error, setError] =
    useState<string | null>(null);

  const [isSubmitting, setIsSubmitting] =
    useState(false);

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (isSubmitting) {
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
        'Create a password.',
      );

      return;
    }

    if (password.length < 8) {
      setError(
        'Your password must be at least 8 characters.',
      );

      return;
    }

    if (
      password !== confirmPassword
    ) {
      setError(
        'The passwords do not match.',
      );

      return;
    }

    setError(null);
    setIsSubmitting(true);

    try {
      await signUp(
        normalizedEmail,
        password,
      );

      navigate('/', {
        replace: true,
      });
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.status === 409
      ) {
        setError(
          'An account with this email already exists.',
        );
      } else if (
        err instanceof Error
      ) {
        setError(err.message);
      } else {
        setError(
          'Unable to create your account. Please try again.',
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  }

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

        {/* Sign-up surface */}
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
              Create your account
            </h1>

            <p
              className="
                mt-2
                text-[13px]
                leading-5
                text-[#6E6E73]
              "
            >
              Start shortening and managing
              your links with Trimly.
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
                autoComplete="new-password"
                value={password}
                onChange={(event) =>
                  setPassword(
                    event.target.value,
                  )
                }
                disabled={isSubmitting}
                placeholder="At least 8 characters"
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

            {/* Confirm password */}
            <div>
              <label
                htmlFor="confirm-password"
                className="
                  mb-1.5
                  block
                  text-[12px]
                  font-medium
                  text-[#424245]
                "
              >
                Confirm password
              </label>

              <input
                id="confirm-password"
                name="confirm-password"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) =>
                  setConfirmPassword(
                    event.target.value,
                  )
                }
                disabled={isSubmitting}
                placeholder="Repeat your password"
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
                ? 'Creating account…'
                : 'Create account'}

              {!isSubmitting && (
                <ArrowRight
                  className="h-3.5 w-3.5"
                  strokeWidth={2}
                  aria-hidden="true"
                />
              )}
            </button>
          </form>

          {/* Sign in */}
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
              Already have an account?{' '}

              <Link
                to="/sign-in"
                className="
                  font-medium
                  text-[#167A45]
                  transition-colors
                  hover:text-[#12683B]
                  hover:underline
                  underline-offset-2
                "
              >
                Sign in
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