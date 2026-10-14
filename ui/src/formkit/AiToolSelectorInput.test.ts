import type { FormKitFrameworkContext } from '@formkit/core'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import AiToolSelectorInput from './AiToolSelectorInput.vue'

vi.mock('@/composables/use-tools-fetch', () => ({ useToolsFetch: vi.fn() }))
const selectorStub = defineComponent({
  props: [
    'modelValue',
    'disabled',
    'pluginName',
    'category',
    'searchPlaceholder',
    'label',
    'id',
    'placeholder',
    'fullWidth',
  ],
  emits: ['update:modelValue'],
  template:
    '<button :id="id" :disabled="disabled" @click="$emit(\'update:modelValue\', [\'ai-foundation/search_site\'])">Choose</button>',
})

describe('AiToolSelectorInput', () => {
  it('forwards the FormKit id, label, display options, filters, and disabled state', () => {
    const input = vi.fn()
    const wrapper = mountInput({
      _value: ['old/tool'],
      id: 'allowed-tools',
      label: '助手工具',
      disabled: true,
      pluginName: 'ai-foundation',
      category: 'site',
      placeholder: '选择助手工具',
      searchPlaceholder: '搜索助手工具',
      fullWidth: true,
      node: { input },
    })
    expect(wrapper.findComponent(selectorStub).props()).toMatchObject({
      modelValue: ['old/tool'],
      id: 'allowed-tools',
      label: '助手工具',
      disabled: true,
      pluginName: 'ai-foundation',
      category: 'site',
      placeholder: '选择助手工具',
      searchPlaceholder: '搜索助手工具',
      fullWidth: true,
    })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(input).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('writes selected ids and an explicit clear through the FormKit node', async () => {
    const input = vi.fn()
    const wrapper = mountInput({ _value: [], id: 'tools', label: '共享工具', node: { input } })
    await wrapper.get('button').trigger('click')
    expect(input).toHaveBeenCalledWith(['ai-foundation/search_site'])
    wrapper.findComponent(selectorStub).vm.$emit('update:modelValue', [])
    expect(input).toHaveBeenLastCalledWith([])
    wrapper.unmount()
  })

  it('reflects stored and externally changed values without writing them back automatically', async () => {
    const input = vi.fn()
    const context = { _value: ['old/tool'], node: { input } }
    const wrapper = mountInput(context)
    expect(wrapper.findComponent(selectorStub).props('modelValue')).toEqual(['old/tool'])
    await wrapper.setProps({
      context: { ...context, _value: ['other/tool'] } as unknown as FormKitFrameworkContext<
        string[]
      >,
    })
    expect(wrapper.findComponent(selectorStub).props('modelValue')).toEqual(['other/tool'])
    await wrapper.setProps({
      context: { ...context, _value: undefined } as unknown as FormKitFrameworkContext<string[]>,
    })
    expect(wrapper.findComponent(selectorStub).props('modelValue')).toEqual([])
    expect(input).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
function mountInput(context: Record<string, unknown>) {
  return mount(AiToolSelectorInput, {
    props: { context: context as unknown as FormKitFrameworkContext<string[]> },
    global: { stubs: { AiToolSelector: selectorStub } },
  })
}
