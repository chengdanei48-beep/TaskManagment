import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError } from '../api/http'
import { useAuth } from '../auth/AuthContext'

const USERNAME_MAX = 50
const PASSWORD_MIN = 8
const PASSWORD_MAX = 72

type Mode = 'login' | 'register'

function validate(username: string, password: string, confirm: string): string | null {
  if (username === '' || username.length > USERNAME_MAX) {
    return `ユーザー名は1〜${USERNAME_MAX}文字で入力してください`
  }
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `パスワードは${PASSWORD_MIN}〜${PASSWORD_MAX}文字で入力してください`
  }
  if (password !== confirm) {
    return 'パスワードが一致しません'
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
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const name = username.trim()
    // 新規登録時のみ入力形式を事前に確認する(ログインは既存アカウントなのでサーバー判定に任せる)
    const invalid = mode === 'register' ? validate(name, password, confirm) : null
    if (invalid) {
      setError(invalid)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await (mode === 'login' ? login : register)(name, password)
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
          <span>
            ユーザー名<span className="req">必須</span>
          </span>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
          />
          {!isLogin && <span className="hint">他の人と重複しない名前</span>}
        </label>
        <label>
          <span>
            パスワード<span className="req">必須</span>
          </span>
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
        {!isLogin && (
          <label>
            <span>
              パスワード(確認)<span className="req">必須</span>
            </span>
            <input
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              autoComplete="new-password"
              required
            />
          </label>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          {isLogin ? 'ログイン' : '登録する'}
        </button>
        <button
          type="button"
          className="link"
          onClick={() => {
            setMode(isLogin ? 'register' : 'login')
            setError(null)
            setConfirm('')
          }}
        >
          {isLogin ? 'アカウント登録はこちら' : 'ログインに戻る'}
        </button>
      </form>
    </main>
  )
}
