import { fromDate, startOfWeek, toCalendarDate } from '@internationalized/date'

interface UsageHeatmapDay {
  /** YYYY-MM-DD in the query time zone */
  date: string
  /** Column index 0..52, each column starting on a Monday */
  week: number
  /** Row index: 0 (Monday)..6 (Sunday) */
  weekday: number
}

const USAGE_HEATMAP_WEEKS = 53

function heatmapStart(anchor: Date, timeZone: string) {
  const date = toCalendarDate(fromDate(anchor, timeZone))
  return startOfWeek(date, 'zh-CN', 'mon').subtract({ weeks: USAGE_HEATMAP_WEEKS - 1 })
}

/** The first visible local day is complete; today includes data up to the shared refresh instant. */
export function heatmapRange(anchor: Date, timeZone: string) {
  return {
    from: heatmapStart(anchor, timeZone).toDate(timeZone).toISOString(),
    to: anchor.toISOString(),
  }
}

/** Builds 53 Monday-aligned calendar weeks through the anchor's local date, including DST days. */
export function heatmapGrid(
  anchor: Date,
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
): UsageHeatmapDay[] {
  const end = toCalendarDate(fromDate(anchor, timeZone))
  const start = heatmapStart(anchor, timeZone)
  const days: UsageHeatmapDay[] = []
  for (let date = start, index = 0; date.compare(end) <= 0; date = date.add({ days: 1 }), index++) {
    days.push({ date: date.toString(), week: Math.floor(index / 7), weekday: index % 7 })
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
