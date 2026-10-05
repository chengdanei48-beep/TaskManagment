import { useId } from 'react'
import { Modal } from './Modal'

interface Props {
  /** 例: 「カードを削除しますか?」 */
  title: string
  message: string
  /** 確認ボタンの文言(既定: 削除する) */
  confirmLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

/** 削除などの取り消せない操作の前に出す確認ダイアログ(画面設計書 5章)。 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = '削除する',
  onConfirm,
  onCancel,
}: Props) {
  const titleId = useId()
  return (
    <Modal onClose={onCancel}>
      <div className="dialog" role="alertdialog" aria-labelledby={titleId}>
        <h2 id={titleId}>{title}</h2>
        <p className="message">{message}</p>
        <div className="dialog-actions">
          <span className="spacer" />
          <button type="button" onClick={onCancel} autoFocus>
            キャンセル
          </button>
          <button type="button" className="danger" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  )
}
