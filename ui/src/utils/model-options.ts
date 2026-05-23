import type { ModelOption } from '@/api/generated'

export interface ModelOptionGroup {
  key: string
  label: string
  models: ModelOption[]
}

export function modelOptionLabel(model: ModelOption) {
  const modelName = model.displayName || model.modelId || model.name || '-'
  const providerName = model.provider?.displayName || model.provider?.name || '-'
  return `${modelName} / ${providerName}`
}

export function modelOptionProviderLabel(model: ModelOption) {
  const providerName = model.provider?.displayName || model.provider?.name || '未知供应商'
  const providerType = model.provider?.providerTypeDisplayName || model.provider?.providerType
  return providerType ? `${providerName} (${providerType})` : providerName
}

export function groupModelOptionsByProvider(models: ModelOption[] | undefined) {
  const groups = new Map<string, ModelOptionGroup>()

  for (const model of models || []) {
    const key = model.provider?.name || modelOptionProviderLabel(model)
    let group = groups.get(key)

    if (!group) {
      group = {
        key,
        label: modelOptionProviderLabel(model),
        models: [],
      }
      groups.set(key, group)
    }

    group.models.push(model)
  }

  return Array.from(groups.values())
}
