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

// 「完了」列のカードは期限を過ぎていても強調しない (要件 D-3)
export function Card({
  card,
  done,
  onClick,
}: {
  card: CardData
  done: boolean
  onClick: () => void
}) {
  const overdue = !done && card.dueDate !== null && card.dueDate < todayString()
  return (
    <div
      className={overdue ? 'card overdue' : 'card'}
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
    >
      <div className="card-title">{card.title}</div>
      <div className="meta">
        {card.priority && (
          <span className={`badge badge-${card.priority.toLowerCase()}`}>
            {PRIORITY_LABEL[card.priority]}
          </span>
        )}
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
