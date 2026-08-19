import { afterEach, describe, expect, it } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { toUsageQueryParams, useUsageFilters } from '../use-usage-filters'

const DAY = 24 * 60 * 60 * 1000
const now = new Date('2026-08-11T06:00:00Z')

describe('toUsageQueryParams', () => {
  it('builds sliding windows for preset ranges', () => {
    const cases: Array<[string, number]> = [
      ['24h', DAY],
      ['7d', 7 * DAY],
      ['30d', 30 * DAY],
      ['90d', 90 * DAY],
    ]
    for (const [range, duration] of cases) {
      const params = toUsageQueryParams({ range }, now)
      expect(params).toBeDefined()
      expect(new Date(params!.to).getTime()).toBe(now.getTime())
      expect(new Date(params!.from).getTime()).toBe(now.getTime() - duration)
    }
  })

  it('returns undefined for unknown preset ranges', () => {
    expect(toUsageQueryParams({ range: '1y' }, now)).toBeUndefined()
  })

  it('converts custom local dates to a UTC half-open interval', () => {
    const params = toUsageQueryParams(
      { range: 'custom', fromDate: '2026-08-01', toDate: '2026-08-03' },
      now,
    )
    expect(params).toBeDefined()
    const from = new Date(params!.from)
    const to = new Date(params!.to)
    // 起始为本地当天零点，结束为本地结束日次日零点（半开区间）
    expect(from.getHours()).toBe(0)
    expect(from.getMinutes()).toBe(0)
    expect(from.getSeconds()).toBe(0)
    expect(to.getHours()).toBe(0)
    expect(to.getTime() - from.getTime()).toBe(3 * DAY)
  })

  it('rejects invalid custom ranges', () => {
    expect(toUsageQueryParams({ range: 'custom' }, now)).toBeUndefined()
    expect(toUsageQueryParams({ range: 'custom', fromDate: '2026-08-03' }, now)).toBeUndefined()
    expect(
      toUsageQueryParams({ range: 'custom', fromDate: '2026-08-03', toDate: '2026-08-01' }, now),
    ).toBeUndefined()
    expect(
      toUsageQueryParams(
        { range: 'custom', fromDate: 'not-a-date', toDate: '2026-08-01' },
        now,
      ),
    ).toBeUndefined()
  })

  it('passes dimension filters through and trims feature', () => {
    const params = toUsageQueryParams(
      {
        range: '7d',
        callerPlugin: 'plugin-a',
        feature: ' semantic-search ',
        providerName: 'provider',
        modelName: 'model',
        modelType: 'LANGUAGE',
        operation: 'language.generateText',
        status: 'FAILED',
        usageQuality: 'PARTIAL',
        resolution: 'HOUR',
      },
      now,
    )
    expect(params).toMatchObject({
      callerPlugin: 'plugin-a',
      feature: 'semantic-search',
      providerName: 'provider',
      modelName: 'model',
      modelType: 'LANGUAGE',
      operation: 'language.generateText',
      status: 'FAILED',
      usageQuality: 'PARTIAL',
      resolution: 'HOUR',
    })
  })

  it('drops invalid feature values instead of sending them to the API', () => {
    const params = toUsageQueryParams({ range: '7d', feature: 'INVALID FEATURE!' }, now)
    expect(params?.feature).toBeUndefined()
  })

  it('treats blank filters as absent', () => {
    const params = toUsageQueryParams(
      { range: '7d', callerPlugin: '', feature: '  ', status: undefined },
      now,
    )
    expect(params?.callerPlugin).toBeUndefined()
    expect(params?.feature).toBeUndefined()
    expect(params?.status).toBeUndefined()
  })

  describe('custom range boundaries', () => {
    const originalTZ = process.env.TZ

    afterEach(() => {
      if (originalTZ === undefined) {
        delete process.env.TZ
      } else {
        process.env.TZ = originalTZ
      }
    })

    it('advances to the next local midnight across DST transitions', () => {
      // 2026-03-08 美国夏令时开始，2026-03-07 ~ 2026-03-09 本地日历两天仅 47 小时；
      // 固定 +24h 算法会得到 48 小时并落在次日 01:00
      process.env.TZ = 'America/New_York'
      const params = toUsageQueryParams(
        { range: 'custom', fromDate: '2026-03-07', toDate: '2026-03-08' },
        now,
      )
      expect(params).toBeDefined()
      const from = new Date(params!.from)
      const to = new Date(params!.to)
      expect(to.getHours()).toBe(0)
      expect(to.getDate()).toBe(9)
      expect(to.getTime() - from.getTime()).toBe(47 * 60 * 60 * 1000)
    })

    it('rejects ranges beyond the 3660-day backend limit', () => {
      expect(
        toUsageQueryParams({ range: 'custom', fromDate: '2016-01-01', toDate: '2026-08-01' }, now),
      ).toBeUndefined()
      expect(
        toUsageQueryParams({ range: 'custom', fromDate: '2026-01-01', toDate: '2026-12-31' }, now),
      ).toBeDefined()
    })
  })
})

describe('useUsageFilters', () => {
  it('normalizes an invalid route resolution at the query boundary', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { render: () => h('div') } }],
    })
    await router.push('/?resolution=BOGUS')
    await router.isReady()
    let filters: ReturnType<typeof useUsageFilters>
    mount(
      defineComponent({
        setup() {
          filters = useUsageFilters(() => now)
          return () => h('div')
        },
      }),
      { global: { plugins: [router] } },
    )

    expect(filters!.state.value.resolution).toBe('DAY')
    expect(filters!.buildParams()?.resolution).toBe('DAY')
  })

  it('freezes preset boundaries for one query session and advances them on refresh', async () => {
    let now = new Date('2026-08-11T06:00:00Z')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { render: () => h('div') } }],
    })
    await router.push('/')
    await router.isReady()
    let filters: ReturnType<typeof useUsageFilters>
    mount(
      defineComponent({
        setup() {
          filters = useUsageFilters(() => now)
          return () => h('div')
        },
      }),
      { global: { plugins: [router] } },
    )

    const first = filters!.buildParams()
    now = new Date('2026-08-11T06:01:00Z')
    expect(filters!.buildParams()).toEqual(first)

    filters!.refreshAnchor()
    expect(filters!.buildParams()?.to).toBe('2026-08-11T06:01:00.000Z')
  })
})
