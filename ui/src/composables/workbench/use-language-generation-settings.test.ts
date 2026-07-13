import { describe, expect, it } from '@rstest/core'
import { nextTick } from 'vue'
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
      maxOutputTokens: 1024,
      seed: 42,
      maxRetries: 2,
      reasoning: undefined,
      headers: { 'X-Trace': 'trace-1' },
      output: { type: 'CHOICE', choices: ['yes', 'no'] },
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
})
