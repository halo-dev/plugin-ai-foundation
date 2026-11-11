import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import UsageSelect from '../UsageSelect.vue'

vi.mock('@halo-dev/components', () => ({
  IconArrowDown: defineComponent({ template: '<svg />' }),
  IconSearch: defineComponent({ template: '<svg />' }),
  VDropdown: defineComponent({
    name: 'UsageDropdownStub',
    emits: ['hide'],
    template: '<div><slot /><slot name="popper" /></div>',
  }),
}))
const items = [
  { label: '全部', value: undefined },
  { label: '重复名称', value: 'model-1', description: 'provider-a-model-1' },
  { label: '重复名称', value: 'model-2', description: 'provider-b-model-2' },
]
function create() {
  return mount(UsageSelect, {
    props: { label: '模型', searchable: true, items },
    global: { directives: { 'close-popper': () => {} } },
  })
}
describe('UsageSelect', () => {
  it('supports explicitly selecting a deleted resource identifier', async () => {
    const wrapper = create()
    await wrapper.setProps({ allowCustom: true })
    await wrapper.get('input').setValue('  deleted-model  ')
    expect(wrapper.get('[data-option]').text()).toContain('历史标识：deleted-model')
    await wrapper.get('[data-option]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['deleted-model']])
    wrapper.unmount()
  })
  it('searches identifiers to distinguish duplicate display names and emits exact values', async () => {
    const wrapper = create()
    await wrapper.get('input').setValue('PROVIDER-B')
    expect(wrapper.findAll('[data-option]')).toHaveLength(1)
    await wrapper.get('[data-option]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['model-2']])
    wrapper.unmount()
  })
  it('shows no matches without changing selection and retains unavailable historical values', async () => {
    const wrapper = create()
    await wrapper.setProps({ modelValue: 'deleted-model' })
    expect(wrapper.get('.usage-select-trigger').attributes('aria-label')).toBe(
      '模型：deleted-model',
    )
    await wrapper.get('input').setValue('not-found')
    expect(wrapper.get('[role="status"]').text()).toBe('没有匹配的选项')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('clears a selection and resets the search when closing', async () => {
    const wrapper = create()
    await wrapper.setProps({ modelValue: 'model-2' })
    await wrapper.findAll('[data-option]')[0]!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[undefined]])
    await wrapper.get('input').setValue('provider-a')
    wrapper.findComponent({ name: 'UsageDropdownStub' }).vm.$emit('hide')
    await wrapper.vm.$nextTick()
    expect(wrapper.get('input').element.value).toBe('')
    expect(wrapper.findAll('[data-option]')).toHaveLength(3)
    wrapper.unmount()
  })
  it('moves keyboard focus from search through visible results only', async () => {
    const wrapper = mount(UsageSelect, {
      attachTo: document.body,
      props: { label: '模型', searchable: true, items },
      global: { directives: { 'close-popper': () => {} } },
    })
    await wrapper.get('input').setValue('重复名称')
    await wrapper.get('input').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(wrapper.findAll('[data-option]')[0]!.element)
    await wrapper.findAll('[data-option]')[0]!.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(wrapper.findAll('[data-option]')[1]!.element)
    wrapper.unmount()
  })
})
