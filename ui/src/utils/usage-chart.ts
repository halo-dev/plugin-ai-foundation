import type { UsageTrendPoint } from '@/api/generated'
import { formatBucketStart, type UsageTrendResolution } from '@/utils/usage'
import { fromDate, toCalendarDate } from '@internationalized/date'

export function bucketDuration(
  resolution?: string,
  bucketStart?: string,
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
) {
  if (!bucketStart || !Number.isFinite(Date.parse(bucketStart))) {
    return resolution === 'HOUR' ? 3600000 : 86400000
  }
  const start = new Date(bucketStart)
  const local = fromDate(start, timeZone)
  if (resolution === 'HOUR') {
    let end = start.getTime() + (60 - local.minute) * 60000
    // An offset transition can start a partial hour, including Lord Howe's 30-minute change.
    if (fromDate(new Date(end), timeZone).offset !== local.offset) {
      let before = start.getTime()
      while (end - before > 1) {
        const middle = Math.floor((before + end) / 2)
        if (fromDate(new Date(middle), timeZone).offset === local.offset) before = middle
        else end = middle
      }
    }
    return end - start.getTime()
  }
  const nextDay = toCalendarDate(local).add({ days: 1 }).toDate(timeZone)
  return nextDay.getTime() - start.getTime()
}

export function chartTimeLabels(
  dates: Date[],
  resolution: UsageTrendResolution,
  timeZone: string,
) {
  const labels = dates.map((date) => formatBucketStart(date.toISOString(), resolution, timeZone))
  return labels.map((label, index) => {
    const day = label.slice(5, 10).replace('-', '/')
    if (resolution === 'DAY') return day
    const repeated = labels.some((other, otherIndex) =>
      otherIndex !== index && other.slice(0, 16) === label.slice(0, 16) && other !== label,
    )
    return `${day} ${label.slice(11, 16)}${repeated ? ` ${label.split(' ')[2]}` : ''}`
  })
}

export function chartDomain(
  points: UsageTrendPoint[],
  from?: string,
  to?: string,
  timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone,
) {
  const buckets = points
    .map((point) => ({
      start: Date.parse(point.bucketStart || ''),
      step: bucketDuration(point.resolution, point.bucketStart, timeZone),
    }))
    .filter((point) => Number.isFinite(point.start))
  const step = buckets.length ? Math.min(...buckets.map((point) => point.step)) : 86400000
  const first = buckets.length ? Math.min(...buckets.map((point) => point.start)) : Date.now()
  const last = buckets.length
    ? Math.max(...buckets.map((point) => point.start + point.step))
    : first + step
  const requestedStart = parseBoundary(from, first)
  const requestedEnd = parseBoundary(to, last)
  if (!buckets.length && requestedStart < requestedEnd) {
    return { start: requestedStart, end: requestedEnd, step }
  }
  const start = Math.min(requestedStart, first)
  const end = Math.max(requestedEnd, last)
  return { start, end: Math.max(start + step, end), step }
}

function parseBoundary(value: string | undefined, fallback: number) {
  if (!value) return fallback
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return fallback
  return timestamp
}

export function chartCeiling(value: number) {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  return Math.ceil(value / power) * power
}

const COMPACT_FORMATTER = new Intl.NumberFormat('en', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

export function compactChartNumber(value: number) {
  return COMPACT_FORMATTER.format(value)
}

// Dimension breakdown palette (primary #4CCBA0); categories beyond Top N merge into a gray Other
const USAGE_CHART_PALETTE = [
  '#4CCBA0',
  '#0ea5e9',
  '#10b981',
  '#f59e0b',
  '#ec4899',
  '#8b5cf6',
  '#64748b',
] as const

export const USAGE_CHART_OTHER_COLOR = '#cbd5e1'

export const USAGE_CHART_TOP_N = USAGE_CHART_PALETTE.length

export function usageCategoryColor(key: string, categories: string[]) {
  const index = categories.indexOf(key)
  return index >= 0 && index < USAGE_CHART_PALETTE.length
    ? USAGE_CHART_PALETTE[index]
    : USAGE_CHART_OTHER_COLOR
}
