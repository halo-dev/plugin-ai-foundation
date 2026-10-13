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
    props: { points },
    global: {
      stubs: { UsageSegmented: UsageSegmentedStub },
      directives: { tooltip: () => {} },
    },
  })
}

describe('UsageHeatmap', () => {
  it('renders one cell per grid day with level classes', async () => {
    const today = new Date().toISOString().slice(0, 10)
    const wrapper = mountHeatmap([
      { bucketStart: `${today}T00:00:00Z`, resolution: 'DAY', callCount: 3, accountedTotalTokens: 900 },
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
      { bucketStart: `${today}T00:00:00Z`, resolution: 'DAY', callCount: 7, accountedTotalTokens: 10 },
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
