import { afterEach, describe, expect, it } from '@rstest/core'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AdvancedSettingsCollapsible from './AdvancedSettingsCollapsible.vue'

const wrappers: VueWrapper[] = []

describe('AdvancedSettingsCollapsible', () => {
  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) {
      wrapper.unmount()
    }
    document.body.innerHTML = ''
  })

  it('is collapsed by default while keeping form content mounted', async () => {
    const wrapper = mountCollapsible()
    await flushPromises()
    const trigger = wrapper.get('button')
    const contentId = trigger.attributes('aria-controls')

    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(contentId).toBeTruthy()
    const content = wrapper.get('[data-state="closed"][hidden]')
    expect(content.attributes('id')).toBe(contentId)
    expect(content.attributes('style')).toContain('display: none')
    expect(wrapper.find('[data-testid="advanced-field"]').exists()).toBe(true)
    expect(wrapper.find('[data-state="closed"][hidden]').exists()).toBe(true)
  })

  it('expands and collapses when clicked', async () => {
    const wrapper = mountCollapsible()
    const trigger = wrapper.get('button')

    await trigger.trigger('click')
    await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    const content = wrapper.get('[data-state="open"]')
    expect(content.attributes('hidden')).toBeUndefined()
    expect(content.attributes('style') || '').not.toContain('display: none')

    await trigger.trigger('click')
    await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('[data-state="closed"][hidden]').attributes('style')).toContain(
      'display: none',
    )
  })
})

function mountCollapsible() {
  const wrapper = mount(AdvancedSettingsCollapsible, {
    attachTo: document.body,
    props: {
      sourceLabel: '手动配置',
    },
    slots: {
      default: '<input data-testid="advanced-field" />',
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
