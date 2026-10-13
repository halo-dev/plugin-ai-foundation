export interface UsageHeatmapDay {
  /** YYYY-MM-DD (UTC) */
  date: string
  /** Column index 0..52, each column starting on a Monday */
  week: number
  /** Row index: 0 (Monday)..6 (Sunday) */
  weekday: number
}

export const USAGE_HEATMAP_WEEKS = 53

/**
 * Builds a 53-week × 7-day grid (UTC, Monday on the first row) ending at today.
 * The first column starts on the Monday of the week 52 weeks ago so the grid stays aligned.
 */
export function heatmapGrid(today: Date): UsageHeatmapDay[] {
  const end = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate())
  const weekday = (new Date(end).getUTCDay() + 6) % 7
  const weekStart = end - weekday * 86400000
  const firstStart = weekStart - (USAGE_HEATMAP_WEEKS - 1) * 7 * 86400000
  const days: UsageHeatmapDay[] = []
  for (let time = firstStart; time <= end; time += 86400000) {
    const date = new Date(time)
    days.push({
      date: date.toISOString().slice(0, 10),
      week: Math.floor((time - firstStart) / (7 * 86400000)),
      weekday: (date.getUTCDay() + 6) % 7,
    })
  }
  return days
}

/** Month label for the first cell of each column (week); undefined when the month matches the previous column. */
export function heatmapMonthLabels(days: UsageHeatmapDay[]): (string | undefined)[] {
  const labels: (string | undefined)[] = Array.from({ length: USAGE_HEATMAP_WEEKS }, () => undefined)
  let lastMonth: string | undefined
  for (const day of days) {
    if (day.weekday !== 0) continue
    const month = day.date.slice(5, 7)
    if (month !== lastMonth) {
      labels[day.week] = month
      lastMonth = month
    }
  }
  return labels
}

/**
 * Five levels: zero maps to level 0, non-zero values map to levels 1–4 by quartile;
 * with fewer than 8 non-zero values, falls back to linear scaling against the max.
 */
export function heatmapLeveler(values: number[]): (value: number) => number {
  const nonZero = values.filter((value) => value > 0).sort((a, b) => a - b)
  if (!nonZero.length) {
    return () => 0
  }
  const max = nonZero[nonZero.length - 1]
  if (nonZero.length < 8) {
    return (value) =>
      value <= 0 ? 0 : Math.min(4, Math.max(1, Math.ceil((value / max) * 4)))
  }
  const quantile = (q: number) =>
    nonZero[Math.min(nonZero.length - 1, Math.floor(q * nonZero.length))]
  const q1 = quantile(0.25)
  const q2 = quantile(0.5)
  const q3 = quantile(0.75)
  return (value) => (value <= 0 ? 0 : value <= q1 ? 1 : value <= q2 ? 2 : value <= q3 ? 3 : 4)
}
