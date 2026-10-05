/** 'YYYY-MM-DD' を画面表示用の 'YYYY/MM/DD' にする。 */
export function formatDate(date: string): string {
  return date.replaceAll('-', '/')
}

/** サーバーの日時('YYYY-MM-DDTHH:mm:ss...')を 'YYYY/MM/DD HH:mm' にする。 */
export function formatDateTime(dateTime: string): string {
  const [date = '', time = ''] = dateTime.split('T')
  return `${formatDate(date)} ${time.slice(0, 5)}`.trim()
}
