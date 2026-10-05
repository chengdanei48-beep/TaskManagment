import type { Card as CardData, Priority } from '../types'

const PRIORITY_LABEL: Record<Priority, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

function todayString(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

export function Card({ card }: { card: CardData }) {
  const overdue = card.dueDate !== null && card.dueDate < todayString()
  return (
    <div className={overdue ? 'card overdue' : 'card'}>
      <div className="card-title">{card.title}</div>
      <div className="meta">
        <span className={`badge badge-${card.priority.toLowerCase()}`}>
          {PRIORITY_LABEL[card.priority]}
        </span>
        {card.dueDate && (
          <span>
            期限: {card.dueDate}
            {overdue && '(期限切れ)'}
          </span>
        )}
      </div>
    </div>
  )
}
