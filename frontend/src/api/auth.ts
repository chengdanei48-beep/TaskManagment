import type { User } from '../types'
import { ApiError, request } from './http'

/** ログイン中のユーザーを返す。未ログイン(401)なら null。 */
export async function fetchMe(signal?: AbortSignal): Promise<User | null> {
  try {
    return await request<User>('/api/auth/me', { signal })
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function login(username: string, password: string): Promise<User> {
  return request<User>('/api/auth/login', { method: 'POST', body: { username, password } })
}

export function register(username: string, password: string): Promise<User> {
  return request<User>('/api/auth/register', { method: 'POST', body: { username, password } })
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' })
}
