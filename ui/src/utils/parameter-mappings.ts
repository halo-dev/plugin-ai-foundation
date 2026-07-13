import type {
  DefaultParameterMappingInfo,
  ModelParameterMappings,
  ParameterMappingTemplateInfo,
  Selection,
} from '@/api/generated'

export type MappingContext = 'provider' | 'model'
export type MappingModelType = 'language' | 'embedding' | 'rerank' | 'image-generation'

export interface ParameterDefinition {
  parameter: string
  domain: keyof ModelParameterMappings
  field: string
  label: string
  description: string
  modelType: MappingModelType
}

export const PARAMETER_DEFINITIONS: ParameterDefinition[] = [
  parameter('MAX_OUTPUT_TOKENS', 'language', 'maxOutputTokens', '最大输出 Token', '限制单次生成的最大输出长度', 'language'),
  parameter('TEMPERATURE', 'language', 'temperature', '随机性（Temperature）', '控制生成结果的随机程度', 'language'),
  parameter('TOP_P', 'language', 'topP', 'Top P', '按累计概率限制候选 Token', 'language'),
  parameter('TOP_K', 'language', 'topK', 'Top K', '限制候选 Token 数量', 'language'),
  parameter('MIN_P', 'language', 'minP', 'Min P', '过滤低于相对概率阈值的 Token', 'language'),
  parameter('PRESENCE_PENALTY', 'language', 'presencePenalty', '存在惩罚', '降低已出现内容再次出现的概率', 'language'),
  parameter('FREQUENCY_PENALTY', 'language', 'frequencyPenalty', '频率惩罚', '按出现频率降低重复内容', 'language'),
  parameter('REPETITION_PENALTY', 'language', 'repetitionPenalty', '重复惩罚', '控制重复 Token 的惩罚倍率', 'language'),
  parameter('STOP_SEQUENCES', 'language', 'stopSequences', '停止序列', '遇到指定文本序列时停止生成', 'language'),
  parameter('SEED', 'language', 'seed', '随机种子', '尽可能复现相同的生成结果', 'language'),
  parameter('LOGPROBS', 'language', 'logprobs', 'Token 概率', '返回输出 Token 的对数概率', 'language'),
  parameter('TOP_LOGPROBS', 'language', 'topLogprobs', '候选 Token 概率数', '返回每个位置概率最高的候选 Token', 'language'),
  parameter('PARALLEL_TOOL_CALLS', 'language', 'parallelToolCalls', '并行工具调用', '允许模型在一步中发起多个工具调用', 'language'),
  parameter('REASONING', 'language', 'reasoning', '推理模式', '映射开启、关闭及低中高推理强度', 'language'),
  parameter('DIMENSIONS', 'embedding', 'dimensions', '向量维度', '指定 Embedding 输出向量的维度', 'embedding'),
  parameter('TOP_N', 'rerank', 'topN', '返回结果数', '指定 Rerank 返回的最高排名结果数', 'rerank'),
  parameter('IMAGE_COUNT', 'imageGeneration', 'n', '图片数量', '指定单次请求生成的图片数量', 'image-generation'),
  parameter('IMAGE_SIZE', 'imageGeneration', 'size', '图片尺寸', '指定图片宽高或尺寸字符串', 'image-generation'),
  parameter('ASPECT_RATIO', 'imageGeneration', 'aspectRatio', '图片比例', '指定图片宽高比', 'image-generation'),
  parameter('IMAGE_SEED', 'imageGeneration', 'seed', '图片随机种子', '尽可能复现相同的图片结果', 'image-generation'),
  parameter('RESPONSE_FORMAT', 'imageGeneration', 'responseFormat', '图片返回格式', '选择 URL 或 Base64 等返回格式', 'image-generation'),
  parameter('NEGATIVE_PROMPT', 'imageGeneration', 'negativePrompt', '反向提示词', '描述图片中不希望出现的内容', 'image-generation'),
]

function parameter(
  parameter: string,
  domain: keyof ModelParameterMappings,
  field: string,
  label: string,
  description: string,
  modelType: MappingModelType,
): ParameterDefinition {
  return { parameter, domain, field, label, description, modelType }
}

export function definitionsForModelType(modelType?: string) {
  return PARAMETER_DEFINITIONS.filter((item) => !modelType || item.modelType === modelType)
}

export function mappingsForModelType(
  mappings: ModelParameterMappings | undefined,
  modelType: MappingModelType,
) {
  const domain = PARAMETER_DEFINITIONS.find((item) => item.modelType === modelType)?.domain
  if (!domain || !mappings?.[domain]) return undefined
  return { [domain]: mappings[domain] } as ModelParameterMappings
}

export function templatesForParameter(
  templates: ParameterMappingTemplateInfo[] | undefined,
  definition: ParameterDefinition,
  adapterType?: string,
) {
  return (templates || []).filter(
    (template) =>
      template.parameter === definition.parameter &&
      template.modelType === definition.modelType &&
      (!adapterType || !template.adapterTypes?.length || template.adapterTypes.includes(adapterType as never)),
  )
}

export function readSelection(
  mappings: ModelParameterMappings | undefined,
  definition: ParameterDefinition,
): Selection | undefined {
  const domain = mappings?.[definition.domain] as Record<string, Selection> | undefined
  return domain?.[definition.field]
}

export function writeSelection(
  mappings: ModelParameterMappings | undefined,
  definition: ParameterDefinition,
  selection: Selection | undefined,
): ModelParameterMappings | undefined {
  const next = { ...mappings } as Record<string, Record<string, Selection>>
  const domain = { ...(mappings?.[definition.domain] as Record<string, Selection> | undefined) }
  if (selection) {
    domain[definition.field] = selection
  } else {
    delete domain[definition.field]
  }
  if (Object.keys(domain).length) {
    next[definition.domain] = domain
  } else {
    delete next[definition.domain]
  }
  return Object.keys(next).length ? (next as ModelParameterMappings) : undefined
}

export function effectiveSelection(
  context: MappingContext,
  definition: ParameterDefinition,
  mappings: ModelParameterMappings | undefined,
  inheritedMappings: ModelParameterMappings | undefined,
  defaults: Record<string, DefaultParameterMappingInfo> | undefined,
) {
  const own = readSelection(mappings, definition)
  if (own && own.mode !== 'INHERIT') {
    return { selection: own, source: context === 'provider' ? 'Provider 覆盖' : 'Model 覆盖' }
  }
  if (context === 'model') {
    const provider = readSelection(inheritedMappings, definition)
    if (provider && provider.mode !== 'INHERIT') {
      return { selection: provider, source: '继承 Provider' }
    }
  }
  const builtIn = defaults?.[definition.parameter]
  return {
    selection: builtIn
      ? ({ mode: builtIn.mode as Selection['mode'], template: builtIn.template } as Selection)
      : undefined,
    source: '内置默认',
  }
}

export function validateReasoningMappings(mappings?: ModelParameterMappings) {
  const errors: string[] = []
  const definition = PARAMETER_DEFINITIONS.find((item) => item.parameter === 'REASONING')
  if (!definition) return errors
  const selection = readSelection(mappings, definition)
  if (selection?.mode !== 'TEMPLATE' || !selection.reasoningMapping) return errors
  const labels = { enabled: '开启', disabled: '关闭', low: '低', medium: '中', high: '高' }
  const configured = Object.entries(labels).filter(
    ([intent]) => selection.reasoningMapping?.[intent as keyof typeof labels],
  )
  if (!configured.length) {
    return ['推理模式至少需要配置一个档位，或者将整个推理参数标记为不支持']
  }
  for (const [intent, label] of configured) {
    const value = selection.reasoningMapping?.[intent as keyof typeof labels]
    if (!value?.field?.trim() || !value.valueType || !value.value?.trim()) {
      errors.push(`推理模式“${label}”需要完整填写请求字段、值类型和请求值`)
      continue
    }
    if (value.valueType === 'BOOLEAN' && !['true', 'false'].includes(value.value)) {
      errors.push(`推理模式“${label}”的布尔值只能是 true 或 false`)
    }
    if (value.valueType === 'INTEGER' && !/^-?\d+$/.test(value.value)) {
      errors.push(`推理模式“${label}”的请求值必须是整数`)
    }
    if (value.valueType === 'DECIMAL' && !Number.isFinite(Number(value.value))) {
      errors.push(`推理模式“${label}”的请求值必须是有效数字`)
    }
  }
  return errors
}
