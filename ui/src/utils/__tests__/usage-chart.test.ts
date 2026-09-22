import { describe, expect, it } from 'vitest'
import { chartCeiling, chartDomain } from '../usage-chart'

describe('usage chart time axis', () => {
  it('does not extend the final hourly bucket to a full day in a mixed range', () => {
    const domain = chartDomain([
      { bucketStart: '2026-06-01T00:00:00Z', resolution: 'DAY' },
      { bucketStart: '2026-09-10T01:00:00Z', resolution: 'HOUR' },
    ])
    expect(domain.end).toBe(Date.parse('2026-09-10T02:00:00Z'))
  })
  it('keeps sparse calls at their time within the entire selected range', () => {
    const domain = chartDomain(
      [{ bucketStart: '2026-09-10T00:00:00Z', resolution: 'DAY' }],
      '2026-09-01T00:00:00Z',
      '2026-09-11T00:00:00Z',
    )
    expect(domain.end - domain.start).toBe(10 * 86400000)
    expect((Date.parse('2026-09-10T00:00:00Z') - domain.start) / (domain.end - domain.start)).toBe(
      0.9,
    )
  })
  it('includes the complete archived day and uses a nonzero readable scale', () => {
    const domain = chartDomain(
      [{ bucketStart: '2026-09-01T00:00:00Z', resolution: 'DAY' }],
      '2026-09-01T12:00:00Z',
      '2026-09-01T20:00:00Z',
    )
    expect(domain.end - domain.start).toBe(86400000)
    expect(chartCeiling(0)).toBe(1)
    expect(chartCeiling(1760)).toBe(2000)
  })
})
