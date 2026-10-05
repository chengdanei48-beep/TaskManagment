import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/http'
import { COLUMN_LIMIT, COLUMN_NAME_MAX } from '../types'

interface Props {
  onAdd: (name: string) => Promise<void>
  /** 列数が上限に達している。追加はできず、その旨を表示する */
  limitReached: boolean
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 409) return `列は最大${COLUMN_LIMIT}列までです`
    if (e.status === 400) return `列名は1〜${COLUMN_NAME_MAX}文字で入力してください`
  }
  return '列を追加できませんでした'
}

export function AddColumn({ onAdd, limitReached }: Props) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const errorId = useId()

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

  if (limitReached) {
    return (
      <div className="add-col">
        <button type="button" disabled>
          + 列を追加
        </button>
        <p className="hint">列は最大{COLUMN_LIMIT}列までのため、追加できません</p>
      </div>
    )
  }
  if (!open) {
    return (
      <button type="button" className="add-col" onClick={() => setOpen(true)}>
        + 列を追加
      </button>
    )
  }
  return (
    <form className="add-col add-col-form" onSubmit={(e) => void submit(e)} noValidate>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="列名"
        aria-label="列名"
        aria-invalid={error !== null}
        aria-describedby={error ? errorId : undefined}
        autoFocus
        onKeyDown={(e) => e.key === 'Escape' && close()}
      />
      {error && (
        <p id={errorId} className="error" role="alert">
          {error}
        </p>
      )}
      <div className="dialog-actions">
        <button type="submit" className="primary" disabled={busy}>
          保存
        </button>
        <button type="button" onClick={close} disabled={busy}>
          キャンセル
        </button>
      </div>
    </form>
  )
}
