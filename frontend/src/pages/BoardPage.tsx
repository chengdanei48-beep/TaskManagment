import { useEffect, useMemo, useState } from 'react'
import { fetchCards, fetchColumns } from '../api/cards'
import { Column } from '../components/Column'
import { SearchBar, type SearchState } from '../components/SearchBar'
import type { BoardColumn, Card } from '../types'

const DEBOUNCE_MS = 300

interface CardsResult {
  key: string
  cards: Card[]
  error: string | null
}

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

export function BoardPage() {
  const [search, setSearch] = useState<SearchState>({ keyword: '', priority: '', columnId: '' })
  const [columns, setColumns] = useState<BoardColumn[]>([])
  const [columnsError, setColumnsError] = useState<string | null>(null)
  const [result, setResult] = useState<CardsResult | null>(null)

  const debouncedKeyword = useDebounced(search.keyword.trim(), DEBOUNCE_MS)
  const { priority, columnId } = search
  // 取得結果がどの検索条件のものかを保持し、現在の条件と違えば「読み込み中」とみなす
  const key = JSON.stringify([debouncedKeyword, priority, columnId])

  useEffect(() => {
    const controller = new AbortController()
    fetchColumns(controller.signal)
      .then(setColumns)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setColumnsError(errorMessage(e))
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    fetchCards(
      {
        keyword: debouncedKeyword,
        priority: priority || undefined,
        columnId: columnId === '' ? undefined : columnId,
      },
      controller.signal,
    )
      .then((cards) => setResult({ key, cards, error: null }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setResult({ key, cards: [], error: errorMessage(e) })
      })
    return () => controller.abort()
  }, [key, debouncedKeyword, priority, columnId])

  const loading = result?.key !== key
  const error = columnsError ?? result?.error ?? null

  const cardsByColumn = useMemo(() => {
    const map = new Map<number, Card[]>()
    for (const card of result?.cards ?? []) {
      const list = map.get(card.columnId) ?? []
      list.push(card)
      map.set(card.columnId, list)
    }
    for (const list of map.values()) list.sort((a, b) => a.position - b.position)
    return map
  }, [result])

  return (
    <main className="page">
      <h1>ボード</h1>
      <SearchBar value={search} columns={columns} onChange={setSearch} />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {loading && !error && <p className="status">読み込み中...</p>}
      <div className="board">
        {columns.map((column) => (
          <Column key={column.id} column={column} cards={cardsByColumn.get(column.id) ?? []} />
        ))}
      </div>
    </main>
  )
}
