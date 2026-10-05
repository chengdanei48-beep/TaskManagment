import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/http'
import type { Card, CardInput, Priority } from '../types'

const TITLE_MAX = 50
const DESCRIPTION_MAX = 500

export type DialogTarget =
  | { mode: 'create'; columnId: number; columnName: string }
  | { mode: 'edit'; card: Card }

interface Props {
  target: DialogTarget
  onSave: (input: CardInput) => Promise<void>
  onDelete?: () => Promise<void>
  onClose: () => void
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) return '入力内容を確認してください'
    if (e.status === 404) return '対象が見つかりません。画面を更新してください'
  }
  return '保存に失敗しました'
}

export function CardDialog({ target, onSave, onDelete, onClose }: Props) {
  const card = target.mode === 'edit' ? target.card : null
  const [title, setTitle] = useState(card?.title ?? '')
  const [description, setDescription] = useState(card?.description ?? '')
  const [dueDate, setDueDate] = useState(card?.dueDate ?? '')
  const [priority, setPriority] = useState<Priority | ''>(card?.priority ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function run(action: () => Promise<void>, failure?: (e: unknown) => string) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError((failure ?? errorText)(e))
      setBusy(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const trimmed = title.trim()
    if (trimmed === '' || trimmed.length > TITLE_MAX) {
      setError(`タイトルは1〜${TITLE_MAX}文字で入力してください`)
      return
    }
    void run(() =>
      onSave({
        ...(target.mode === 'create' ? { columnId: target.columnId } : {}),
        title: trimmed,
        description: description === '' ? null : description,
        dueDate: dueDate === '' ? null : dueDate,
        priority: priority === '' ? null : priority,
      }),
    )
  }

  function remove() {
    if (!onDelete) return
    if (!window.confirm('このカードを削除しますか?削除すると元に戻せません。')) return
    void run(onDelete, () => '削除に失敗しました')
  }

  const heading =
    target.mode === 'create' ? `タスクを追加(${target.columnName})` : 'タスクを編集'
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="card-dialog-title"
        onSubmit={submit}
      >
        <h2 id="card-dialog-title">{heading}</h2>
        <label>
          タイトル<span className="req">必須</span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={TITLE_MAX}
            autoFocus
          />
        </label>
        <label>
          詳細説明
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={DESCRIPTION_MAX}
            rows={4}
          />
          <span className="hint">
            {description.length}/{DESCRIPTION_MAX}
          </span>
        </label>
        <label>
          期限日
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </label>
        <label>
          重要度
          <select value={priority} onChange={(e) => setPriority(e.target.value as Priority | '')}>
            <option value="">未設定</option>
            <option value="HIGH">高</option>
            <option value="MEDIUM">中</option>
            <option value="LOW">低</option>
          </select>
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          {onDelete && (
            <button type="button" className="danger" onClick={remove} disabled={busy}>
              削除
            </button>
          )}
          <span className="spacer" />
          <button type="button" onClick={onClose} disabled={busy}>
            キャンセル
          </button>
          <button type="submit" className="primary" disabled={busy}>
            保存
          </button>
        </div>
      </form>
    </div>
  )
}
