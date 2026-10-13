import { describe, expect, it } from 'vitest'
import { heatmapGrid, heatmapLeveler, heatmapMonthLabels } from '../usage-heatmap'

describe('usage-heatmap utils', () => {
  it('builds a 53-week grid aligned to Monday with today as the last day', () => {
    const days = heatmapGrid(new Date('2026-10-13T08:00:00Z'))
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
    const days = heatmapGrid(new Date('2026-10-13T00:00:00Z'))
    const labels = heatmapMonthLabels(days)
    const labeled = labels.filter(Boolean)
    // 53 weeks span 13 months (both ends in October); labels appear only on month transitions
    expect(labeled.length).toBeGreaterThanOrEqual(12)
    for (let i = 1; i < labeled.length; i++) {
      expect(labeled[i]).not.toBe(labeled[i - 1])
    }
    expect(labeled.at(-1)).toBe('10')
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
