import { describe, expect, it } from 'vitest'
import { bucketDuration, chartCeiling, chartDomain, chartTimeLabels } from '../usage-chart'

describe('usage chart time axis', () => {
  it('does not extend the final hourly bucket to a full day in a mixed range', () => {
    const domain = chartDomain([
      { bucketStart: '2026-06-01T00:00:00Z', resolution: 'DAY' },
      { bucketStart: '2026-09-10T01:00:00Z', resolution: 'HOUR' },
    ], undefined, undefined, 'UTC')
    expect(domain.end).toBe(Date.parse('2026-09-10T02:00:00Z'))
  })
  it('keeps sparse calls at their time within the entire selected range', () => {
    const domain = chartDomain(
      [{ bucketStart: '2026-09-10T00:00:00Z', resolution: 'DAY' }],
      '2026-09-01T00:00:00Z',
      '2026-09-11T00:00:00Z',
      'UTC',
    )
    expect(domain.end - domain.start).toBe(10 * 86400000)
    expect((Date.parse('2026-09-10T00:00:00Z') - domain.start) / (domain.end - domain.start)).toBe(
      0.9,
    )
  })
  it.each([
    ['2026-03-08T05:00:00Z', 23],
    ['2026-11-01T04:00:00Z', 25],
  ])('uses the calendar length of the DST day %s', (start, hours) => {
    expect(bucketDuration('DAY', start, 'America/New_York')).toBe(hours * 3600000)
    const domain = chartDomain([{ bucketStart: start, resolution: 'DAY' }], undefined, undefined, 'America/New_York')
    expect(domain.end - domain.start).toBe(hours * 3600000)
  })

  it.each([
    ['2026-04-04T14:00:00Z', 60],
    ['2026-04-04T15:00:00Z', 30],
    ['2026-10-03T15:30:00Z', 30],
  ])('ends Lord Howe hour buckets at the next boundary for %s', (start, minutes) => {
    expect(bucketDuration('HOUR', start, 'Australia/Lord_Howe')).toBe(minutes * 60000)
  })

  it('keeps local axis minutes and distinguishes repeated DST hours', () => {
    expect(chartTimeLabels([new Date('2026-07-31T18:15:00Z')], 'HOUR', 'Asia/Kathmandu')).toEqual(['08/01 00:00'])
    expect(chartTimeLabels([new Date('2026-07-31T16:00:00Z')], 'DAY', 'Asia/Shanghai')).toEqual(['08/01'])
    expect(chartTimeLabels([
      new Date('2026-11-01T05:00:00Z'), new Date('2026-11-01T06:00:00Z'),
    ], 'HOUR', 'America/New_York')).toEqual(['11/01 01:00 GMT-04:00', '11/01 01:00 GMT-05:00'])
  })

  it('includes the bucket day and uses a nonzero readable scale', () => {
    const domain = chartDomain(
      [{ bucketStart: '2026-09-01T00:00:00Z', resolution: 'DAY' }],
      '2026-09-01T12:00:00Z',
      '2026-09-01T20:00:00Z',
      'UTC',
    )
    expect(domain.end - domain.start).toBe(86400000)
    expect(chartCeiling(0)).toBe(1)
    expect(chartCeiling(1760)).toBe(2000)
  })
})
