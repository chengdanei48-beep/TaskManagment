import type { BoardColumn, Card, CardFilter } from '../types'
import { request } from './http'

export function fetchColumns(signal?: AbortSignal): Promise<BoardColumn[]> {
  return request<BoardColumn[]>('/api/columns', { signal })
}

export function fetchCards(filter: CardFilter, signal?: AbortSignal): Promise<Card[]> {
  const params = new URLSearchParams()
  if (filter.columnId !== undefined) params.set('columnId', String(filter.columnId))
  if (filter.priority) params.set('priority', filter.priority)
  if (filter.keyword) params.set('keyword', filter.keyword)
  const query = params.toString()
  return request<Card[]>(query ? `/api/cards?${query}` : '/api/cards', { signal })
}
