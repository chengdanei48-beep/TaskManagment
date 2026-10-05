import type { BoardColumn, Priority } from '../types'

export interface SearchState {
  keyword: string
  priority: Priority | ''
  columnId: number | ''
}

interface Props {
  value: SearchState
  columns: BoardColumn[]
  onChange: (value: SearchState) => void
}

export function SearchBar({ value, columns, onChange }: Props) {
  return (
    <div className="search-bar">
      <input
        type="search"
        placeholder="タイトルで検索"
        value={value.keyword}
        onChange={(e) => onChange({ ...value, keyword: e.target.value })}
      />
      <select
        aria-label="優先度"
        value={value.priority}
        onChange={(e) => onChange({ ...value, priority: e.target.value as Priority | '' })}
      >
        <option value="">優先度: すべて</option>
        <option value="HIGH">高</option>
        <option value="MEDIUM">中</option>
        <option value="LOW">低</option>
      </select>
      <select
        aria-label="カラム"
        value={value.columnId}
        onChange={(e) =>
          onChange({ ...value, columnId: e.target.value === '' ? '' : Number(e.target.value) })
        }
      >
        <option value="">カラム: すべて</option>
        {columns.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </select>
    </div>
  )
}
