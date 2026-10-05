import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { User } from '../types'

interface AuthState {
  /** undefined: 確認中 / null: 未ログイン */
  user: User | null | undefined
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** サーバー側でセッションが切れていた(401)ときに、画面側のログイン状態を破棄する */
  expire: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined)

  useEffect(() => {
    const controller = new AbortController()
    authApi
      .fetchMe(controller.signal)
      .then(setUser)
      .catch(() => {
        // 401 以外(通信エラーなど)でも確認できなかったものとして、ログイン画面へ進める
        if (!controller.signal.aborted) setUser(null)
      })
    return () => controller.abort()
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    setUser(await authApi.login(username, password))
  }, [])

  const register = useCallback(async (username: string, password: string) => {
    setUser(await authApi.register(username, password))
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // サーバーに届かなくても、画面側はログアウト済みにする(セッションは期限切れで失効する)
    } finally {
      setUser(null)
    }
  }, [])

  const expire = useCallback(() => setUser(null), [])

  const value = useMemo(
    () => ({ user, login, register, logout, expire }),
    [user, login, register, logout, expire],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// oxlint-disable-next-line react/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth は AuthProvider の内側で使ってください')
  return ctx
}
