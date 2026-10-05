import type { BoardColumn, Card, CardFilter, CardInput } from '../types'
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

export function createCard(input: CardInput): Promise<Card> {
  return request<Card>('/api/cards', { method: 'POST', body: input })
}

export function updateCard(id: number, input: CardInput): Promise<Card> {
  return request<Card>(`/api/cards/${id}`, { method: 'PUT', body: input })
}

export function deleteCard(id: number): Promise<void> {
  return request<void>(`/api/cards/${id}`, { method: 'DELETE' })
}

/** カードを columnId の列へ移す。beforeCardId のカードの手前に入れ、null なら末尾に置く。 */
export function moveCard(id: number, columnId: number, beforeCardId: number | null): Promise<void> {
  return request<void>(`/api/cards/${id}/move`, {
    method: 'PUT',
    body: { columnId, beforeCardId },
  })
}

export type SortKey = 'PRIORITY' | 'DUE_DATE'

/** 列内のカードを並び替え、結果をサーバーに保存する。 */
export function sortColumn(columnId: number, by: SortKey): Promise<void> {
  return request<void>(`/api/columns/${columnId}/sort`, { method: 'PUT', body: { by } })
}

export function createColumn(name: string): Promise<BoardColumn> {
  return request<BoardColumn>('/api/columns', { method: 'POST', body: { name } })
}

/** 列を削除する。列内のカードも一緒に削除される。 */
export function deleteColumn(id: number): Promise<void> {
  return request<void>(`/api/columns/${id}`, { method: 'DELETE' })
}
