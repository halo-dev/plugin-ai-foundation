import { afterEach, describe, expect, it } from '@rstest/core'
import { mount, type VueWrapper } from '@vue/test-utils'
import AgentRunDiagnostics from './AgentRunDiagnostics.vue'

const wrappers: VueWrapper[] = []

describe('AgentRunDiagnostics', () => {
  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) wrapper.unmount()
  })

  it('renders terminal policy, recovery identity, warnings, usage, and final output', () => {
    const wrapper = mount(AgentRunDiagnostics, {
      props: {
        diagnostics: {
          enabled: true,
          profile: 'CONCISE',
          effectiveInstructions: 'Be concise.',
          maximumSteps: 20,
          completedSteps: 2,
          stepPolicy: 'SERVER_THEN_ALL',
          outputMode: 'OBJECT',
          recoveryScenario: 'RENAMED_TOOL',
          callPreparationCount: 1,
          terminalState: 'done',
          activeTools: ['halo_repair_test_info'],
          steps: [
            {
              stepIndex: 0,
              finishReason: 'TOOL_CALLS',
              usage: { inputTokens: 10, outputTokens: 4 },
            },
          ],
          tools: [
            {
              toolCallId: 'call_1',
              originalToolName: 'halo_legacy_repair_test_info',
              resolvedToolName: 'halo_repair_test_info',
              state: 'output-available',
              output: { ok: true },
            },
          ],
          warnings: [{ code: 'tool-call-repaired', message: 'Recovered' }],
          finalOutput: { title: 'Halo' },
        },
      },
    })
    wrappers.push(wrapper)

    expect(wrapper.text()).toContain('2 / 20')
    expect(wrapper.text()).toContain('CONCISE')
    expect(wrapper.text()).toContain('halo_legacy_repair_test_info')
    expect(wrapper.text()).toContain('halo_repair_test_info')
    expect(wrapper.text()).toContain('call_1')
    expect(wrapper.text()).toContain('tool-call-repaired')
    expect(wrapper.text()).toContain('inputTokens')
    expect(wrapper.text()).toContain('已校验的最终结构化值')
    expect(wrapper.text()).toContain('done')
  })
})
