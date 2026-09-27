const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

type RequestOptions = RequestInit & { public?: boolean }

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { public: isPublic = false, headers, ...init } = options
  const token = isPublic ? null : sessionStorage.getItem('alertops.token')
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  })

  if (response.status === 401 && token) {
    sessionStorage.removeItem('alertops.token')
    sessionStorage.removeItem('alertops.team')
    window.dispatchEvent(new Event('alertops:unauthorized'))
  }

  const contentType = response.headers.get('content-type') ?? ''
  const payload: unknown = response.status === 204
    ? undefined
    : contentType.includes('application/json')
      ? await response.json().catch(() => undefined)
      : await response.text().catch(() => '')

  if (!response.ok) {
    const body = payload as { code?: string; message?: string } | string | undefined
    const safeApiMessage = typeof body === 'object' && body !== null && typeof body.code === 'string'
      ? body.message
      : undefined
    const message = safeApiMessage ?? (response.status === 401
      ? 'Your session has expired. Sign in again.'
      : response.status === 403
        ? 'You do not have access to this team or resource.'
        : response.status === 404
          ? 'This record could not be found in the current team.'
          : response.status === 400
            ? 'Check the submitted values and try again.'
            : 'AlertOps could not complete that request. Try again.')
    throw new ApiError(message, response.status)
  }

  return payload as T
}

export function jsonBody(value: unknown): string {
  return JSON.stringify(value)
}
