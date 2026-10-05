import type { Label, LabelInput } from '../types'
import { request } from './http'

export function fetchLabels(signal?: AbortSignal): Promise<Label[]> {
  return request<Label[]>('/api/labels', { signal })
}

export function createLabel(input: LabelInput): Promise<Label> {
  return request<Label>('/api/labels', { method: 'POST', body: input })
}

export function deleteLabel(id: number): Promise<void> {
  return request<void>(`/api/labels/${id}`, { method: 'DELETE' })
}
