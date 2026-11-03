import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatParameterPanel from './ChatParameterPanel.vue'

describe('workbench model parameter controls', () => {
  it('allows selecting effort mode when the model supports only a different intensity', () => {
    const wrapper = mount(ChatParameterPanel, {
      props: {
        reasoningMode: 'EFFORT',
        reasoningEffort: 'MEDIUM',
        parameterProfile: {
          mappings: {
            language: {
              reasoning: {
                mode: 'TEMPLATE',
                reasoningMapping: {
                  high: { field: 'reasoning_effort', valueType: 'STRING', value: 'high' },
                },
              },
            },
          },
          constraints: { temperature: false, outputTokens: 1024, toolCalling: false },
        },
      },
    })
    expect(wrapper.get('option[value="EFFORT"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('option[value="LOW"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('option[value="HIGH"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('input[type="range"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('input[max="1024"]').exists()).toBe(true)
  })
})