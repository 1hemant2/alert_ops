import { jsonBody, request } from './client'
import type { LoginResponse, RegisterRequest, User } from './types'

export async function login(email: string, password: string): Promise<string> {
  const response = await request<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: jsonBody({ email, password }),
    public: true,
  })
  const token = response.data?.['jwt-token']
  if (!token) throw new Error('The API response did not include a login token.')
  return token
}

export function register(payload: RegisterRequest): Promise<User> {
  return request<User>('/api/v1/auth/register', {
    method: 'POST',
    body: jsonBody(payload),
    public: true,
  })
}
