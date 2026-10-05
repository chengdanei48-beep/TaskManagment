import type { BoardColumn, Card, CardFilter } from '../types'

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(url, { signal })
  if (!res.ok) {
    throw new Error(`${url} の取得に失敗しました (HTTP ${res.status})`)
  }
  return res.json() as Promise<T>
}

export function fetchColumns(signal?: AbortSignal): Promise<BoardColumn[]> {
  return getJson<BoardColumn[]>('/api/columns', signal)
}

export function fetchCards(filter: CardFilter, signal?: AbortSignal): Promise<Card[]> {
  const params = new URLSearchParams()
  if (filter.columnId !== undefined) params.set('columnId', String(filter.columnId))
  if (filter.priority) params.set('priority', filter.priority)
  if (filter.keyword) params.set('keyword', filter.keyword)
  const query = params.toString()
  return getJson<Card[]>(query ? `/api/cards?${query}` : '/api/cards', signal)
}
