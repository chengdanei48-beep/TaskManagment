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
  /** 列内のカード数(絞り込みに関係なく全件) */
  cardCount: number
}

/** 列の上限数(要件 C-2) */
export const COLUMN_LIMIT = 10
/** 列名の最大文字数(要件 C-2) */
export const COLUMN_NAME_MAX = 20

export interface User {
  id: number
  username: string
}

export interface CardFilter {
  columnId?: number
  priority?: Priority
  keyword?: string
}

/** カードの作成・更新で送る入力値。columnId は作成時のみ使う。 */
export interface CardInput {
  columnId?: number
  title: string
  description: string | null
  dueDate: string | null
  priority: Priority | null
}
