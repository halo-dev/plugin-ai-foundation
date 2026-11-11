import type { ModelParameterProfile } from '@/api/generated'
import { supportsLanguageParameter, supportsReasoningIntent } from '@/utils/model-parameter-support'
import {
  buildOutputSpec,
  buildReasoningOptions,
  type OutputMode,
  type ReasoningEffort,
  type ReasoningMode,
} from '@/utils/model-test-workbench'
import { numberOrUndefined, parseStringMapJson } from '@/utils/model-test-workbench-request'
import { shallowRef, watch, type Ref } from 'vue'

export function useLanguageGenerationSettings(
  options: {
    profile?: Readonly<Ref<ModelParameterProfile | undefined>>
    isFetching?: Readonly<Ref<boolean>>
    isError?: Readonly<Ref<boolean>>
  } = {},
) {
  const systemPrompt = shallowRef('')
  const temperature = shallowRef(0.7)
  const topP = shallowRef(1)
  const topK = shallowRef<number | undefined>()
  const minP = shallowRef<number | undefined>()
  const presencePenalty = shallowRef<number | undefined>()
  const frequencyPenalty = shallowRef<number | undefined>()
  const repetitionPenalty = shallowRef<number | undefined>()
  const stopSequencesText = shallowRef('')
  const logprobs = shallowRef<boolean | undefined>()
  const topLogprobs = shallowRef<number | undefined>()
  const parallelToolCalls = shallowRef<boolean | undefined>()
  const maxTokens = shallowRef(4096)
  const seed = shallowRef<number | undefined>()
  const maxRetries = shallowRef<number | undefined>(2)
  const reasoningMode = shallowRef<ReasoningMode>('DEFAULT')
  const reasoningEffort = shallowRef<ReasoningEffort>('MEDIUM')
  const testToolEnabled = shallowRef(false)
  const toolIds = shallowRef<string[]>([])
  const testToolApprovalEnabled = shallowRef(false)
  const externalTestToolEnabled = shallowRef(false)
  const agentTestToolsEnabled = shallowRef(false)
  const toolCallRepairEnabled = shallowRef(false)
  const toolInputStreamTestEnabled = shallowRef(false)
  const outputMode = shallowRef<OutputMode>('TEXT')
  const outputSchemaText = shallowRef(`{
  "type": "object",
  "properties": {
    "title": {
      "type": "string"
    },
    "summary": {
      "type": "string"
    }
  },
  "required": ["title", "summary"]
}`)
  const outputChoicesText = shallowRef('yes\nno')
  const outputError = shallowRef('')
  const chatHeadersText = shallowRef('{}')
  const chatHeadersError = shallowRef('')
  const parameterError = shallowRef('')

  function buildParameters(
    headers: Record<string, string> | undefined,
    output: ReturnType<typeof buildOutputSpec>['value'],
  ) {
    const stopSequences = stopSequencesText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const parameters = {
      systemPrompt: systemPrompt.value,
      toolIds: [...toolIds.value],
      temperature: numberOrUndefined(temperature.value),
      topP: numberOrUndefined(topP.value),
      topK: numberOrUndefined(topK.value),
      minP: numberOrUndefined(minP.value),
      presencePenalty: numberOrUndefined(presencePenalty.value),
      frequencyPenalty: numberOrUndefined(frequencyPenalty.value),
      repetitionPenalty: numberOrUndefined(repetitionPenalty.value),
      stopSequences: stopSequences.length ? stopSequences : undefined,
      logprobs: logprobs.value,
      topLogprobs: numberOrUndefined(topLogprobs.value),
      parallelToolCalls: parallelToolCalls.value,
      maxOutputTokens: numberOrUndefined(maxTokens.value),
      seed: numberOrUndefined(seed.value),
      maxRetries: numberOrUndefined(maxRetries.value),
      reasoning: buildReasoningOptions({
        mode: reasoningMode.value,
        effort: reasoningEffort.value,
      }),
      headers,
      output,
    }
    for (const key of [
      'temperature',
      'topP',
      'topK',
      'minP',
      'presencePenalty',
      'frequencyPenalty',
      'repetitionPenalty',
      'stopSequences',
      'logprobs',
      'topLogprobs',
      'parallelToolCalls',
      'maxOutputTokens',
      'seed',
    ] as const) {
      if (!supportsLanguageParameter(options.profile?.value, key)) parameters[key] = undefined
    }
    return parameters
  }

  function buildValidatedParameters(
    validation: { tools?: boolean; output?: boolean } = {},
  ): ReturnType<typeof buildParameters> | undefined {
    parameterError.value = ''
    if (options.isFetching?.value) parameterError.value = '正在读取当前模型的参数配置，请稍后重试'
    else if (options.isError?.value)
      parameterError.value = '无法读取当前模型的参数配置，请重新选择模型后重试'
    const profile = options.profile?.value
    if (options.profile && !profile && !parameterError.value) {
      parameterError.value = '正在读取当前模型的参数配置，请稍后重试'
    }
    const parameters = buildParameters(undefined, undefined)
    const outputLimit = profile?.constraints?.outputTokens
    if (
      outputLimit !== undefined &&
      parameters.maxOutputTokens !== undefined &&
      parameters.maxOutputTokens > outputLimit
    ) {
      parameterError.value = `最大输出不能超过 ${outputLimit} Token`
    } else if (
      validation.tools !== false &&
      profile?.constraints?.toolCalling === false &&
      Object.values(streamOptions()).some(Boolean)
    ) {
      parameterError.value = '当前模型不支持工具调用，请关闭工具测试'
    } else if (!supportsReasoningIntent(profile, reasoningMode.value, reasoningEffort.value)) {
      parameterError.value = '当前模型不支持所选推理模式，请调整推理控制'
    }
    if (parameterError.value) return undefined
    const headers = parseStringMapJson(chatHeadersText.value)
    chatHeadersError.value = headers.error || ''
    if (headers.error) return undefined

    const outputSpec =
      validation.output === false
        ? {}
        : buildOutputSpec({
            mode: outputMode.value,
            schemaText: outputSchemaText.value,
            choicesText: outputChoicesText.value,
          })
    outputError.value = outputSpec.error || ''
    if (outputSpec.error) return undefined

    return { ...parameters, headers: headers.value, output: outputSpec.value }
  }

  function streamOptions() {
    return {
      testToolEnabled: testToolEnabled.value,
      testToolApprovalEnabled: testToolApprovalEnabled.value,
      externalTestToolEnabled: externalTestToolEnabled.value,
      agentTestToolsEnabled: agentTestToolsEnabled.value,
      toolCallRepairEnabled: toolCallRepairEnabled.value,
      toolInputStreamTestEnabled: toolInputStreamTestEnabled.value,
    }
  }

  watch(testToolApprovalEnabled, (enabled) => {
    if (enabled && !testToolEnabled.value) testToolEnabled.value = true
  })
  watch(testToolEnabled, (enabled) => {
    if (!enabled) testToolApprovalEnabled.value = false
  })

  return {
    systemPrompt,
    temperature,
    topP,
    topK,
    minP,
    presencePenalty,
    frequencyPenalty,
    repetitionPenalty,
    stopSequencesText,
    logprobs,
    topLogprobs,
    parallelToolCalls,
    maxTokens,
    seed,
    maxRetries,
    reasoningMode,
    reasoningEffort,
    testToolEnabled,
    toolIds,
    testToolApprovalEnabled,
    externalTestToolEnabled,
    agentTestToolsEnabled,
    toolCallRepairEnabled,
    toolInputStreamTestEnabled,
    outputMode,
    outputSchemaText,
    outputChoicesText,
    outputError,
    chatHeadersText,
    chatHeadersError,
    parameterError,
    buildParameters,
    buildValidatedParameters,
    streamOptions,
  }
}