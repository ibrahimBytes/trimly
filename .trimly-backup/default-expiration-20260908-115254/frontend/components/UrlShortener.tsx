import { useRef, useState } from 'react';

import {
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  ExternalLink,
  Share2,
} from 'lucide-react';

import { apiFetch, ApiError } from '@/api/apiClient';

import { ShortLink } from '@/types';

interface Props {
  onLinkCreated: (link: ShortLink) => void;
}

interface URLResponse {
  shortUrl: string;
  shortCode: string;
  longUrl: string;
  expirationTime: string | null;
  createdAt?: string | null;
  active?: boolean;
  clicks?: number;
}

export function UrlShortener({
  onLinkCreated,
}: Props) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const [result, setResult] =
    useState<ShortLink | null>(null);

  const [copied, setCopied] = useState(false);

  const [customOpen, setCustomOpen] =
    useState(false);

  const [customAlias, setCustomAlias] =
    useState('');

  const [expirationDate, setExpirationDate] =
    useState('');

  const inputRef =
    useRef<HTMLInputElement>(null);

  // ============================================================
  // URL VALIDATION
  // ============================================================

  function isValidUrl(value: string): boolean {
    try {
      const normalized =
        value.startsWith('http://') ||
        value.startsWith('https://')
          ? value
          : `https://${value}`;

      const parsed = new URL(normalized);

      return (
        parsed.protocol === 'http:' ||
        parsed.protocol === 'https:'
      );
    } catch {
      return false;
    }
  }

  function normalizeUrl(value: string): string {
    const trimmed = value.trim();

    if (
      trimmed.startsWith('http://') ||
      trimmed.startsWith('https://')
    ) {
      return trimmed;
    }

    return `https://${trimmed}`;
  }

  // ============================================================
  // CUSTOM ALIAS VALIDATION
  // ============================================================

  function isValidCustomAlias(
    value: string,
  ): boolean {
    if (!value) {
      return true;
    }

    return /^[a-zA-Z0-9_-]{1,8}$/.test(
      value,
    );
  }

  // ============================================================
  // CREATE SHORT URL
  // ============================================================

  async function handleShorten() {
    const trimmedUrl = url.trim();
    const trimmedAlias = customAlias.trim();

    // ----------------------------------------------------------
    // URL validation
    // ----------------------------------------------------------

    if (!trimmedUrl) {
      setError('Enter a valid URL.');

      inputRef.current?.focus();

      return;
    }

    if (!isValidUrl(trimmedUrl)) {
      setError('Enter a valid URL.');

      inputRef.current?.focus();

      return;
    }

    // ----------------------------------------------------------
    // Custom alias validation
    // ----------------------------------------------------------

    if (
      !isValidCustomAlias(
        trimmedAlias,
      )
    ) {
      setError(
        'Custom alias can only contain letters, numbers, dashes, and underscores, and must be 8 characters or fewer.',
      );

      return;
    }

    // ----------------------------------------------------------
    // Expiration validation
    // ----------------------------------------------------------

    if (expirationDate) {
      const selectedDate = new Date(
        `${expirationDate}T23:59:59`,
      );

      const now = new Date();

      if (selectedDate <= now) {
        setError(
          'Expiration date must be in the future.',
        );

        return;
      }
    }

    const longUrl =
      normalizeUrl(trimmedUrl);

    // ----------------------------------------------------------
    // Reset request state
    // ----------------------------------------------------------

    setError('');
    setResult(null);
    setCopied(false);
    setLoading(true);

    try {
      // --------------------------------------------------------
      // Spring LocalDateTime format
      // --------------------------------------------------------

      const expirationTime =
        expirationDate
          ? `${expirationDate}T23:59:59`
          : null;

      // --------------------------------------------------------
      // Backend API contract
      //
      // POST /api/urls
      //
      // Authentication is attached automatically by apiFetch().
      // --------------------------------------------------------

      const requestBody = {
        longUrl,
        customAlias:
          trimmedAlias || null,
        expirationTime,
      };

      const data =
        await apiFetch<URLResponse>(
          '/api/urls',
          {
            method: 'POST',
            body: JSON.stringify(
              requestBody,
            ),
          },
        );

      // --------------------------------------------------------
      // Validate backend response
      // --------------------------------------------------------

      if (
        !data ||
        !data.shortCode ||
        !data.shortUrl
      ) {
        console.error(
          'Invalid URL shortening response:',
          data,
        );

        setError(
          'The server returned an invalid short URL.',
        );

        return;
      }

      // --------------------------------------------------------
      // Format created date
      // --------------------------------------------------------

      const createdAt =
        data.createdAt
          ? new Date(
              data.createdAt,
            ).toLocaleDateString(
              'en-US',
              {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
              },
            )
          : new Date().toLocaleDateString(
              'en-US',
              {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
              },
            );

      // --------------------------------------------------------
      // Determine status
      // --------------------------------------------------------

      const isExpired =
        !data.active ||
        (
          data.expirationTime !== null &&
          new Date(
            data.expirationTime,
          ) < new Date()
        );

      // --------------------------------------------------------
      // Backend → frontend model
      // --------------------------------------------------------

      const shortLink: ShortLink = {
        id: data.shortCode,

        alias: data.shortCode,

        destination:
          data.longUrl.replace(
            /^https?:\/\//,
            '',
          ),

        clicks:
          data.clicks ?? 0,

        status:
          isExpired
            ? 'expired'
            : 'active',

        createdAt,

        title:
          trimmedAlias ||
          data.shortCode,

        /*
         * The backend owns the generated short URL.
         * Never reconstruct it on the frontend.
         */
        shortUrl:
          data.shortUrl,
      };

      // --------------------------------------------------------
      // Update UI
      // --------------------------------------------------------

      setResult(shortLink);

      onLinkCreated(shortLink);

      // --------------------------------------------------------
      // Reset form
      // --------------------------------------------------------

      setUrl('');
      setCustomAlias('');
      setExpirationDate('');
      setCustomOpen(false);

    } catch (err) {
      console.error(
        'Failed to create short URL:',
        err,
      );

      // --------------------------------------------------------
      // Authentication failure
      // --------------------------------------------------------

      if (
        err instanceof ApiError &&
        err.status === 401
      ) {
        setError(
          'Your session has expired. Please sign in again.',
        );

        return;
      }

      // --------------------------------------------------------
      // Validation
      // --------------------------------------------------------

      if (
        err instanceof ApiError &&
        err.status === 400
      ) {
        setError(
          err.message ||
            'The URL or custom options are invalid.',
        );

        return;
      }

      // --------------------------------------------------------
      // Alias conflict
      // --------------------------------------------------------

      if (
        err instanceof ApiError &&
        err.status === 409
      ) {
        setError(
          'That custom alias is already in use. Try another one.',
        );

        return;
      }

      // --------------------------------------------------------
      // Rate limit
      // --------------------------------------------------------

      if (
        err instanceof ApiError &&
        err.status === 429
      ) {
        setError(
          'Too many requests. Please wait a moment and try again.',
        );

        return;
      }

      // --------------------------------------------------------
      // Server error
      // --------------------------------------------------------

      if (
        err instanceof ApiError &&
        err.status >= 500
      ) {
        setError(
          'The Trimly server encountered an error. Please try again.',
        );

        return;
      }

      // --------------------------------------------------------
      // Network / unknown error
      // --------------------------------------------------------

      setError(
        'Unable to connect to Trimly. Make sure the backend is running.',
      );

    } finally {
      setLoading(false);
    }
  }

  // ============================================================
  // COPY SHORT URL
  // ============================================================

  async function handleCopy() {
    if (!result) {
      return;
    }

    const shortUrl =
      resultUrl(result);

    if (!shortUrl) {
      setError(
        'Short URL is unavailable.',
      );

      return;
    }

    try {
      await navigator.clipboard.writeText(
        shortUrl,
      );

      setCopied(true);
      setError('');

      window.setTimeout(() => {
        setCopied(false);
      }, 2000);

    } catch (err) {
      console.error(
        'Failed to copy short URL:',
        err,
      );

      setError(
        'Unable to copy the short URL.',
      );
    }
  }

  // ============================================================
  // SHARE SHORT URL
  // ============================================================

  async function handleShare() {
    if (!result) {
      return;
    }

    const shortUrl =
      resultUrl(result);

    if (!shortUrl) {
      setError(
        'Short URL is unavailable.',
      );

      return;
    }

    // ----------------------------------------------------------
    // Native Web Share API
    // ----------------------------------------------------------

    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Trimly short link',
          text: 'Here is a shortened link from Trimly.',
          url: shortUrl,
        });

        return;

      } catch (err) {
        if (
          err instanceof DOMException &&
          err.name === 'AbortError'
        ) {
          return;
        }

        console.error(
          'Failed to share short URL:',
          err,
        );
      }
    }

    // ----------------------------------------------------------
    // Clipboard fallback
    // ----------------------------------------------------------

    await handleCopy();
  }

  // ============================================================
  // RESULT URL
  // ============================================================

  const shortUrl =
    result
      ? resultUrl(result)
      : '';

  // ============================================================
  // RENDER
  // ============================================================

  return (
    <div className="w-full">

      {/* ========================================================
          PRIMARY URL FORM
      ======================================================== */}

      <div
        className="
          overflow-hidden
          rounded-xl
          border
          border-[#D2D2D7]
          bg-white
          shadow-[0_2px_8px_rgba(0,0,0,0.04)]
          transition-shadow
          focus-within:shadow-[0_4px_16px_rgba(0,0,0,0.07)]
        "
      >
        <div
          className="
            flex
            flex-col
            sm:flex-row
          "
        >
          {/* URL input */}

          <div className="relative flex-1">
            <input
              ref={inputRef}
              type="url"
              value={url}
              onChange={(event) => {
                setUrl(
                  event.target.value,
                );

                if (result) {
                  setResult(null);
                }

                if (error) {
                  setError('');
                }
              }}
              onKeyDown={(event) => {
                if (
                  event.key === 'Enter' &&
                  !loading
                ) {
                  void handleShorten();
                }
              }}
              placeholder="Paste a long URL"
              className={[
                `
                  h-[54px]
                  w-full
                  bg-white
                  px-4
                  text-[15px]
                  text-[#1D1D1F]
                  outline-none
                  placeholder:text-[#A1A1A6]
                `,
                error
                  ? 'border-red-400'
                  : '',
              ].join(' ')}
              aria-label="Long URL to shorten"
              aria-describedby={
                error
                  ? 'url-error'
                  : undefined
              }
              aria-invalid={!!error}
              disabled={loading}
            />
          </div>

          {/* Shorten button */}

          <button
            type="button"
            onClick={() =>
              void handleShorten()
            }
            disabled={loading}
            className="
              h-[54px]
              shrink-0
              border-t
              border-[#D2D2D7]
              bg-[#147A4A]
              px-7
              text-[14px]
              font-semibold
              tracking-[-0.01em]
              text-white
              transition-all
              duration-150
              hover:bg-[#126C42]
              active:bg-[#0F5D39]
              disabled:cursor-not-allowed
              disabled:opacity-60
              sm:border-l
              sm:border-t-0
            "
          >
            {loading
              ? 'Shortening…'
              : 'Shorten URL'}
          </button>
        </div>
      </div>

      {/* ========================================================
          ERROR
      ======================================================== */}

      {error && (
        <p
          id="url-error"
          className="
            mt-2.5
            text-left
            text-xs
            font-medium
            text-red-500
          "
          role="alert"
        >
          {error}
        </p>
      )}

      {/* ========================================================
          CUSTOM OPTIONS TOGGLE
      ======================================================== */}

      <button
        type="button"
        onClick={() =>
          setCustomOpen(
            current => !current,
          )
        }
        className="
          group
          mx-auto
          mt-4
          flex
          items-center
          gap-1.5
          text-[12px]
          font-medium
          text-[#6E6E73]
          transition-colors
          hover:text-[#1D1D1F]
        "
        aria-expanded={customOpen}
      >
        <span
          className="
            flex
            h-5
            w-5
            items-center
            justify-center
            rounded-md
            border
            border-[#D2D2D7]
            bg-white
            text-[#86868B]
            transition-colors
            group-hover:border-[#A1A1A6]
            group-hover:text-[#1D1D1F]
          "
        >
          {customOpen ? (
            <ChevronUp size={12} />
          ) : (
            <ChevronDown size={12} />
          )}
        </span>

        <span>
          Custom options
        </span>

        <span className="text-[#A1A1A6]">
          Optional
        </span>
      </button>

      {/* ========================================================
          CUSTOM OPTIONS PANEL
      ======================================================== */}

      {customOpen && (
        <div
          className="
            mt-4
            rounded-xl
            border
            border-[#E5E5E7]
            bg-[#FAFAFA]
            p-5
            text-left
            shadow-[0_2px_8px_rgba(0,0,0,0.025)]
            sm:p-6
          "
        >
          <div
            className="
              grid
              grid-cols-1
              gap-5
              sm:grid-cols-2
              sm:gap-6
            "
          >

            {/* ==================================================
                CUSTOM ALIAS
            ================================================== */}

            <div>
              <label
                htmlFor="custom-alias"
                className="
                  mb-2
                  block
                  text-[12px]
                  font-semibold
                  text-[#1D1D1F]
                "
              >
                Custom alias
              </label>

              <div
                className="
                  flex
                  h-11
                  overflow-hidden
                  rounded-lg
                  border
                  border-[#D2D2D7]
                  bg-white
                  transition-all
                  focus-within:border-[#2E7D52]
                  focus-within:ring-2
                  focus-within:ring-[#2E7D52]/10
                "
              >
                <span
                  className="
                    flex
                    shrink-0
                    items-center
                    border-r
                    border-[#E5E5E7]
                    bg-[#F5F5F7]
                    px-3
                    text-[12px]
                    font-medium
                    text-[#86868B]
                  "
                >
                  trimly.app/
                </span>

                <input
                  id="custom-alias"
                  type="text"
                  value={customAlias}
                  maxLength={8}
                  onChange={(event) => {
                    const value =
                      event.target.value;

                    if (
                      value === '' ||
                      /^[a-zA-Z0-9_-]{0,8}$/.test(
                        value,
                      )
                    ) {
                      setCustomAlias(
                        value,
                      );

                      if (result) {
                        setResult(null);
                      }

                      if (error) {
                        setError('');
                      }
                    }
                  }}
                  placeholder="my-link"
                  className="
                    min-w-0
                    flex-1
                    bg-transparent
                    px-3
                    text-[13px]
                    text-[#1D1D1F]
                    outline-none
                    placeholder:text-[#A1A1A6]
                  "
                  disabled={loading}
                />
              </div>

              <p
                className="
                  mt-2
                  text-[11px]
                  leading-5
                  text-[#86868B]
                "
              >
                Up to 8 characters.
              </p>
            </div>

            {/* ==================================================
                EXPIRATION DATE
            ================================================== */}

            <div>
              <label
                htmlFor="expiration-date"
                className="
                  mb-2
                  block
                  text-[12px]
                  font-semibold
                  text-[#1D1D1F]
                "
              >
                Expiration date
              </label>

              <input
                id="expiration-date"
                type="date"
                value={expirationDate}
                min={
                  new Date()
                    .toISOString()
                    .split('T')[0]
                }
                onChange={(event) => {
                  setExpirationDate(
                    event.target.value,
                  );

                  if (result) {
                    setResult(null);
                  }

                  if (error) {
                    setError('');
                  }
                }}
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
                  transition-all
                  focus:border-[#2E7D52]
                  focus:ring-2
                  focus:ring-[#2E7D52]/10
                "
                disabled={loading}
              />

              <p
                className="
                  mt-2
                  text-[11px]
                  leading-5
                  text-[#86868B]
                "
              >
                Leave empty for no expiration.
              </p>
            </div>

          </div>
        </div>
      )}

      {/* ========================================================
          SUCCESS RESULT
      ======================================================== */}

      {result && !loading && (
        <div
          className="
            mt-5
            overflow-hidden
            rounded-xl
            border
            border-[#D2D2D7]
            bg-white
            shadow-[0_2px_10px_rgba(0,0,0,0.04)]
          "
        >

          {/* ====================================================
              SUCCESS HEADER
          ==================================================== */}

          <div
            className="
              flex
              items-center
              gap-2.5
              border-b
              border-[#E5E5E7]
              bg-[#FAFAFA]
              px-4
              py-3
            "
          >
            <span
              className="
                flex
                h-5
                w-5
                shrink-0
                items-center
                justify-center
                rounded-full
                bg-[#147A4A]
              "
            >
              <Check
                size={11}
                strokeWidth={3}
                className="text-white"
              />
            </span>

            <span
              className="
                text-[12px]
                font-semibold
                text-[#1D1D1F]
              "
            >
              Short link created
            </span>
          </div>

          {/* ====================================================
              SHORT URL
          ==================================================== */}

          <div
            className="
              flex
              min-w-0
              items-center
              gap-3
              px-4
              py-4
            "
          >
            <span
              className="
                min-w-0
                flex-1
                truncate
                font-mono-url
                text-[13px]
                font-medium
                text-[#1D1D1F]
              "
            >
              {shortUrl}
            </span>

            <button
              type="button"
              onClick={() =>
                void handleCopy()
              }
              className="
                shrink-0
                rounded-md
                p-1.5
                text-[#86868B]
                transition-colors
                hover:bg-[#F5F5F7]
                hover:text-[#1D1D1F]
              "
              aria-label="Copy short URL"
            >
              {copied ? (
                <Check
                  size={14}
                  strokeWidth={2.5}
                />
              ) : (
                <Copy size={14} />
              )}
            </button>
          </div>

          {/* ====================================================
              ACTIONS
          ==================================================== */}

          <div
            className="
              grid
              grid-cols-3
              border-t
              border-[#E5E5E7]
            "
          >

            {/* Copy */}

            <button
              type="button"
              onClick={() =>
                void handleCopy()
              }
              className="
                flex
                items-center
                justify-center
                gap-2
                border-r
                border-[#E5E5E7]
                py-3
                text-[12px]
                font-medium
                text-[#424245]
                transition-colors
                hover:bg-[#F5F5F7]
                hover:text-[#1D1D1F]
              "
            >
              {copied ? (
                <Check
                  size={13}
                  strokeWidth={2.5}
                />
              ) : (
                <Copy size={13} />
              )}

              {copied
                ? 'Copied'
                : 'Copy'}
            </button>

            {/* Open */}

            <a
              href={shortUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="
                flex
                items-center
                justify-center
                gap-2
                border-r
                border-[#E5E5E7]
                py-3
                text-[12px]
                font-medium
                text-[#424245]
                transition-colors
                hover:bg-[#F5F5F7]
                hover:text-[#1D1D1F]
              "
            >
              <ExternalLink size={13} />

              Open
            </a>

            {/* Share */}

            <button
              type="button"
              onClick={() =>
                void handleShare()
              }
              className="
                flex
                items-center
                justify-center
                gap-2
                py-3
                text-[12px]
                font-medium
                text-[#424245]
                transition-colors
                hover:bg-[#F5F5F7]
                hover:text-[#1D1D1F]
              "
            >
              <Share2 size={13} />

              Share
            </button>

          </div>
        </div>
      )}
    </div>
  );
}

// ================================================================
// HELPERS
// ================================================================

function resultUrl(
  link: ShortLink,
): string {
  /*
   * The backend is the source of truth.
   *
   * Never reconstruct the short URL on the frontend.
   */
  return link.shortUrl ?? '';
} 