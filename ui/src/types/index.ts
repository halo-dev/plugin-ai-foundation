import {
  AiModelSpecFeaturesEnum,
  AiModelSpecModelTypeEnum,
  DiscoveredModelItemConfidenceEnum,
  DiscoveredModelItemSourceEnum,
} from '@/api/generated'

export const MODEL_TYPE_OPTIONS = [
  { label: '语言模型', value: AiModelSpecModelTypeEnum.Language },
  { label: 'Embedding', value: AiModelSpecModelTypeEnum.Embedding },
  { label: 'Rerank', value: AiModelSpecModelTypeEnum.Rerank },
  { label: '图像生成', value: AiModelSpecModelTypeEnum.ImageGeneration },
] as const

export const MODEL_FEATURE_OPTIONS = [
  { label: '流式输出', value: AiModelSpecFeaturesEnum.Streaming },
  { label: '视觉', value: AiModelSpecFeaturesEnum.Vision },
  { label: '工具调用', value: AiModelSpecFeaturesEnum.ToolCall },
  { label: '结构化输出', value: AiModelSpecFeaturesEnum.StructuredOutput },
  { label: '推理', value: AiModelSpecFeaturesEnum.Reasoning },
] as const

const MODEL_TYPE_LABELS = Object.fromEntries(
  MODEL_TYPE_OPTIONS.map((item) => [item.value, item.label]),
)
const MODEL_FEATURE_LABELS = Object.fromEntries(
  MODEL_FEATURE_OPTIONS.map((item) => [item.value, item.label]),
)

const DISCOVERY_SOURCE_LABELS: Record<string, string> = {
  [DiscoveredModelItemSourceEnum.Remote]: '远程',
  [DiscoveredModelItemSourceEnum.Catalog]: '目录',
  [DiscoveredModelItemSourceEnum.Rule]: '规则',
  [DiscoveredModelItemSourceEnum.Manual]: '手动',
}

const DISCOVERY_CONFIDENCE_LABELS: Record<string, string> = {
  [DiscoveredModelItemConfidenceEnum.High]: '高',
  [DiscoveredModelItemConfidenceEnum.Medium]: '中',
  [DiscoveredModelItemConfidenceEnum.Low]: '低',
}

export function modelTypeLabel(value?: string) {
  return value ? MODEL_TYPE_LABELS[value] || value : '-'
}

export function modelFeatureLabel(value?: string) {
  return value ? MODEL_FEATURE_LABELS[value] || value : '-'
}

export function discoverySourceLabel(value?: string) {
  return value ? DISCOVERY_SOURCE_LABELS[value] || value : '-'
}

export function discoveryConfidenceLabel(value?: string) {
  return value ? DISCOVERY_CONFIDENCE_LABELS[value] || value : '-'
}
