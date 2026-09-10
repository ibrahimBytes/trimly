const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ??
  'http://localhost:8080';

const AUTH_STORAGE_KEY =
  'trimly.auth';

interface StoredAuth {
  token: string;
  tokenType: string;
  userId: number;
  email: string;
}

export interface ApiFetchOptions
  extends RequestInit {
  skipAuth?: boolean;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(
    message: string,
    status: number,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function getStoredAuth(): StoredAuth | null {
  try {
    const stored =
      localStorage.getItem(
        AUTH_STORAGE_KEY,
      );

    if (!stored) {
      return null;
    }

    const auth =
      JSON.parse(
        stored,
      ) as Partial<StoredAuth>;

    if (
      typeof auth.token !== 'string' ||
      !auth.token ||
      typeof auth.tokenType !== 'string' ||
      !auth.tokenType ||
      typeof auth.userId !== 'number' ||
      !Number.isFinite(auth.userId) ||
      typeof auth.email !== 'string' ||
      !auth.email
    ) {
      return null;
    }

    return {
      token: auth.token,
      tokenType: auth.tokenType,
      userId: auth.userId,
      email: auth.email,
    };
  } catch {
    return null;
  }
}

function getStoredToken(): string | null {
  return getStoredAuth()?.token ?? null;
}

export function getStoredAuthState(): StoredAuth | null {
  return getStoredAuth();
}

export function setStoredAuth(
  auth: StoredAuth,
): void {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify(auth),
  );
}

export function clearStoredAuth(): void {
  localStorage.removeItem(
    AUTH_STORAGE_KEY,
  );
}

function dispatchUnauthorized(): void {
  window.dispatchEvent(
    new CustomEvent(
      'trimly:unauthorized',
    ),
  );
}

async function parseErrorMessage(
  response: Response,
): Promise<string> {
  try {
    const body =
      (await response.json()) as {
        message?: unknown;
        error?: unknown;
      };

    if (
      body &&
      typeof body.message === 'string' &&
      body.message.trim()
    ) {
      return body.message;
    }

    if (
      body &&
      typeof body.error === 'string' &&
      body.error.trim()
    ) {
      return body.error;
    }
  } catch {
    // Response may not contain JSON.
  }

  switch (response.status) {
    case 400:
      return 'The request is invalid.';

    case 401:
      return 'Your session is no longer valid.';

    case 403:
      return 'You are not allowed to perform this action.';

    case 404:
      return 'The requested resource was not found.';

    case 409:
      return 'The request conflicts with existing data.';

    case 429:
      return 'Too many requests. Please try again later.';

    default:
      return 'The server returned an unexpected error.';
  }
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const {
    skipAuth = false,
    ...requestOptions
  } = options;

  const token =
    skipAuth
      ? null
      : getStoredToken();

  const headers =
    new Headers(
      requestOptions.headers,
    );

  headers.set(
    'Accept',
    'application/json',
  );

  if (
    requestOptions.body &&
    !headers.has(
      'Content-Type',
    )
  ) {
    headers.set(
      'Content-Type',
      'application/json',
    );
  }

  if (token) {
    headers.set(
      'Authorization',
      `Bearer ${token}`,
    );
  }

  const response =
    await fetch(
      `${API_BASE_URL}${path}`,
      {
        ...requestOptions,
        headers,
      },
    );

  /*
   * 401 means the normal authenticated
   * session is no longer valid.
   *
   * We intentionally do NOT treat every
   * 403 as an authentication failure.
   *
   * A 403 can represent a legitimate
   * authorization failure.
   */
  if (
    response.status === 401 &&
    !skipAuth
  ) {
    clearStoredAuth();
    dispatchUnauthorized();

    throw new ApiError(
      'Your session has expired or been signed out.',
      401,
    );
  }

  if (!response.ok) {
    throw new ApiError(
      await parseErrorMessage(
        response,
      ),
      response.status,
    );
  }

  if (
    response.status === 204
  ) {
    return undefined as T;
  }

  const contentType =
    response.headers.get(
      'content-type',
    );

  if (
    !contentType?.includes(
      'application/json',
    )
  ) {
    return undefined as T;
  }

  return (
    await response.json()
  ) as T;
}

export function getApiBaseUrl(): string {
  return API_BASE_URL;
} 