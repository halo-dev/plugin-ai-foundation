import type { ModelParameterConstraints } from '@/api/generated'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import ModelNativeParameterConstraintFields from './ModelNativeParameterConstraintFields.vue'

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 25))
  await nextTick()
}

function mountFields(initial?: ModelParameterConstraints) {
  const wrapper = mount(ModelNativeParameterConstraintFields, {
    props: {
      modelValue: initial,
      inherited: { toolStrict: false, serviceTiers: ['priority'] },
      'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value }),
    },
    global: { plugins: [[FormKitPlugin, defaultConfig()]] },
  })
  return wrapper
}

describe('native parameter compatibility', () => {
  it('keeps inherited protocol facts separate from model features and reasoning mappings', async () => {
    const wrapper = mountFields()
    await settle()
    expect(wrapper.get('select[name="constraint_toolStrict"]').text()).toContain(
      '自动（models.dev：不支持）',
    )
    expect(wrapper.get('input[name="constraint_serviceTiers"]').attributes('placeholder')).toBe(
      '自动（models.dev：priority）',
    )
    expect(wrapper.props('modelValue')).toBeUndefined()
    expect(wrapper.findAll('select')).toHaveLength(4)
    expect(wrapper.findAll('input')).toHaveLength(2)
    for (const field of [
      'toolCalling',
      'structuredOutput',
      'reasoning_toggle',
      'reasoning_efforts',
    ]) {
      expect(wrapper.find(`[name="constraint_${field}"]`).exists()).toBe(false)
    }
  })

  it('distinguishes missing catalog declarations from loading and failures without creating overrides', async () => {
    const wrapper = mountFields({ store: true })
    await settle()
    const automaticOption = () =>
      wrapper.get('select[name="constraint_store"] option[value=""]').text()
    expect(automaticOption()).toBe('自动（未声明）')
    await wrapper.setProps({ loading: true })
    expect(automaticOption()).toBe('自动（加载中）')
    await wrapper.setProps({ loading: false, failed: true })
    expect(automaticOption()).toBe('自动（读取失败）')
    await wrapper.setProps({ failed: false, inherited: { store: false } })
    expect(automaticOption()).toBe('自动（models.dev：不支持）')
    await settle()
    expect(wrapper.props('modelValue')).toEqual({ store: true })
    expect(
      (wrapper.get('select[name="constraint_store"]').element as HTMLSelectElement).value,
    ).toBe('true')
  })

  it('edits and resets protocol overrides without changing capabilities or token limits', async () => {
    const initial = { toolCalling: false, outputTokens: 4096, reasoning: { budgetMaximum: 8192 } }
    const wrapper = mountFields(initial)
    await wrapper.get('select[name="constraint_toolStrict"]').setValue('false')
    await settle()
    await wrapper.get('input[name="constraint_serviceTiers"]').setValue('priority, flex')
    await settle()
    expect(wrapper.props('modelValue')).toEqual({
      ...initial,
      toolStrict: false,
      serviceTiers: ['priority', 'flex'],
    })
    await wrapper.get('select[name="constraint_toolStrict"]').setValue('')
    await settle()
    await wrapper.get('input[name="constraint_serviceTiers"]').setValue('')
    await settle()
    expect(wrapper.props('modelValue')).toEqual(initial)
  })
})