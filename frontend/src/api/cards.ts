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
