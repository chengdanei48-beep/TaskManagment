import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

interface Props {
  /** 閉じる操作(Esc・背景クリック)を受けたときに呼ばれる */
  onClose: () => void
  children: ReactNode
}

/**
 * ネイティブ <dialog> を showModal() で開くモーダル。
 * フォーカストラップ・Esc で閉じる・背面の操作無効化・閉じたときのフォーカス復帰はブラウザが行う。
 */
export function Modal({ onClose, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  // onClose が親の再描画で変わっても、ダイアログを開き直さない
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    dialog.showModal()
    return () => dialog.close()
  }, [])

  return (
    <dialog
      ref={ref}
      className="modal"
      onCancel={(e) => {
        e.preventDefault()
        onCloseRef.current()
      }}
      onMouseDown={(e) => e.target === e.currentTarget && onCloseRef.current()}
    >
      {children}
    </dialog>
  )
}
