import type { ModelParameterConstraints } from '@/api/generated'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import ModelParameterConstraintFields from './ModelParameterConstraintFields.vue'

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 25))
  await nextTick()
}

function mountFields(initial?: ModelParameterConstraints) {
  const wrapper = mount(ModelParameterConstraintFields, {
    props: {
      modelValue: initial,
      inherited: {
        toolCalling: false,
        outputTokens: 8192,
        reasoning: { supported: true, efforts: ['low', 'high'] },
      },
      'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value }),
    },
    global: { plugins: [[FormKitPlugin, defaultConfig()]] },
  })
  return wrapper
}

describe('model parameter constraints', () => {
  it('shows inherited values without storing them as manual overrides', async () => {
    const wrapper = mountFields()
    await settle()
    expect(wrapper.findAll('input')).toHaveLength(5)
    expect(wrapper.findAll('select')).toHaveLength(0)
    for (const field of [
      'temperature',
      'topP',
      'topK',
      'toolCalling',
      'structuredOutput',
      'reasoning_supported',
      'reasoning_toggle',
      'reasoning_efforts',
    ]) {
      expect(wrapper.find(`[name="constraint_${field}"]`).exists()).toBe(false)
    }
    expect(wrapper.get('input[name="constraint_outputTokens"]').attributes('placeholder')).toBe(
      'models.dev：8192',
    )
    expect(wrapper.props('modelValue')).toBeUndefined()

    await wrapper.get('input[name="constraint_outputTokens"]').setValue('4096')
    await settle()
    expect(wrapper.props('modelValue')).toEqual({ outputTokens: 4096 })
    await wrapper.get('input[name="constraint_outputTokens"]').setValue('')
    await settle()
    expect(wrapper.props('modelValue')).toBeUndefined()
  })

  it('preserves explicit false and independent reasoning overrides', async () => {
    const wrapper = mountFields({
      temperature: false,
      reasoning: { supported: true, budgetMaximum: 16000 },
    })
    await wrapper.get('input[name="constraint_reasoning_budgetMinimum"]').setValue('1024')
    await settle()
    expect(wrapper.props('modelValue')).toEqual({
      temperature: false,
      reasoning: { supported: true, budgetMinimum: 1024, budgetMaximum: 16000 },
    })
  })
})