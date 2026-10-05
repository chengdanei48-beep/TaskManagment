import { useEffect, useMemo, useState } from 'react'
import {
  createCard,
  createColumn,
  deleteColumn,
  deleteCard,
  fetchCards,
  fetchColumns,
  moveCard,
  sortColumn,
  type SortKey,
  updateCard,
} from '../api/cards'
import { ApiError } from '../api/http'
import { createLabel, deleteLabel, fetchLabels } from '../api/labels'
import { useAuth } from '../auth/AuthContext'
import { CardDialog, type DialogTarget } from '../components/CardDialog'
import { AddColumn } from '../components/AddColumn'
import { Column } from '../components/Column'
import { SearchBar, type SearchState } from '../components/SearchBar'
import { COLUMN_LIMIT, type BoardColumn, type Card, type Label, type LabelInput } from '../types'

const DEBOUNCE_MS = 300

interface DropTarget {
  columnId: number
  /** この手前に挿入する。null は列の末尾 */
  beforeCardId: number | null
}

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
  const { user, logout, expire } = useAuth()
  const [search, setSearch] = useState<SearchState>({ keyword: '', priority: '', columnId: '' })
  const [columns, setColumns] = useState<BoardColumn[]>([])
  const [columnsError, setColumnsError] = useState<string | null>(null)
  const [labels, setLabels] = useState<Label[]>([])
  const [result, setResult] = useState<CardsResult | null>(null)
  const [dialog, setDialog] = useState<DialogTarget | null>(null)
  // カードの追加・編集・削除のたびに増やし、現在の検索条件のまま一覧を取り直す
  const [reload, setReload] = useState(0)
  const [draggingCardId, setDraggingCardId] = useState<number | null>(null)
  const [dropTarget, setDropTarget] = useState<DropTarget | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const debouncedKeyword = useDebounced(search.keyword.trim(), DEBOUNCE_MS)
  const { priority, columnId } = search
  // 取得結果がどの検索条件のものかを保持し、現在の条件と違えば「読み込み中」とみなす
  const key = JSON.stringify([debouncedKeyword, priority, columnId, reload])

  useEffect(() => {
    const controller = new AbortController()
    fetchColumns(controller.signal)
      .then(setColumns)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        // セッション切れならログイン画面へ戻す
        if (e instanceof ApiError && e.status === 401) expire()
        else setColumnsError(errorMessage(e))
      })
    fetchLabels(controller.signal)
      .then(setLabels)
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        if (e instanceof ApiError && e.status === 401) expire()
        else setColumnsError(errorMessage(e))
      })
    return () => controller.abort()
  }, [expire, reload])

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
        if (controller.signal.aborted) return
        if (e instanceof ApiError && e.status === 401) expire()
        else setResult({ key, cards: [], error: errorMessage(e) })
      })
    return () => controller.abort()
  }, [key, debouncedKeyword, priority, columnId, expire])

  // 更新系の操作。セッション切れ(401)ならログイン画面へ戻し、エラーはダイアログ側で表示する
  async function mutate(action: () => Promise<unknown>) {
    try {
      await action()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) expire()
      throw e
    }
    setDialog(null)
    setReload((n) => n + 1)
  }

  // ラベルの作成・削除。ダイアログは開いたままにし、一覧とカード表示だけ取り直す
  async function handleCreateLabel(input: LabelInput): Promise<Label> {
    try {
      const created = await createLabel(input)
      setLabels((prev) => [...prev, created])
      return created
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) expire()
      throw e
    }
  }

  async function handleDeleteLabel(id: number): Promise<void> {
    try {
      await deleteLabel(id)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) expire()
      throw e
    }
    setLabels((prev) => prev.filter((l) => l.id !== id))
    setReload((n) => n + 1)
  }

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

  // 元の位置と変わらないドロップは、位置表示も保存もしない
  function isNoop(target: DropTarget): boolean {
    const list = cardsByColumn.get(target.columnId) ?? []
    const index = list.findIndex((c) => c.id === draggingCardId)
    return index >= 0 && (list[index + 1]?.id ?? null) === target.beforeCardId
  }
  const effectiveDrop = draggingCardId !== null && dropTarget && !isNoop(dropTarget) ? dropTarget : null

  function handleDragOverPosition(targetColumnId: number, beforeCardId: number | null) {
    // dragover は高頻度で発火するため、位置が変わったときだけ更新する
    setDropTarget((prev) =>
      prev && prev.columnId === targetColumnId && prev.beforeCardId === beforeCardId
        ? prev
        : { columnId: targetColumnId, beforeCardId },
    )
  }

  function endDrag() {
    setDraggingCardId(null)
    setDropTarget(null)
  }

  async function handleDeleteColumn(column: BoardColumn) {
    // カードがある列だけ確認する(要件 C-3)。件数は絞り込みに関係なく全件
    if (
      column.cardCount > 0 &&
      !window.confirm(
        `「${column.name}」には ${column.cardCount} 件のカードがあります。カードも一緒に削除されます。よろしいですか?`,
      )
    ) {
      return
    }
    setActionError(null)
    try {
      await mutate(() => deleteColumn(column.id))
      // 削除した列で絞り込み中なら解除する(存在しない列での検索を防ぐ)
      setSearch((s) => (s.columnId === column.id ? { ...s, columnId: '' } : s))
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) {
        setActionError('列を削除できませんでした。画面を更新してもう一度お試しください')
      }
    }
  }

  async function handleSort(targetColumnId: number, by: SortKey) {
    setActionError(null)
    try {
      await mutate(() => sortColumn(targetColumnId, by))
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) {
        setActionError('並び替えできませんでした。画面を更新してもう一度お試しください')
      }
    }
  }

  async function handleDrop() {
    const cardId = draggingCardId
    const target = effectiveDrop
    endDrag()
    if (cardId === null || !target) return
    setActionError(null)
    try {
      await mutate(() => moveCard(cardId, target.columnId, target.beforeCardId))
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) {
        setActionError('カードを移動できませんでした。画面を更新してもう一度お試しください')
      }
    }
  }

  return (
    <main className="page">
      <header className="page-head">
        <h1>ボード</h1>
        <div className="user-menu">
          <span>{user?.username}</span>
          <button type="button" onClick={() => void logout()}>
            ログアウト
          </button>
        </div>
      </header>
      <SearchBar value={search} columns={columns} onChange={setSearch} />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {actionError && (
        <p className="error" role="alert">
          {actionError}
        </p>
      )}
      {loading && !error && <p className="status">読み込み中...</p>}
      <div className="board">
        {columns.map((column) => (
          <Column
            key={column.id}
            column={column}
            cards={cardsByColumn.get(column.id) ?? []}
            onAdd={(c) => setDialog({ mode: 'create', columnId: c.id, columnName: c.name })}
            onEdit={(card) => setDialog({ mode: 'edit', card })}
            draggingCardId={draggingCardId}
            dropBefore={effectiveDrop?.columnId === column.id ? effectiveDrop.beforeCardId : undefined}
            onDragStart={(cardId) => {
              setActionError(null)
              setDraggingCardId(cardId)
            }}
            onDragEnd={endDrag}
            onDragOverPosition={handleDragOverPosition}
            onDragLeaveColumn={() => setDropTarget(null)}
            onDrop={() => void handleDrop()}
            onSort={(by) => void handleSort(column.id, by)}
            onDelete={() => void handleDeleteColumn(column)}
          />
        ))}
        {columns.length < COLUMN_LIMIT && (
          <AddColumn onAdd={(name) => mutate(() => createColumn(name))} />
        )}
      </div>
      {dialog && (
        <CardDialog
          // 対象が変わったら入力欄を初期化する
          key={dialog.mode === 'edit' ? `edit-${dialog.card.id}` : `create-${dialog.columnId}`}
          target={dialog}
          labels={labels}
          onCreateLabel={handleCreateLabel}
          onDeleteLabel={handleDeleteLabel}
          onClose={() => setDialog(null)}
          onSave={(input) =>
            mutate(() =>
              dialog.mode === 'edit' ? updateCard(dialog.card.id, input) : createCard(input),
            )
          }
          onDelete={
            dialog.mode === 'edit' ? () => mutate(() => deleteCard(dialog.card.id)) : undefined
          }
        />
      )}
    </main>
  )
}
