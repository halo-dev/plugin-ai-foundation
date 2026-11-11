import { describe, expect, it } from 'vitest'
import { heatmapGrid, heatmapLeveler, heatmapMonthLabels, heatmapRange } from '../usage-heatmap'
import { parseDate } from '@internationalized/date'

describe('usage-heatmap utils', () => {
  it('builds a 53-week grid aligned to Monday with today as the last day', () => {
    const days = heatmapGrid(new Date('2026-10-13T08:00:00Z'), 'UTC')
    const weekday = (new Date('2026-10-13T00:00:00Z').getUTCDay() + 6) % 7
    expect(days).toHaveLength(52 * 7 + weekday + 1)
    expect(days.at(-1)?.date).toBe('2026-10-13')
    // The first column starts on a Monday and no row goes past today
    expect(days[0]?.weekday).toBe(0)
    expect(days[0]?.week).toBe(0)
    // Each Monday starts a new column
    const weekStarts = days.filter((day) => day.weekday === 0)
    expect(weekStarts).toHaveLength(53)
    expect(new Set(days.map((day) => day.week)).size).toBe(53)
  })

  it('labels a month only at the first week it appears', () => {
    const days = heatmapGrid(new Date('2026-10-13T00:00:00Z'), 'UTC')
    const labels = heatmapMonthLabels(days)
    const labeled = labels.filter(Boolean)
    // 53 weeks span 13 months (both ends in October); labels appear only on month transitions
    expect(labeled.length).toBeGreaterThanOrEqual(12)
    for (let i = 1; i < labeled.length; i++) {
      expect(labeled[i]).not.toBe(labeled[i - 1])
    }
    expect(labeled.at(-1)).toBe('10')
  })

  it.each([
    ['Asia/Shanghai', '2026-10-13T16:01:00Z', '2026-10-14', '2025-10-12T16:00:00.000Z'],
    ['Asia/Kathmandu', '2026-10-13T18:16:00Z', '2026-10-14', '2025-10-12T18:15:00.000Z'],
    ['America/New_York', '2026-11-02T04:59:00Z', '2026-11-01', '2025-10-27T04:00:00.000Z'],
    ['America/New_York', '2026-03-09T03:59:00Z', '2026-03-08', '2025-03-03T05:00:00.000Z'],
  ])('covers every local date and its entire first day in %s', (timeZone, iso, last, from) => {
    const anchor = new Date(iso)
    const days = heatmapGrid(anchor, timeZone)
    const range = heatmapRange(anchor, timeZone)
    expect(range).toEqual({ from, to: anchor.toISOString() })
    expect(days.at(-1)?.date).toBe(last)
    expect(range.from).toBe(parseDate(days[0]!.date).toDate(timeZone).toISOString())
    for (let index = 1; index < days.length; index++) {
      expect(days[index]!.date).toBe(parseDate(days[index - 1]!.date).add({ days: 1 }).toString())
    }
    expect(new Set(days.map((day) => day.date)).size).toBe(days.length)
  })

  it('returns level 0 for empty data and zero values', () => {
    const level = heatmapLeveler([])
    expect(level(0)).toBe(0)
    expect(level(100)).toBe(0)
    expect(heatmapLeveler([0, 0])(0)).toBe(0)
    expect(heatmapLeveler([1, 2])(0)).toBe(0)
  })

  it('falls back to linear levels with fewer than 8 non-zero values', () => {
    const level = heatmapLeveler([10, 20, 40])
    expect(level(0)).toBe(0)
    expect(level(10)).toBe(1)
    expect(level(20)).toBe(2)
    expect(level(30)).toBe(3)
    expect(level(40)).toBe(4)
    expect(level(400)).toBe(4)
  })

  it('uses quartile levels with 8 or more non-zero values', () => {
    const level = heatmapLeveler([10, 20, 30, 40, 50, 60, 70, 80, 90, 100])
    expect(level(0)).toBe(0)
    expect(level(10)).toBe(1)
    expect(level(30)).toBeLessThanOrEqual(2)
    expect(level(100)).toBe(4)
    // Levels are monotonic
    const levels = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100].map(level)
    for (let i = 1; i < levels.length; i++) {
      expect(levels[i]!).toBeGreaterThanOrEqual(levels[i - 1]!)
    }
  })
})
