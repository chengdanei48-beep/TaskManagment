import { request } from './http'

export interface Label {
  id: number
  name: string
  color: string
  createdAt: string
}

export interface LabelInput {
  name: string
  /** #RRGGBB 形式 */
  color: string
}

export function fetchLabels(signal?: AbortSignal): Promise<Label[]> {
  return request<Label[]>('/api/labels', { signal })
}

export function createLabel(input: LabelInput): Promise<Label> {
  return request<Label>('/api/labels', { method: 'POST', body: input })
}

export function deleteLabel(id: number): Promise<void> {
  return request<void>(`/api/labels/${id}`, { method: 'DELETE' })
}
