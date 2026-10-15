import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
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
    it.each([
      ['Asia/Shanghai', '2026-08-01', '2026-07-31T16:00:00.000Z', '2026-08-01T16:00:00.000Z'],
      ['Asia/Kathmandu', '2026-08-01', '2026-07-31T18:15:00.000Z', '2026-08-01T18:15:00.000Z'],
      ['America/New_York', '2026-03-08', '2026-03-08T05:00:00.000Z', '2026-03-09T04:00:00.000Z'],
      ['America/New_York', '2026-11-01', '2026-11-01T04:00:00.000Z', '2026-11-02T05:00:00.000Z'],
    ])('uses calendar midnights in %s for %s', (timeZone, date, from, to) => {
      expect(toUsageQueryParams(
        { range: 'custom', fromDate: date, toDate: date }, now, timeZone,
      )).toMatchObject({ from, to, timeZone })
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
  it('batches related filter changes into one navigation without losing query fields', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { render: () => h('div') } }],
    })
    await router.push('/?providerName=old-provider&modelName=old-model&keep=value')
    await router.isReady()
    let filters: ReturnType<typeof useUsageFilters>
    const wrapper = mount(defineComponent({
      setup() {
        filters = useUsageFilters(() => now)
        return () => h('div')
      },
    }), { global: { plugins: [router] } })
    const replace = vi.spyOn(router, 'replace')
    try {
      filters!.applyChange({ range: 'custom', fromDate: '2026-08-01', toDate: '2026-08-03' })
      await flushPromises()
      expect(replace).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.query).toMatchObject({
        range: 'custom', from: '2026-08-01', to: '2026-08-03', keep: 'value',
      })
      expect(filters!.valid.value).toBe(true)

      replace.mockClear()
      filters!.applyChange({ fromDate: '2026-08-02', toDate: '2026-08-04' })
      await flushPromises()
      expect(replace).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.query).toMatchObject({ from: '2026-08-02', to: '2026-08-04' })

      replace.mockClear()
      filters!.applyChange({ range: '7d' })
      await flushPromises()
      expect(replace).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.query.range).toBe('7d')
      expect(router.currentRoute.value.query.from).toBeUndefined()
      expect(router.currentRoute.value.query.to).toBeUndefined()
      expect(filters!.valid.value).toBe(true)

      replace.mockClear()
      filters!.applyChange({ providerName: 'new-provider' })
      await flushPromises()
      expect(replace).toHaveBeenCalledTimes(1)
      expect(router.currentRoute.value.query.providerName).toBe('new-provider')
      expect(router.currentRoute.value.query.modelName).toBeUndefined()
      expect(router.currentRoute.value.query.keep).toBe('value')
    } finally {
      replace.mockRestore()
      wrapper.unmount()
    }
  })

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
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
    expect(filters!.buildParams()?.timeZone).toBe(timeZone)
    expect(JSON.parse(filters!.fingerprint.value).timeZone).toBe(timeZone)
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
