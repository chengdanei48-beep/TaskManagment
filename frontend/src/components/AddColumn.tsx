import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/http'
import { COLUMN_LIMIT, COLUMN_NAME_MAX } from '../types'

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 409) return `列は最大${COLUMN_LIMIT}列までです`
    if (e.status === 400) return `列名は1〜${COLUMN_NAME_MAX}文字で入力してください`
  }
  return '列を追加できませんでした'
}

export function AddColumn({ onAdd }: { onAdd: (name: string) => Promise<void> }) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function close() {
    setOpen(false)
    setName('')
    setError(null)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (trimmed === '' || trimmed.length > COLUMN_NAME_MAX) {
      setError(`列名は1〜${COLUMN_NAME_MAX}文字で入力してください`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onAdd(trimmed)
      close()
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  if (!open) {
    return (
      <button type="button" className="add-col" onClick={() => setOpen(true)}>
        + 列を追加
      </button>
    )
  }
  return (
    <form className="add-col add-col-form" onSubmit={(e) => void submit(e)}>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        maxLength={COLUMN_NAME_MAX}
        placeholder="列名"
        aria-label="列名"
        autoFocus
        onKeyDown={(e) => e.key === 'Escape' && close()}
      />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="dialog-actions">
        <button type="submit" className="primary" disabled={busy}>
          追加
        </button>
        <button type="button" onClick={close} disabled={busy}>
          キャンセル
        </button>
      </div>
    </form>
  )
}
