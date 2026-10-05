import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'

const USERNAME_MAX = 50
const PASSWORD_MIN = 8
const PASSWORD_MAX = 72

type Mode = 'login' | 'register'

function validate(username: string, password: string): string | null {
  if (username.trim() === '' || username.length > USERNAME_MAX) {
    return `ユーザー名は1〜${USERNAME_MAX}文字で入力してください`
  }
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `パスワードは${PASSWORD_MIN}〜${PASSWORD_MAX}文字で入力してください`
  }
  return null
}

function errorText(mode: Mode, e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return 'ユーザー名またはパスワードが正しくありません'
    if (e.status === 409) return 'このユーザー名は既に使われています'
    if (e.status === 400) return '入力内容を確認してください'
  }
  return mode === 'login' ? 'ログインに失敗しました' : '登録に失敗しました'
}

export function LoginPage() {
  const { user, login, register } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    // 新規登録時のみ入力形式を事前に確認する(ログインは既存アカウントなのでサーバー判定に任せる)
    const invalid = mode === 'register' ? validate(username, password) : null
    if (invalid) {
      setError(invalid)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await (mode === 'login' ? login : register)(username, password)
    } catch (err) {
      setError(errorText(mode, err))
      setSubmitting(false)
    }
  }

  const isLogin = mode === 'login'
  return (
    <main className="auth-page">
      <form className="auth-form" onSubmit={onSubmit}>
        <h1>{isLogin ? 'ログイン' : 'アカウント登録'}</h1>
        <label>
          ユーザー名
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
        </label>
        <label>
          パスワード
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            required
          />
          {!isLogin && (
            <span className="hint">
              {PASSWORD_MIN}〜{PASSWORD_MAX}文字
            </span>
          )}
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {isLogin ? 'ログイン' : '登録してはじめる'}
        </button>
        <button
          type="button"
          className="link"
          onClick={() => {
            setMode(isLogin ? 'register' : 'login')
            setError(null)
          }}
        >
          {isLogin ? 'アカウント登録はこちら' : 'ログインはこちら'}
        </button>
      </form>
    </main>
  )
}
