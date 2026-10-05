import type { Label } from '../types'

/** 背景色の明るさに応じて、読みやすい文字色(黒/白)を返す。 */
function textColor(background: string): string {
  const r = parseInt(background.slice(1, 3), 16)
  const g = parseInt(background.slice(3, 5), 16)
  const b = parseInt(background.slice(5, 7), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? '#172b4d' : '#ffffff'
}

export function LabelChip({ label }: { label: Pick<Label, 'name' | 'color'> }) {
  return (
    <span className="label-chip" style={{ background: label.color, color: textColor(label.color) }}>
      {label.name}
    </span>
  )
}
