import type { BoardColumn, Card as CardData } from '../types'
import { Card } from './Card'

export function Column({ column, cards }: { column: BoardColumn; cards: CardData[] }) {
  return (
    <section className="column">
      <h2 className="col-head">
        {column.name} <span className="count">{cards.length}</span>
      </h2>
      {cards.length === 0 ? (
        <p className="empty">カードなし</p>
      ) : (
        cards.map((card) => <Card key={card.id} card={card} />)
      )}
    </section>
  )
}
