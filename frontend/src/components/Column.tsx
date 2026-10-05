import type { DragEvent } from 'react'
import type { BoardColumn, Card as CardData } from '../types'
import { Card } from './Card'

const DONE_COLUMN_NAME = '完了'

interface Props {
  column: BoardColumn
  cards: CardData[]
  /** ドラッグ中のカードのID(ドラッグしていなければ null) */
  draggingCardId: number | null
  /** この列に表示するドロップ位置。undefined: 表示しない / null: 末尾 / 数値: そのカードの手前 */
  dropBefore: number | null | undefined
  onAdd: (column: BoardColumn) => void
  onEdit: (card: CardData) => void
  onDragStart: (cardId: number) => void
  onDragEnd: () => void
  onDragOverPosition: (columnId: number, beforeCardId: number | null) => void
  onDragLeaveColumn: () => void
  onDrop: () => void
}

export function Column({
  column,
  cards,
  draggingCardId,
  dropBefore,
  onAdd,
  onEdit,
  onDragStart,
  onDragEnd,
  onDragOverPosition,
  onDragLeaveColumn,
  onDrop,
}: Props) {
  const done = column.name === DONE_COLUMN_NAME

  function handleDragOver(e: DragEvent<HTMLElement>) {
    if (draggingCardId === null) return
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
    // マウス位置より下にある最初のカード(ドラッグ中のカードを除く)の手前に入れる。なければ末尾
    let before: number | null = null
    for (const el of e.currentTarget.querySelectorAll<HTMLElement>('[data-card-id]')) {
      const id = Number(el.dataset.cardId)
      if (id === draggingCardId) continue
      const rect = el.getBoundingClientRect()
      if (e.clientY < rect.top + rect.height / 2) {
        before = id
        break
      }
    }
    onDragOverPosition(column.id, before)
  }

  function handleDragLeave(e: DragEvent<HTMLElement>) {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) onDragLeaveColumn()
  }

  function handleDrop(e: DragEvent<HTMLElement>) {
    e.preventDefault()
    onDrop()
  }

  const placeholder = <div className="drop-zone" aria-hidden="true" />
  return (
    <section
      className="column"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <h2 className="col-head">
        {column.name} <span className="count">{cards.length}</span>
      </h2>
      {cards.length === 0 && dropBefore === undefined && <p className="empty">カードなし</p>}
      {cards.map((card) => (
        <div key={card.id}>
          {dropBefore === card.id && placeholder}
          <Card
            card={card}
            done={done}
            dragging={draggingCardId === card.id}
            onClick={() => onEdit(card)}
            onDragStart={() => onDragStart(card.id)}
            onDragEnd={onDragEnd}
          />
        </div>
      ))}
      {dropBefore === null && placeholder}
      <button type="button" className="btn-add" onClick={() => onAdd(column)}>
        + タスク追加
      </button>
    </section>
  )
}
