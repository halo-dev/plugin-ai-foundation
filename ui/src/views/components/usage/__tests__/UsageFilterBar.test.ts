import type { UsageFilterState } from '@/composables/use-usage-filters'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import UsageFilterBar from '../UsageFilterBar.vue'

vi.mock('@halo-dev/components', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@halo-dev/components')>()),
  VDropdown: defineComponent({ template: '<div><slot /><slot name="popper" /></div>' }),
  VDropdownItem: defineComponent({
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>',
  }),
}))

const FilterDropdownStub = defineComponent({
  props: ['modelValue', 'label', 'items'],
  emits: ['update:modelValue'],
  template: `
    <div class="filter-dropdown" :data-label="label">
      <button
        v-for="item in items"
        :key="item.value ?? '__all__'"
        :data-value="item.value ?? ''"
        @click="$emit('update:modelValue', item.value)"
      >{{ item.label }}</button>
    </div>
  `,
})

const FilterCleanButtonStub = defineComponent({
  emits: ['click'],
  template: '<button data-test="clear" @click="$emit(\'click\')">清除筛选</button>',
})

function mountBar(props: Partial<InstanceType<typeof UsageFilterBar>['$props']> = {}) {
  const state: UsageFilterState = { range: '30d', ...props.state }
  return mount(UsageFilterBar, {
    props: { state, ...props },
    global: {
      stubs: {
        Dropdown: defineComponent({ template: '<div><slot /><slot name="popper" /></div>' }),
        VDropdownItem: defineComponent({
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        }),
        UsageSelect: FilterDropdownStub,
        FilterCleanButton: FilterCleanButtonStub,
      },
      directives: { tooltip: () => {}, 'close-popper': () => {} },
    },
  })
}

describe('UsageFilterBar', () => {
  it('renders all filter dimensions', () => {
    const wrapper = mountBar()
    const labels = wrapper.findAll('.filter-dropdown').map((item) => item.attributes('data-label'))
    expect(labels).toEqual([
      '时间范围',
      '调用插件',
      '供应商',
      '模型',
      '状态',
      '模型类型',
      '操作',
      '用量质量',
    ])
    expect(wrapper.find('input[aria-label="功能标识"]').exists()).toBe(true)
  })

  it('lets active advanced filters collapse and reopen', async () => {
    const wrapper = mountBar({ state: { range: '30d', feature: 'semantic-search' } })
    const toggle = wrapper.findAll('button').find((button) => button.text() === '更多筛选')!
    expect(toggle.attributes('aria-expanded')).toBe('true')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
  })

  it('emits change patches when a filter value is selected', async () => {
    const wrapper = mountBar()
    const statusDropdown = wrapper.find('.filter-dropdown[data-label="状态"]')
    const failed = statusDropdown
      .findAll('button')
      .find((button) => button.attributes('data-value') === 'FAILED')
    await failed!.trigger('click')
    expect(wrapper.emitted('change')).toContainEqual([{ status: 'FAILED' }])

    const qualityDropdown = wrapper.find('.filter-dropdown[data-label="用量质量"]')
    const partial = qualityDropdown
      .findAll('button')
      .find((button) => button.attributes('data-value') === 'PARTIAL')
    await partial!.trigger('click')
    expect(wrapper.emitted('change')).toContainEqual([{ usageQuality: 'PARTIAL' }])
  })

  it('clears a filter through the 全部 option', async () => {
    const wrapper = mountBar({ state: { range: '30d', status: 'FAILED' } })
    const statusDropdown = wrapper.find('.filter-dropdown[data-label="状态"]')
    await statusDropdown.find('button[data-value=""]').trigger('click')
    expect(wrapper.emitted('change')).toContainEqual([{ status: undefined }])
  })

  it('shows custom date inputs only for the custom range and emits date changes', async () => {
    const preset = mountBar({ state: { range: '30d' } })
    expect(preset.find('input[type="date"]').exists()).toBe(false)

    const custom = mountBar({ state: { range: 'custom', fromDate: '2026-08-01' } })
    const inputs = custom.findAll('input[type="date"]')
    expect(inputs).toHaveLength(2)
    await inputs[0]!.setValue('2026-08-02')
    await flushPromises()
    expect(custom.emitted('change')).toContainEqual([{ fromDate: '2026-08-02' }])
  })

  it('emits feature text changes and shows the invalid hint', async () => {
    const wrapper = mountBar({ featureInvalid: false })
    await wrapper.find('input[aria-label="功能标识"]').setValue('semantic-search')
    expect(wrapper.emitted('change')).toContainEqual([{ feature: 'semantic-search' }])
    expect(wrapper.text()).not.toContain('当前值不会作为过滤条件')

    const invalid = mountBar({
      state: { range: '30d', feature: 'BAD VALUE' },
      featureInvalid: true,
    })
    expect(invalid.text()).toContain('当前值不会作为过滤条件')
  })

  it('emits refresh and reset actions', async () => {
    const wrapper = mountBar()
    const refresh = wrapper.find('button[aria-label="刷新"]')
    await refresh.trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    const reset = wrapper.findAll('button').find((button) => button.text() === '重置统计')
    await reset!.trigger('click')
    expect(wrapper.emitted('reset')).toHaveLength(1)
  })

  it('shows the clear button only with dimension filters and emits clear', async () => {
    const withoutFilters = mountBar({ hasDimensionFilters: false })
    expect(withoutFilters.find('button[aria-label="清除筛选"]').exists()).toBe(false)

    const withFilters = mountBar({
      state: { range: '30d', status: 'FAILED' },
      hasDimensionFilters: true,
    })
    await withFilters.find('button[aria-label="清除筛选"]').trigger('click')
    expect(withFilters.emitted('clear')).toHaveLength(1)
  })
})
