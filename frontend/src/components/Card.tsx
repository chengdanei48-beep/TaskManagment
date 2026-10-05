import type { Card as CardData, Priority } from '../types'
import { LabelChip } from './LabelChip'

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
interface Props {
  card: CardData
  done: boolean
  dragging: boolean
  onClick: () => void
  onDragStart: () => void
  onDragEnd: () => void
}

export function Card({ card, done, dragging, onClick, onDragStart, onDragEnd }: Props) {
  const overdue = !done && card.dueDate !== null && card.dueDate < todayString()
  const className = ['card', overdue && 'overdue', dragging && 'dragging'].filter(Boolean).join(' ')
  return (
    <div
      className={className}
      data-card-id={card.id}
      draggable
      onDragStart={(e) => {
        // Firefox はデータを設定しないとドラッグが始まらない
        e.dataTransfer.setData('text/plain', String(card.id))
        e.dataTransfer.effectAllowed = 'move'
        onDragStart()
      }}
      onDragEnd={onDragEnd}
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
      {card.labels.length > 0 && (
        <div className="label-list">
          {card.labels.map((label) => (
            <LabelChip key={label.id} label={label} />
          ))}
        </div>
      )}
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
