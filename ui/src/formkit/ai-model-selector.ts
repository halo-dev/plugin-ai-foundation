import {
  ModelOptionFeaturesEnum,
  ModelOptionModelTypeEnum,
  ModelOptionUnavailableReasonEnum,
  type ModelOption,
} from '@/api/generated'
import { capabilitySummaryLabels } from '@/utils/capabilities'

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

export function modelCapabilityLabels(model: ModelOption) {
  const featureLabels = new Set((model.features ?? []).map(modelFeatureLabel))
  return capabilitySummaryLabels(model.capabilities).filter((label) => !featureLabels.has(label))
}

export function modelDetailLabels(model: ModelOption) {
  return Array.from(
    new Set(
      [
        modelTypeLabel(model.modelType),
        ...(model.features ?? []).map(modelFeatureLabel),
        ...modelCapabilityLabels(model),
      ].filter(Boolean),
    ),
  )
}

export function shouldShowModelId(model: ModelOption) {
  return Boolean(
    model.modelId && model.modelId !== model.displayName && model.modelId !== model.name,
  )
}

export function shouldShowModelDetails(model: ModelOption) {
  return modelDetailLabels(model).length > 0 || !isModelOptionSelectable(model)
}

export function modelFeatureLabel(feature: string): string {
  switch (feature) {
    case ModelOptionFeaturesEnum.Streaming:
      return '流式'
    case ModelOptionFeaturesEnum.Vision:
      return '图片识别'
    case ModelOptionFeaturesEnum.AudioInput:
      return '音频识别'
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
    case ModelOptionUnavailableReasonEnum.CapabilityUnsupported:
      return '能力不满足'
    default:
      return '暂不可用'
  }
}
