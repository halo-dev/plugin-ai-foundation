import type {
  DefaultParameterMappingInfo,
  ModelParameterDefinitionInfo,
  ModelParameterDefinitionInfoDomainEnum,
  ModelParameterMappings,
  ParameterMappingTemplateInfo,
  Selection,
} from '@/api/generated'

export type MappingContext = 'provider' | 'model'
export type MappingModelType = 'language' | 'embedding' | 'rerank' | 'image-generation'

export interface ParameterDefinition extends ModelParameterDefinitionInfo {
  parameter: string
  domain: ModelParameterDefinitionInfoDomainEnum & keyof ModelParameterMappings
  field: string
  displayName: string
  description: string
  modelType: MappingModelType
  common: boolean
}

export function parameterDefinitionsForModelType(
  definitions: ModelParameterDefinitionInfo[] | undefined,
  modelType?: string,
) {
  return (definitions || []).filter(
    (definition): definition is ParameterDefinition =>
      isParameterDefinition(definition) && (!modelType || definition.modelType === modelType),
  )
}

export function mappingsForModelType(
  mappings: ModelParameterMappings | undefined,
  modelType: MappingModelType,
  definitions: ModelParameterDefinitionInfo[] | undefined,
) {
  const domain = parameterDefinitionsForModelType(definitions, modelType)[0]?.domain
  if (!domain) return mappings
  if (!mappings?.[domain]) return undefined
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
      (!adapterType ||
        !template.adapterTypes?.length ||
        template.adapterTypes.includes(adapterType as never)),
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
  const selection = mappings?.language?.reasoning
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

function isParameterDefinition(
  definition: ModelParameterDefinitionInfo,
): definition is ParameterDefinition {
  return Boolean(
    definition.parameter &&
    definition.field &&
    definition.displayName &&
    definition.description &&
    definition.modelType &&
    definition.common !== undefined &&
    definition.domain,
  )
}
