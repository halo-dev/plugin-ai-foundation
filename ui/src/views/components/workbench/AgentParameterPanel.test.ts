import { describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import AgentParameterPanel from './AgentParameterPanel.vue'

rstest.mock('@halo-dev/components', () => ({
  VSwitch: defineComponent({
    props: { modelValue: Boolean, disabled: Boolean },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
      return () =>
        h('button', {
          type: 'button',
          disabled: props.disabled,
          'aria-checked': props.modelValue,
          onClick: () => emit('update:modelValue', !props.modelValue),
        })
    },
  }),
}))

describe('AgentParameterPanel', () => {
  it('emits mode, typed profile, step policy, recovery, and tool controls', async () => {
    const wrapper = mount(AgentParameterPanel, {
      props: {
        enabled: true,
        profile: 'BALANCED',
        maxSteps: 20,
        stepPolicy: 'ALL_TOOLS',
        serverToolEnabled: true,
        recoveryScenario: 'NONE',
      },
    })

    await wrapper.findAll('button')[0]?.trigger('click')
    const selects = wrapper.findAll('select')
    await selects[0]?.setValue('CONCISE')
    await wrapper.get('input[type="number"]').setValue('8')
    await selects[1]?.setValue('SERVER_THEN_BROWSER')
    await selects[2]?.setValue('RENAMED_TOOL')
    await wrapper.findAll('button')[2]?.trigger('click')

    expect(wrapper.emitted('update:enabled')).toEqual([[false]])
    expect(wrapper.emitted('update:profile')).toEqual([['CONCISE']])
    expect(wrapper.emitted('update:maxSteps')).toEqual([[8]])
    expect(wrapper.emitted('update:stepPolicy')).toEqual([['SERVER_THEN_BROWSER']])
    expect(wrapper.emitted('update:recoveryScenario')).toEqual([['RENAMED_TOOL']])
    expect(wrapper.emitted('update:browserToolEnabled')).toEqual([[true]])
  })
})
