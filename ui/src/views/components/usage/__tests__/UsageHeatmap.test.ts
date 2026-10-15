import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent } from 'vue'
import UsageHeatmap from '../UsageHeatmap.vue'

const UsageSegmentedStub = defineComponent({
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
  template: `
    <div>
      <button
        v-for="option in options"
        :key="option.value"
        :data-value="option.value"
        @click="$emit('update:modelValue', option.value)"
      >{{ option.label }}</button>
    </div>
  `,
})

function mountHeatmap(points: unknown[] = []) {
  return mount(UsageHeatmap, {
    props: { points, anchor: new Date(), timeZone: 'UTC' },
    global: {
      stubs: { UsageSegmented: UsageSegmentedStub },
      directives: { tooltip: () => {} },
    },
  })
}

describe('UsageHeatmap', () => {
  it.each(['missingUsageCalls', 'partialUsageCalls'])(
    'marks known token subtotals with %s without marking complete call counts', async (counter) => {
      const wrapper = mountHeatmap([
        { bucketStart: '2026-08-10T00:00:00Z', callCount: 1, accountedTotalTokens: 10 },
        { bucketStart: '2026-08-10T01:00:00Z', callCount: 1, [counter]: 1 },
      ])
      await wrapper.setProps({ anchor: new Date('2026-08-10T12:00:00Z') })
      const cell = wrapper.get('[aria-label^="2026-08-10："]')
      expect(cell.attributes('aria-label')).toContain('已知 10 Token（用量未完整报告）')
      expect(cell.classes()).toContain('is-partial')
      expect(cell.classes()).not.toContain('is-unknown')
      await wrapper.get('button[data-value="calls"]').trigger('click')
      expect(cell.attributes('aria-label')).toBe('2026-08-10：2 次调用')
      expect(cell.classes()).not.toContain('is-partial')
      wrapper.unmount()
    },
  )

  it('marks incomplete event delivery for both metrics', async () => {
    const wrapper = mountHeatmap([
      { bucketStart: '2026-08-10T00:00:00Z', callCount: 2, accountedTotalTokens: 10, complete: false },
    ])
    await wrapper.setProps({ anchor: new Date('2026-08-10T12:00:00Z') })
    const cell = wrapper.get('[aria-label^="2026-08-10："]')
    expect(cell.attributes('aria-label')).toContain('统计数据不完整')
    expect(cell.classes()).toContain('is-partial')
    await wrapper.get('button[data-value="calls"]').trigger('click')
    expect(cell.attributes('aria-label')).toContain('2 次调用（统计数据不完整）')
    expect(cell.classes()).toContain('is-partial')
    wrapper.unmount()
  })

  it('distinguishes unknown token buckets from known zero and absent buckets', async () => {
    const wrapper = mountHeatmap([
      { bucketStart: '2026-08-10T00:00:00Z', callCount: 2 },
      { bucketStart: '2026-08-09T00:00:00Z', callCount: 1, accountedTotalTokens: 0 },
    ])
    await wrapper.setProps({ anchor: new Date('2026-08-10T12:00:00Z') })
    const unknown = wrapper.get('[aria-label="2026-08-10：未知 Token"]')
    expect(unknown.classes()).toContain('is-unknown')
    expect(wrapper.get('[aria-label="2026-08-09：0 Token"]').classes()).toContain('l0')
    expect(wrapper.get('[aria-label="2026-08-08：0 Token"]').classes()).toContain('l0')
    await wrapper.get('button[data-value="calls"]').trigger('click')
    expect(wrapper.get('[aria-label="2026-08-10：2 次调用"]').classes()).toContain('l4')
  })

  it.each([
    ['Asia/Shanghai', '2026-08-09T16:00:00Z'],
    ['Asia/Kathmandu', '2026-08-09T18:15:00Z'],
    ['America/New_York', '2026-08-10T04:00:00Z'],
  ])('maps UTC bucket instants to the local grid day in %s', async (timeZone, bucketStart) => {
    const wrapper = mountHeatmap([{ bucketStart, accountedTotalTokens: 12 }])
    await wrapper.setProps({ anchor: new Date(bucketStart), timeZone })
    expect(wrapper.get('[aria-label="2026-08-10：12 Token"]').classes()).toContain('l4')
    expect(wrapper.findAll('.usage-heat-cell[aria-label]').at(-1)?.attributes('aria-label')).toBe('2026-08-10：12 Token')
    expect(wrapper.text()).not.toContain('UTC')
    wrapper.unmount()
  })

  it('updates the last grid day when its shared anchor crosses midnight', async () => {
    const wrapper = mountHeatmap()
    await wrapper.setProps({ anchor: new Date('2026-08-10T23:59:00Z') })
    expect(
      wrapper.findAll('.usage-heat-cell[aria-label]').at(-1)?.attributes('aria-label'),
    ).toContain('2026-08-10')
    await wrapper.setProps({ anchor: new Date('2026-08-11T00:01:00Z') })
    expect(
      wrapper.findAll('.usage-heat-cell[aria-label]').at(-1)?.attributes('aria-label'),
    ).toContain('2026-08-11')
  })

  it('renders one cell per grid day with level classes', async () => {
    const today = new Date().toISOString().slice(0, 10)
    const wrapper = mountHeatmap([
      {
        bucketStart: `${today}T00:00:00Z`,
        resolution: 'DAY',
        callCount: 3,
        accountedTotalTokens: 900,
      },
    ])
    await flushPromises()
    const cells = wrapper.findAll('.usage-heat .usage-heat-cell')
    expect(cells.length).toBeGreaterThan(360)
    const todayCell = cells.find((cell) => cell.attributes('aria-label')?.startsWith(today))
    expect(todayCell?.attributes('class')).toContain('l4')
    expect(todayCell?.attributes('aria-label')).toContain('900')
  })

  it('switches the metric without rebuilding the grid', async () => {
    const today = new Date().toISOString().slice(0, 10)
    const wrapper = mountHeatmap([
      {
        bucketStart: `${today}T00:00:00Z`,
        resolution: 'DAY',
        callCount: 7,
        accountedTotalTokens: 10,
      },
    ])
    await flushPromises()
    const before = wrapper.findAll('.usage-heat .usage-heat-cell').length
    await wrapper.find('button[data-value="calls"]').trigger('click')
    await flushPromises()
    const cells = wrapper.findAll('.usage-heat .usage-heat-cell')
    expect(cells.length).toBe(before)
    const todayCell = cells.find((cell) => cell.attributes('aria-label')?.startsWith(today))
    expect(todayCell?.attributes('aria-label')).toContain('7 次调用')
  })

  it('shows month and weekday labels and a legend', async () => {
    const wrapper = mountHeatmap()
    await flushPromises()
    expect(wrapper.text()).toContain('周一')
    expect(wrapper.text()).toContain('周日')
    expect(wrapper.findAll('.usage-heat-month').some((month) => month.text().endsWith('月'))).toBe(
      true,
    )
    expect(wrapper.text()).toContain('少')
    expect(wrapper.text()).toContain('多')
  })
})
