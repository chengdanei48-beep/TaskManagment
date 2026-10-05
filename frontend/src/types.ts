export type Priority = 'HIGH' | 'MEDIUM' | 'LOW'

export interface Card {
  id: number
  columnId: number
  title: string
  description: string | null
  dueDate: string | null
  priority: Priority | null
  position: number
  createdAt: string
}

export interface BoardColumn {
  id: number
  name: string
  position: number
}

export interface User {
  id: number
  username: string
}

export interface CardFilter {
  columnId?: number
  priority?: Priority
  keyword?: string
}
