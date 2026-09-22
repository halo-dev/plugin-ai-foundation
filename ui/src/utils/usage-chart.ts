import type { UsageTrendPoint } from '@/api/generated'

export function bucketDuration(resolution?: string) {
  return resolution === 'HOUR' ? 3600000 : 86400000
}

export function chartDomain(points: UsageTrendPoint[], from?: string, to?: string) {
  const buckets = points
    .map((point) => ({
      start: Date.parse(point.bucketStart || ''),
      step: bucketDuration(point.resolution),
    }))
    .filter((point) => Number.isFinite(point.start))
  const step = buckets.length ? Math.min(...buckets.map((point) => point.step)) : 86400000
  const first = buckets.length ? Math.min(...buckets.map((point) => point.start)) : Date.now()
  const last = buckets.length
    ? Math.max(...buckets.map((point) => point.start + point.step))
    : first + step
  const start =
    from && Number.isFinite(Date.parse(from)) ? Math.min(Date.parse(from), first) : first
  const end = to && Number.isFinite(Date.parse(to)) ? Math.max(Date.parse(to), last) : last
  return { start, end: Math.max(start + step, end), step }
}

export function chartCeiling(value: number) {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  return Math.ceil(value / power) * power
}

export function compactChartNumber(value: number) {
  return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(
    value,
  )
}
