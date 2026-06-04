import {
  ModelOptionFeaturesEnum,
  ModelOptionModelTypeEnum,
  ModelOptionUnavailableReasonEnum,
  type ModelOption,
} from '@/api/generated'

export function normalizeRequiredFeatures(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value.map((item) => item.trim()).filter(Boolean)
  }

  return value
    ?.split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export function isModelOptionSelectable(model: ModelOption) {
  return model.available !== false
}

export function modelOptionDisplayName(model: ModelOption) {
  return model.displayName || model.modelId || model.name
}

export function selectedModelDisplayName(
  selectedModel: ModelOption | undefined,
  snapshot: ModelOption | undefined,
  selectedValue: string,
) {
  const model = selectedModel || snapshot
  return model ? modelOptionDisplayName(model) : selectedValue
}

export function nextActiveModelName(
  models: ModelOption[],
  selectedValue: string,
  currentActiveName?: string,
) {
  if (currentActiveName && models.some((model) => model.name === currentActiveName)) {
    return currentActiveName
  }

  return models.find((model) => model.name === selectedValue)?.name || models[0]?.name
}

export function shouldShowModelId(model: ModelOption) {
  return Boolean(
    model.modelId && model.modelId !== model.displayName && model.modelId !== model.name,
  )
}

export function shouldShowModelDetails(model: ModelOption) {
  return Boolean(model.modelType || model.features?.length || !isModelOptionSelectable(model))
}

export function modelFeatureLabel(feature: string): string {
  switch (feature) {
    case ModelOptionFeaturesEnum.Streaming:
      return '流式'
    case ModelOptionFeaturesEnum.Vision:
      return '视觉'
    case ModelOptionFeaturesEnum.ToolCall:
      return '工具调用'
    case ModelOptionFeaturesEnum.StructuredOutput:
      return '结构化'
    case ModelOptionFeaturesEnum.Reasoning:
      return '推理'
    default:
      return feature
  }
}

export function modelTypeLabel(type?: string): string {
  switch (type) {
    case ModelOptionModelTypeEnum.Language:
      return '语言'
    case ModelOptionModelTypeEnum.Embedding:
      return '向量'
    case ModelOptionModelTypeEnum.Rerank:
      return '重排'
    case ModelOptionModelTypeEnum.ImageGeneration:
      return '图像'
    default:
      return ''
  }
}

export function modelOptionUnavailableReasonLabel(reason?: string) {
  switch (reason) {
    case ModelOptionUnavailableReasonEnum.ModelDisabled:
      return '模型已禁用'
    case ModelOptionUnavailableReasonEnum.ProviderMissing:
      return '供应商不存在'
    case ModelOptionUnavailableReasonEnum.ProviderDisabled:
      return '供应商已禁用'
    default:
      return '暂不可用'
  }
}
