import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/http'
import { formatDateTime } from '../format'
import {
  LABEL_NAME_MAX,
  PRIORITY_LABEL,
  type Card,
  type CardInput,
  type Label,
  type LabelInput,
  type Priority,
} from '../types'
import { ConfirmDialog } from './ConfirmDialog'
import { LabelChip } from './LabelChip'
import { Modal } from './Modal'

const TITLE_MAX = 50
const DESCRIPTION_MAX = 500
const DEFAULT_LABEL_COLOR = '#0c66e4'

export type DialogTarget = { mode: 'create'; columnId: number } | { mode: 'edit'; card: Card }

interface Props {
  target: DialogTarget
  /** 選べるラベルの一覧 */
  labels: Label[]
  onCreateLabel: (input: LabelInput) => Promise<Label>
  onDeleteLabel: (id: number) => Promise<void>
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

function labelErrorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 400) return `ラベル名は1〜${LABEL_NAME_MAX}文字で入力してください`
    if (e.status === 409) return '同じ名前のラベルが既にあります'
    if (e.status === 404) return 'ラベルが見つかりません。画面を更新してください'
  }
  return 'ラベルの操作に失敗しました'
}

export function CardDialog({
  target,
  labels,
  onCreateLabel,
  onDeleteLabel,
  onSave,
  onDelete,
  onClose,
}: Props) {
  const card = target.mode === 'edit' ? target.card : null
  const [selectedLabelIds, setSelectedLabelIds] = useState<Set<number>>(
    () => new Set(card?.labels.map((l) => l.id) ?? []),
  )
  const [newLabelName, setNewLabelName] = useState('')
  const [newLabelColor, setNewLabelColor] = useState(DEFAULT_LABEL_COLOR)
  const [labelError, setLabelError] = useState<string | null>(null)
  const [title, setTitle] = useState(card?.title ?? '')
  const [description, setDescription] = useState(card?.description ?? '')
  const [dueDate, setDueDate] = useState(card?.dueDate ?? '')
  const [priority, setPriority] = useState<Priority | ''>(card?.priority ?? '')
  const [titleError, setTitleError] = useState<string | null>(null)
  const [descriptionError, setDescriptionError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  // 確認ダイアログの対象(カード削除 / ラベル削除)
  const [confirming, setConfirming] = useState<'card' | Label | null>(null)
  const headingId = useId()
  const titleErrorId = useId()
  const descriptionErrorId = useId()

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

  function toggleLabel(id: number) {
    setSelectedLabelIds((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }

  async function addLabel() {
    const name = newLabelName.trim()
    if (name === '' || name.length > LABEL_NAME_MAX) {
      setLabelError(`ラベル名は1〜${LABEL_NAME_MAX}文字で入力してください`)
      return
    }
    setLabelError(null)
    try {
      const created = await onCreateLabel({ name, color: newLabelColor })
      // 作ったラベルはそのまま選択状態にする
      setSelectedLabelIds((prev) => new Set(prev).add(created.id))
      setNewLabelName('')
    } catch (e) {
      setLabelError(labelErrorText(e))
    }
  }

  async function removeLabel(label: Label) {
    setConfirming(null)
    setLabelError(null)
    try {
      await onDeleteLabel(label.id)
      setSelectedLabelIds((prev) => {
        const next = new Set(prev)
        next.delete(label.id)
        return next
      })
    } catch (e) {
      setLabelError(labelErrorText(e))
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const trimmed = title.trim()
    const invalidTitle =
      trimmed === '' || trimmed.length > TITLE_MAX
        ? `タイトルは1〜${TITLE_MAX}文字で入力してください`
        : null
    const invalidDescription =
      description.length > DESCRIPTION_MAX
        ? `詳細説明文は${DESCRIPTION_MAX}文字までで入力してください`
        : null
    setTitleError(invalidTitle)
    setDescriptionError(invalidDescription)
    if (invalidTitle || invalidDescription) return
    void run(() =>
      onSave({
        ...(target.mode === 'create' ? { columnId: target.columnId } : {}),
        title: trimmed,
        description: description === '' ? null : description,
        dueDate: dueDate === '' ? null : dueDate,
        priority: priority === '' ? null : priority,
        // 一覧にあるラベルだけ送る(他タブで削除済みのIDは除く)
        labelIds: labels.filter((l) => selectedLabelIds.has(l.id)).map((l) => l.id),
      }),
    )
  }

  function removeCard() {
    setConfirming(null)
    if (onDelete) void run(onDelete, () => '削除に失敗しました')
  }

  const heading = target.mode === 'create' ? 'タスクを追加' : 'タスクを編集'
  return (
    <>
      <Modal onClose={onClose}>
        <form className="dialog" aria-labelledby={headingId} onSubmit={submit} noValidate>
          <h2 id={headingId}>{heading}</h2>
          <label>
            <span>
              タイトル<span className="req">必須</span>
            </span>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              aria-invalid={titleError !== null}
              aria-describedby={titleError ? titleErrorId : undefined}
              autoFocus
            />
            {titleError ? (
              <span id={titleErrorId} className="error" role="alert">
                {titleError}
              </span>
            ) : (
              <span className="hint">1〜{TITLE_MAX}文字</span>
            )}
          </label>
          <label>
            詳細説明文
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              aria-invalid={descriptionError !== null}
              aria-describedby={descriptionError ? descriptionErrorId : undefined}
              rows={4}
            />
            {descriptionError ? (
              <span id={descriptionErrorId} className="error" role="alert">
                {descriptionError}
              </span>
            ) : (
              <span className="hint">
                {description.length}/{DESCRIPTION_MAX}(任意)
              </span>
            )}
          </label>
          <label>
            期限日
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </label>
          <label>
            重要度
            <select value={priority} onChange={(e) => setPriority(e.target.value as Priority | '')}>
              <option value="">未設定</option>
              <option value="HIGH">{PRIORITY_LABEL.HIGH}</option>
              <option value="MEDIUM">{PRIORITY_LABEL.MEDIUM}</option>
              <option value="LOW">{PRIORITY_LABEL.LOW}</option>
            </select>
          </label>
          <fieldset className="label-picker">
            <legend>ラベル</legend>
            {labels.length === 0 && <span className="hint">ラベルはまだありません</span>}
            {labels.map((label) => (
              <div key={label.id} className="label-option">
                <label>
                  <input
                    type="checkbox"
                    checked={selectedLabelIds.has(label.id)}
                    onChange={() => toggleLabel(label.id)}
                  />
                  <LabelChip label={label} />
                </label>
                <button
                  type="button"
                  aria-label={`ラベル「${label.name}」を削除`}
                  onClick={() => setConfirming(label)}
                >
                  ×
                </button>
              </div>
            ))}
            <div className="label-new">
              <input
                type="text"
                aria-label="新しいラベル名"
                placeholder="新しいラベル名"
                value={newLabelName}
                maxLength={LABEL_NAME_MAX}
                onChange={(e) => setNewLabelName(e.target.value)}
                onKeyDown={(e) => {
                  // Enter でカード本体が保存されないよう、ラベル追加として扱う
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    void addLabel()
                  }
                }}
              />
              <input
                type="color"
                aria-label="ラベルの色"
                value={newLabelColor}
                onChange={(e) => setNewLabelColor(e.target.value)}
              />
              <button type="button" onClick={() => void addLabel()}>
                追加
              </button>
            </div>
            {labelError && (
              <p className="error" role="alert">
                {labelError}
              </p>
            )}
          </fieldset>
          {card && (
            <div className="hint-created">作成日時: {formatDateTime(card.createdAt)}(変更できません)</div>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <div className="dialog-actions">
            {onDelete && (
              <button
                type="button"
                className="danger"
                onClick={() => setConfirming('card')}
                disabled={busy}
              >
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
      </Modal>
      {confirming === 'card' && card && (
        <ConfirmDialog
          title="カードを削除しますか?"
          message={`「${card.title}」を削除します。削除後は元に戻せません。`}
          onConfirm={removeCard}
          onCancel={() => setConfirming(null)}
        />
      )}
      {confirming !== null && confirming !== 'card' && (
        <ConfirmDialog
          title="ラベルを削除しますか?"
          message={`「${confirming.name}」を削除します。すべてのカードから外れます。`}
          onConfirm={() => void removeLabel(confirming)}
          onCancel={() => setConfirming(null)}
        />
      )}
    </>
  )
}
