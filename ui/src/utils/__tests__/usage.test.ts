import { describe, expect, it } from 'vitest'
import {
  formatBucketStart,
  formatCoverage,
  formatDateTime,
  formatDuration,
  formatTokens,
  usageOperationLabel,
  usageQualityLabel,
  usageStatusTagTheme,
  usageStatusLabel,
  usageUnitKindLabel,
} from '../usage'

describe('usage utils', () => {
  it('renders null token fields as 未知 instead of zero', () => {
    expect(formatTokens(undefined)).toBe('未知')
    expect(formatTokens(null)).toBe('未知')
    expect(formatTokens(0)).toBe('0')
    expect(formatTokens(1234567)).toBe('1,234,567')
  })

  it('renders coverage as percentage and keeps 未知 explicit', () => {
    expect(formatCoverage(undefined)).toBe('未知')
    expect(formatCoverage(null)).toBe('未知')
    expect(formatCoverage(1)).toBe('100%')
    expect(formatCoverage(0.852)).toBe('85.2%')
    expect(formatCoverage(0)).toBe('0%')
  })

  it('renders all statuses with labels and semantic tag themes', () => {
    const statuses = [
      'IN_PROGRESS',
      'SUCCEEDED',
      'FAILED',
      'TIMED_OUT',
      'CANCELLED',
      'ABANDONED',
    ] as const
    const labels = new Set(statuses.map((status) => usageStatusLabel(status)))
    expect(labels.size).toBe(statuses.length)
    expect(usageStatusTagTheme('FAILED')).toBe('danger')
    expect(usageStatusTagTheme('SUCCEEDED')).toBe('primary')
    expect(usageStatusLabel('CANCELLED')).toBe('已取消')
    expect(usageStatusLabel('TIMED_OUT')).toBe('超时')
    expect(usageStatusLabel('ABANDONED')).toBe('已废弃')
    expect(usageStatusLabel('UNKNOWN_VALUE')).toBe('UNKNOWN_VALUE')
    expect(usageStatusLabel(undefined)).toBe('未知')
  })

  it('renders usage qualities including PARTIAL and MISSING', () => {
    expect(usageQualityLabel('REPORTED_COMPONENTS')).toBe('供应商分项报告')
    expect(usageQualityLabel('REPORTED_TOTAL')).toBe('供应商报告总量')
    expect(usageQualityLabel('PARTIAL')).toBe('部分用量')
    expect(usageQualityLabel('MISSING')).toBe('用量缺失')
    expect(usageQualityLabel(undefined)).toBe('未知')
  })

  it('renders operation and unit kind labels', () => {
    expect(usageOperationLabel('language.streamText')).toBe('流式文本生成')
    expect(usageUnitKindLabel('GENERATION_STEP')).toBe('生成步骤')
    expect(usageUnitKindLabel('IMAGE_BATCH')).toBe('图像批次')
  })

  it('formats durations', () => {
    expect(formatDuration(undefined)).toBe('未知')
    expect(formatDuration(350)).toBe('350 ms')
    expect(formatDuration(1200)).toBe('1.2 s')
  })

  it.each([
    ['Asia/Shanghai', '2026-07-31T16:00:00Z', '2026-08-01', '2026-08-01 00:00:00 GMT+08:00'],
    ['Asia/Kathmandu', '2026-07-31T18:15:00Z', '2026-08-01', '2026-08-01 00:00:00 GMT+05:45'],
    ['America/New_York', '2026-11-01T05:00:00Z', '2026-11-01', '2026-11-01 01:00:00 GMT-04:00'],
    ['America/New_York', '2026-11-01T06:00:00Z', '2026-11-01', '2026-11-01 01:00:00 GMT-05:00'],
  ])('formats local bucket dates and offsets in %s', (timeZone, iso, day, hour) => {
    expect(formatBucketStart(iso, 'DAY', timeZone)).toBe(day)
    expect(formatBucketStart(iso, 'HOUR', timeZone)).toBe(hour)
  })

  it('formats datetimes and day-resolution buckets without hourly precision', () => {
    const iso = '2026-08-01T12:34:56Z'
    const parsed = new Date(iso)
    const pad = (value: number) => String(value).padStart(2, '0')
    const expected =
      `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ` +
      `${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}`
    expect(formatDateTime(iso)).toBe(expected)
    expect(formatDateTime(undefined)).toBe('未知')
    expect(formatDateTime('not-a-date')).toBe('未知')
    expect(formatBucketStart('2026-08-01T00:00:00Z', 'DAY', 'UTC')).toBe('2026-08-01')
    expect(formatBucketStart(iso, 'HOUR')).toMatch(new RegExp(`^${expected} GMT[+-]\\d{2}:\\d{2}$`))
  })
})
