import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import SegmentedTabs, { type Tab } from './SegmentedTabs.vue'

const tabs: Tab[] = [
  { label: '模型配置', value: 'providers' },
  { label: '模型列表', value: 'models' },
  { label: '测试', value: 'test' },
]
const wrappers: VueWrapper[] = []

describe('SegmentedTabs', () => {
  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) {
      wrapper.unmount()
    }
    document.body.innerHTML = ''
  })

  it('renders accessible tabs and defaults to the first item', () => {
    const wrapper = mountTabs({ ariaLabel: 'AI Foundation 页面导航' })

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe(
      'AI Foundation 页面导航',
    )
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3)
    expect(wrapper.findAll('[role="tab"]')[0]?.attributes('data-state')).toBe('active')
  })

  it('updates the controlled value when a tab is activated', async () => {
    const wrapper = mountTabs({ modelValue: 'providers' })

    await wrapper.findAll('[role="tab"]')[1]?.trigger('mousedown', {
      button: 0,
      ctrlKey: false,
    })
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toContainEqual(['models'])
    expect(wrapper.findAll('[role="tab"]')[1]?.attributes('data-state')).toBe('active')
  })

  it('prevents activation when disabled', async () => {
    const wrapper = mountTabs({ disabled: true, modelValue: 'providers' })
    const target = wrapper.findAll('[role="tab"]')[1]

    expect(target?.attributes('disabled')).toBeDefined()
    await target?.trigger('mousedown', { button: 0, ctrlKey: false })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('moves focus with arrow keys and activates manually with Enter', async () => {
    const wrapper = mountTabs({ modelValue: 'providers' })
    await flushPromises()
    const items = wrapper.findAll('[role="tab"]')
    const firstTab = items[0]?.element as HTMLElement

    firstTab.focus()
    await items[0]?.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()

    expect(document.activeElement).toBe(items[1]?.element)
    expect(items[0]?.attributes('data-state')).toBe('active')

    await items[1]?.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toContainEqual(['models'])
  })
})

function mountTabs(props: Partial<InstanceType<typeof SegmentedTabs>['$props']> = {}) {
  const wrapper = mount(SegmentedTabs, {
    attachTo: document.body,
    props: {
      tabs,
      ...props,
      'onUpdate:modelValue': (value: string) => {
        void wrapper.setProps({ modelValue: value })
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
