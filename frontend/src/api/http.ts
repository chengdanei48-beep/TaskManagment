export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// Spring Security が配布する XSRF-TOKEN Cookie を、更新系リクエストのヘッダに載せる
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/** サーバーのエラー応答(ProblemDetail)の detail を取り出す。読めなければ状態に応じた定型文を返す。 */
async function errorMessage(res: Response): Promise<string> {
  try {
    const problem: unknown = await res.json()
    if (typeof problem === 'object' && problem !== null && 'detail' in problem) {
      const { detail } = problem
      if (typeof detail === 'string' && detail !== '') return detail
    }
  } catch {
    // 本文が JSON でない場合は定型文にする
  }
  return res.status >= 500
    ? 'サーバーでエラーが発生しました。時間をおいてもう一度お試しください'
    : '処理に失敗しました'
}

/** JSON API を呼ぶ。2xx 以外は ApiError を投げる。204 の場合は undefined を返す。 */
export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })
  if (!res.ok) {
    throw new ApiError(res.status, await errorMessage(res))
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
