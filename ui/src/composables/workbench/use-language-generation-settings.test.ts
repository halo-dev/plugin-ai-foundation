import type { ModelParameterProfile } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import { nextTick, shallowRef } from 'vue'
import { useLanguageGenerationSettings } from './use-language-generation-settings'

describe('useLanguageGenerationSettings', () => {
  it('builds the chat request parameters from the current controls', () => {
    const settings = useLanguageGenerationSettings()
    settings.systemPrompt.value = ' Be concise '
    settings.temperature.value = 0.2
    settings.topK.value = 40
    settings.minP.value = 0.1
    settings.presencePenalty.value = 0.3
    settings.frequencyPenalty.value = 0.4
    settings.repetitionPenalty.value = 1.1
    settings.stopSequencesText.value = 'END\n STOP '
    settings.logprobs.value = true
    settings.topLogprobs.value = 5
    settings.parallelToolCalls.value = false
    settings.seed.value = 42
    settings.chatHeadersText.value = '{"X-Trace":"trace-1"}'
    settings.outputMode.value = 'CHOICE'
    settings.outputChoicesText.value = 'yes\nno\n'

    expect(settings.buildValidatedParameters()).toEqual({
      systemPrompt: ' Be concise ',
      temperature: 0.2,
      topP: 1,
      topK: 40,
      minP: 0.1,
      presencePenalty: 0.3,
      frequencyPenalty: 0.4,
      repetitionPenalty: 1.1,
      stopSequences: ['END', 'STOP'],
      logprobs: true,
      topLogprobs: 5,
      parallelToolCalls: false,
      maxOutputTokens: 4096,
      seed: 42,
      maxRetries: 2,
      reasoning: undefined,
      headers: { 'X-Trace': 'trace-1' },
      output: { type: 'CHOICE', choices: ['yes', 'no'] },
      agent: {
        enabled: false,
        profile: 'BALANCED',
        maxSteps: 20,
        stepPolicy: 'ALL_TOOLS',
        serverToolEnabled: true,
        browserToolEnabled: false,
        externalToolEnabled: false,
        approvalRequired: false,
        toolInputStreamEnabled: false,
        recoveryScenario: 'NONE',
      },
    })
  })

  it('reports validation errors without creating a request', () => {
    const settings = useLanguageGenerationSettings()
    settings.chatHeadersText.value = '[]'

    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.chatHeadersError.value).toBe('Headers 必须是 JSON 对象')
  })

  it('keeps tool approval dependent on the test tool switch', async () => {
    const settings = useLanguageGenerationSettings()
    settings.testToolApprovalEnabled.value = true
    await nextTick()
    expect(settings.testToolEnabled.value).toBe(true)

    settings.testToolEnabled.value = false
    await nextTick()
    expect(settings.testToolApprovalEnabled.value).toBe(false)
  })

  it('maps the tool input stream diagnostic switch to stream options', () => {
    const settings = useLanguageGenerationSettings()
    settings.toolInputStreamTestEnabled.value = true

    expect(settings.streamOptions()).toMatchObject({ toolInputStreamTestEnabled: true })
  })
  it('builds the complete typed agent options and disables direct-mode query flags', async () => {
    const settings = useLanguageGenerationSettings()
    settings.testToolEnabled.value = true
    settings.agentModeEnabled.value = true
    settings.agentProfile.value = 'EXPLICIT'
    settings.agentMaxSteps.value = 7
    settings.agentStepPolicy.value = 'SERVER_THEN_BROWSER'
    settings.agentServerToolEnabled.value = false
    await nextTick()
    settings.agentBrowserToolEnabled.value = true
    settings.agentExternalToolEnabled.value = true
    settings.agentApprovalRequired.value = true
    settings.agentToolInputStreamEnabled.value = true
    settings.agentRecoveryScenario.value = 'RENAMED_TOOL'
    await nextTick()

    expect(settings.agentServerToolEnabled.value).toBe(true)
    expect(settings.buildValidatedParameters()?.agent).toEqual({
      enabled: true,
      profile: 'EXPLICIT',
      maxSteps: 7,
      stepPolicy: 'SERVER_THEN_BROWSER',
      serverToolEnabled: true,
      browserToolEnabled: true,
      externalToolEnabled: true,
      approvalRequired: true,
      toolInputStreamEnabled: true,
      recoveryScenario: 'RENAMED_TOOL',
    })
    expect(settings.streamOptions()).toEqual({})
  })

  it('omits unsupported sampling parameters without changing the saved controls', () => {
    const profile = shallowRef<ModelParameterProfile>({
      constraints: { temperature: false, topP: false, topK: false },
    })
    const settings = useLanguageGenerationSettings({ profile })
    settings.topK.value = 32
    expect(settings.buildValidatedParameters()).toMatchObject({
      temperature: undefined,
      topP: undefined,
      topK: undefined,
    })
    expect(settings.temperature.value).toBe(0.7)
    expect(settings.topK.value).toBe(32)
    profile.value = { constraints: { temperature: true, topP: true, topK: true } }
    expect(settings.buildValidatedParameters()).toMatchObject({
      temperature: 0.7,
      topP: 1,
      topK: 32,
    })
  })

  it('validates output limits and tools after switching models while preserving choices', () => {
    const profile = shallowRef<ModelParameterProfile>({
      constraints: { outputTokens: 1024, toolCalling: false },
    })
    const settings = useLanguageGenerationSettings({ profile })
    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.parameterError.value).toContain('1024')
    settings.maxTokens.value = 512
    settings.agentTestToolsEnabled.value = true
    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.parameterError.value).toContain('工具调用')
    expect(settings.agentTestToolsEnabled.value).toBe(true)
    settings.agentTestToolsEnabled.value = false
    expect(settings.buildValidatedParameters()?.maxOutputTokens).toBe(512)
  })

  it('validates only sent output tokens when a manual limit remains on an unsupported mapping', () => {
    const profile = shallowRef<ModelParameterProfile>({
      mappings: { language: { maxOutputTokens: { mode: 'UNSUPPORTED' } } },
      constraints: { outputTokens: 1024 },
    })
    const settings = useLanguageGenerationSettings({ profile })

    expect(settings.buildValidatedParameters()).toMatchObject({ maxOutputTokens: undefined })
    expect(settings.parameterError.value).toBe('')
    expect(settings.maxTokens.value).toBe(4096)

    profile.value = {
      mappings: {
        language: { maxOutputTokens: { mode: 'TEMPLATE', template: 'chat.max-tokens' } },
      },
      constraints: { outputTokens: 1024 },
    }
    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.parameterError.value).toContain('1024')
    settings.maxTokens.value = 512
    expect(settings.buildValidatedParameters()?.maxOutputTokens).toBe(512)
  })

  it('blocks unsupported reasoning intents while allowing structured output fallback', () => {
    const profile = shallowRef<ModelParameterProfile>({
      mappings: {
        language: {
          reasoning: {
            mode: 'TEMPLATE',
            reasoningMapping: {
              high: { field: 'thinking.budget_tokens', valueType: 'INTEGER', value: '16000' },
            },
          },
        },
      },
      constraints: { structuredOutput: false },
    })
    const settings = useLanguageGenerationSettings({ profile })
    settings.reasoningMode.value = 'EFFORT'
    settings.reasoningEffort.value = 'LOW'
    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.parameterError.value).toContain('推理模式')
    settings.reasoningEffort.value = 'HIGH'
    expect(settings.buildValidatedParameters()?.reasoning).toEqual({
      mode: 'ENABLED',
      effort: 'HIGH',
    })
    settings.outputMode.value = 'JSON'
    expect(settings.buildValidatedParameters()?.output).toEqual({ type: 'JSON' })
    expect(settings.parameterError.value).toBe('')
  })

  it('does not send a request before the selected model profile is resolved', () => {
    const isFetching = shallowRef(true)
    const settings = useLanguageGenerationSettings({ isFetching })
    expect(settings.buildValidatedParameters()).toBeUndefined()
    expect(settings.parameterError.value).toContain('正在读取')
    isFetching.value = false
    expect(settings.buildValidatedParameters()).toBeDefined()
  })
})
