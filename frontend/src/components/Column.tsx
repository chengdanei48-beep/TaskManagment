import type { BoardColumn, Card as CardData } from '../types'
import { Card } from './Card'

const DONE_COLUMN_NAME = '完了'

interface Props {
  column: BoardColumn
  cards: CardData[]
  onAdd: (column: BoardColumn) => void
  onEdit: (card: CardData) => void
}

export function Column({ column, cards, onAdd, onEdit }: Props) {
  const done = column.name === DONE_COLUMN_NAME
  return (
    <section className="column">
      <h2 className="col-head">
        {column.name} <span className="count">{cards.length}</span>
      </h2>
      {cards.length === 0 ? (
        <p className="empty">カードなし</p>
      ) : (
        cards.map((card) => <Card key={card.id} card={card} done={done} onClick={() => onEdit(card)} />)
      )}
      <button type="button" className="btn-add" onClick={() => onAdd(column)}>
        + タスク追加
      </button>
    </section>
  )
}
