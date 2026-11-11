import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import UsageSegmented from '../UsageSegmented.vue'

function mountSegmented(modelValue = 'a') {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  return mount(UsageSegmented, {
    props: {
      modelValue,
      options: [
        { label: '选项 A', value: 'a' },
        { label: '选项 B', value: 'b' },
        { label: '选项 C', value: 'c' },
      ],
    },
  })
}

describe('UsageSegmented', () => {
  it('marks the active option and emits updates on click', async () => {
    const wrapper = mountSegmented('b')
    await flushPromises()
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(3)
    expect(buttons[1]!.attributes('aria-pressed')).toBe('true')
    expect(buttons[0]!.attributes('aria-pressed')).toBe('false')
    await buttons[2]!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['c']])
    vi.unstubAllGlobals()
  })

  it('positions the thumb under the active option', async () => {
    const wrapper = mountSegmented('a')
    await flushPromises()
    const thumb = wrapper.find('.usage-segmented-thumb')
    expect(thumb.exists()).toBe(true)
    expect(thumb.isVisible()).toBe(true)
    vi.unstubAllGlobals()
  })
})
