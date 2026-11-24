import type { ProviderTypeInfo } from '@/api/generated'
import { DiscoveredModelItemSyncStatusEnum } from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'
import { modelFeatureLabel, modelTypeLabel } from '@/types'

export interface DiscoveredModelSyncGroup {
  key: DiscoveredModelItemSyncStatusEnum
  label: string
  models: DiscoveredModel[]
}

const SYNC_GROUPS = [
  { key: DiscoveredModelItemSyncStatusEnum.New, label: '新增模型' },
  { key: DiscoveredModelItemSyncStatusEnum.Existing, label: '已存在模型' },
  { key: DiscoveredModelItemSyncStatusEnum.Disabled, label: '本地已禁用' },
] as const

export function syncStatusOf(model: DiscoveredModel) {
  return model.syncStatus || DiscoveredModelItemSyncStatusEnum.New
}

export function isNewDiscoveredModel(model: DiscoveredModel) {
  return syncStatusOf(model) === DiscoveredModelItemSyncStatusEnum.New
}

export function hasLocalModel(model: DiscoveredModel) {
  return !isNewDiscoveredModel(model)
}

export function canSyncDiscoveredModel(model: DiscoveredModel) {
  return hasLocalModel(model) && model.syncable !== false
}

export function hasSyncDifferences(model: DiscoveredModel) {
  return (
    canSyncDiscoveredModel(model) &&
    (model.differences || []).some((difference) => difference.applied)
  )
}

export function appliedDifferenceCount(model: DiscoveredModel) {
  return (model.differences || []).filter((difference) => difference.applied).length
}

export function groupDiscoveredModelsBySyncStatus(
  models: DiscoveredModel[],
): DiscoveredModelSyncGroup[] {
  return SYNC_GROUPS.map((group) => ({
    key: group.key,
    label: group.label,
    models: models.filter((model) => syncStatusOf(model) === group.key),
  })).filter((group) => group.models.length > 0)
}

const DIFFERENCE_LABELS: Record<string, string> = {
  displayName: '显示名称',
  modelType: '模型类型',
  adapterType: '调用接口',
  features: '模型特性',
  'capabilities.language.imageInput': '图片识别',
  'capabilities.language.fileInput': '文件识别',
  'capabilities.language.reasoningHistory': '推理历史',
  'capabilities.language.inputMediaTypes': '支持媒体类型',
  'capabilities.language.inputSources': '输入来源',
  'capabilities.imageGeneration.textToImage': '文生图',
  'capabilities.imageGeneration.imageToImage': '图生图',
  'capabilities.imageGeneration.maskInput': '蒙版',
  'capabilities.imageGeneration.maxImagesPerCall': '单次图片数量',
  'capabilities.imageGeneration.sizes': '图片尺寸',
  'capabilities.imageGeneration.aspectRatios': '宽高比',
  'capabilities.imageGeneration.outputMediaTypes': '输出媒体类型',
}

export function syncDifferenceLabel(field?: string) {
  if (!field) return ''
  return DIFFERENCE_LABELS[field] || field
}

const BOOLEAN_FIELDS = new Set([
  'capabilities.language.imageInput',
  'capabilities.language.fileInput',
  'capabilities.language.reasoningHistory',
  'capabilities.imageGeneration.textToImage',
  'capabilities.imageGeneration.imageToImage',
  'capabilities.imageGeneration.maskInput',
])

export function syncDifferenceValue(
  field: string | undefined,
  value: string | null | undefined,
  providerType?: ProviderTypeInfo,
) {
  if (value === undefined || value === null || value === '') {
    return '未配置'
  }
  if (field === 'modelType') {
    return modelTypeLabel(value)
  }
  if (field === 'adapterType') {
    const adapter = providerType?.adapters?.find((item) => item.adapterType === value)
    return adapter?.displayName || value
  }
  if (field === 'features') {
    return value
      .split(', ')
      .filter(Boolean)
      .map((feature) => modelFeatureLabel(feature))
      .join('、')
  }
  if (field === 'capabilities.language.inputSources') {
    const labels: Record<string, string> = { data: '文件数据', url: 'URL' }
    return value
      .split(', ')
      .filter(Boolean)
      .map((source) => labels[source] || source)
      .join('、')
  }
  if (field && BOOLEAN_FIELDS.has(field)) {
    return value === 'true' ? '支持' : '不支持'
  }
  return value
}
