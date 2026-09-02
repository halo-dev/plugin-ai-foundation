import type {
  AiModel,
  AiProvider,
  DefaultParameterMappingInfo,
  ProviderTypeInfo,
} from '@/api/generated'
import {
  AiModelSpecAdapterTypeEnum,
  AiModelSpecDiscoveryConfidenceEnum,
  AiModelSpecDiscoverySourceEnum,
  AiModelSpecModelTypeEnum,
} from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'
import { MODEL_FEATURE_OPTIONS, MODEL_TYPE_OPTIONS } from '@/types'

export function findProviderTypeForModel(
  model: AiModel,
  providers: AiProvider[] | undefined,
  providerTypes: ProviderTypeInfo[] | undefined,
) {
  const provider = providers?.find((p) => p.metadata.name === model.spec.providerName)
  return providerTypes?.find((type) => type.providerType === provider?.spec.providerType)
}

export function modelTypeOptionsForProviderType(providerType: ProviderTypeInfo | undefined) {
  if (!providerType) {
    return [...MODEL_TYPE_OPTIONS]
  }
  const supportedTypes = providerType.supportedModelTypes || []
  return MODEL_TYPE_OPTIONS.filter((item) => supportedTypes.includes(item.value))
}

export function modelFeatureOptionsForProviderType(
  providerType: ProviderTypeInfo | undefined,
  adapterType?: string,
) {
  if (!providerType) {
    return [...MODEL_FEATURE_OPTIONS]
  }
  const adapter = providerType.adapters?.find((item) => item.adapterType === adapterType)
  const supportedFeatures = adapter?.supportedFeatures ?? providerType.supportedFeatures ?? []
  return MODEL_FEATURE_OPTIONS.filter((item) => supportedFeatures.includes(item.value))
}

export function defaultModelTypeForProviderType(
  providerType: ProviderTypeInfo | undefined,
  candidate?: string,
) {
  const options = modelTypeOptionsForProviderType(providerType)
  const matched = options.find((item) => item.value === candidate)
  return matched?.value || options[0]?.value || AiModelSpecModelTypeEnum.Language
}

export interface ModelAdapterOption {
  value: AiModelSpecAdapterTypeEnum
  label: string
  description?: string
  recommended: boolean
}

export function adapterOptionsForProviderType(
  providerType: ProviderTypeInfo | undefined,
  modelType: string,
): ModelAdapterOption[] {
  if (!providerType) {
    return []
  }
  if (providerType.adapters?.length) {
    return providerType.adapters
      .filter((adapter) => adapter.modelType === modelType && adapter.adapterType)
      .map((adapter) => ({
        value: adapter.adapterType as AiModelSpecAdapterTypeEnum,
        label: `${adapter.displayName || adapter.adapterType}${adapter.recommended ? '（推荐）' : ''}`,
        description: adapter.description,
        recommended: adapter.recommended === true,
      }))
  }
  return []
}

export function defaultAdapterForProviderType(
  providerType: ProviderTypeInfo | undefined,
  modelType: string,
  candidate?: string,
) {
  const options = adapterOptionsForProviderType(providerType, modelType)
  return (
    options.find((option) => option.value === candidate)?.value ||
    options.find((option) => option.recommended)?.value ||
    options[0]?.value
  )
}

export function defaultParameterMappingsForAdapter(
  providerType: ProviderTypeInfo | undefined,
  adapterType?: string,
): Record<string, DefaultParameterMappingInfo> | undefined {
  const adapter = providerType?.adapters?.find((item) => item.adapterType === adapterType)
  const defaults = providerType?.defaultParameterMappings
  const overrides = adapter?.defaultParameterMappingOverrides
  if (!overrides) {
    return defaults
  }
  return { ...defaults, ...overrides }
}

export function filterModelFeaturesForProviderType(
  providerType: ProviderTypeInfo | undefined,
  features: string[] = [],
  adapterType?: string,
) {
  const allowedFeatures = new Set<string>(
    modelFeatureOptionsForProviderType(providerType, adapterType).map((item) => item.value),
  )
  return features.filter((feature) => allowedFeatures.has(feature)) as NonNullable<
    AiModel['spec']['features']
  >
}

export interface DiscoveredModelProfileOverride {
  modelType?: AiModel['spec']['modelType']
  adapterType?: AiModel['spec']['adapterType']
  features?: NonNullable<AiModel['spec']['features']>
}

export interface DiscoveredModelProfile {
  modelType: AiModel['spec']['modelType']
  adapterType?: AiModel['spec']['adapterType']
  features: NonNullable<AiModel['spec']['features']>
}

export type DiscoveredModelProfiles = Record<string, DiscoveredModelProfile>

export interface ModelImportFailure {
  modelId: string
  reason: unknown
}

export interface DiscoveredModelGroup {
  key: string
  label: string
  models: DiscoveredModel[]
}

export function discoveredModelProfileForProviderType(
  providerType: ProviderTypeInfo | undefined,
  model: DiscoveredModel,
  existing?: DiscoveredModelProfile,
): DiscoveredModelProfile {
  const modelType = defaultModelTypeForProviderType(
    providerType,
    existing?.modelType || model.modelType,
  )
  const candidateAdapter = existing?.adapterType || model.adapterType
  const adapterOptions = adapterOptionsForProviderType(providerType, modelType)
  const adapterType =
    adapterOptions.find((option) => option.value === candidateAdapter)?.value ||
    (adapterOptions.length === 0 && modelType === model.modelType
      ? model.adapterType
      : undefined) ||
    (adapterOptions.length === 1 ? adapterOptions[0].value : undefined)
  return {
    modelType,
    ...(adapterType ? { adapterType } : {}),
    features: filterModelFeaturesForProviderType(
      providerType,
      existing?.features || model.features || [],
      adapterType,
    ),
  }
}

export function syncDiscoveredModelProfiles(
  models: DiscoveredModel[],
  providerType: ProviderTypeInfo | undefined,
  existingProfiles: DiscoveredModelProfiles,
): DiscoveredModelProfiles {
  return models.reduce<DiscoveredModelProfiles>((profiles, model) => {
    profiles[model.modelId] = discoveredModelProfileForProviderType(
      providerType,
      model,
      existingProfiles[model.modelId],
    )
    return profiles
  }, {})
}

export function groupDiscoveredModels(models: DiscoveredModel[]): DiscoveredModelGroup[] {
  const groups: DiscoveredModelGroup[] = []

  for (const item of MODEL_TYPE_OPTIONS) {
    const typedModels = models.filter((model) => model.modelType === item.value)
    if (typedModels.length > 0) {
      groups.push({
        key: item.value,
        label: item.label,
        models: typedModels,
      })
    }
  }

  const knownTypes = new Set<string>(MODEL_TYPE_OPTIONS.map((item) => item.value))
  const otherModels = models.filter((model) => !knownTypes.has(model.modelType))
  if (otherModels.length > 0) {
    groups.push({
      key: 'other',
      label: '其他',
      models: otherModels,
    })
  }

  return groups
}

export function summarizeModelImportResults(
  models: Array<Pick<DiscoveredModel, 'modelId'>>,
  results: PromiseSettledResult<unknown>[],
) {
  const failed = results.reduce<ModelImportFailure[]>((items, result, index) => {
    if (result.status === 'rejected') {
      items.push({
        modelId: models[index]?.modelId || '未知模型',
        reason: result.reason,
      })
    }
    return items
  }, [])

  return {
    succeeded: results.length - failed.length,
    failed,
  }
}

export function modelImportFailureMessage(failure: ModelImportFailure) {
  const reason = failure.reason
  const message =
    reason instanceof Error
      ? reason.message
      : typeof reason === 'object' &&
          reason !== null &&
          'message' in reason &&
          typeof reason.message === 'string'
        ? reason.message
        : String(reason || '未知错误')
  return `${failure.modelId}: ${message}`
}

export function createModelFromDiscovered(
  providerName: string,
  model: DiscoveredModel,
  override?: DiscoveredModelProfileOverride,
): AiModel {
  const modelType = override?.modelType || model.modelType || AiModelSpecModelTypeEnum.Language
  const adapterType =
    override?.adapterType || (modelType === model.modelType ? model.adapterType : undefined)
  const spec = {
    providerName,
    modelId: model.modelId,
    displayName: model.displayName || model.modelId,
    enabled: true,
    modelType,
    features: override?.features || model.features || [],
    discoverySource: model.source || AiModelSpecDiscoverySourceEnum.Rule,
    discoveryConfidence: model.confidence || AiModelSpecDiscoveryConfidenceEnum.Low,
    ...(adapterType ? { adapterType } : {}),
    ...discoveredCapabilityFields(model, modelType, override),
  } as AiModel['spec']

  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: {
      name: '',
    },
    spec,
  }
}

function discoveredCapabilityFields(
  model: DiscoveredModel,
  modelType: AiModel['spec']['modelType'],
  override?: DiscoveredModelProfileOverride,
): Partial<AiModel['spec']> {
  if (!model.capabilities && !model.capabilitySources) {
    return {}
  }
  if (!override?.modelType || override.modelType === model.modelType) {
    return {
      capabilities: model.capabilities,
      capabilitySources: model.capabilitySources,
    }
  }
  if (modelType === AiModelSpecModelTypeEnum.Language && model.capabilities?.language) {
    return {
      capabilities: {
        language: model.capabilities.language,
        sources: model.capabilities.sources?.language
          ? { language: model.capabilities.sources.language }
          : undefined,
      },
      capabilitySources: model.capabilitySources?.language
        ? { language: model.capabilitySources.language }
        : undefined,
    }
  }
  if (
    modelType === AiModelSpecModelTypeEnum.ImageGeneration &&
    model.capabilities?.imageGeneration
  ) {
    return {
      capabilities: {
        imageGeneration: model.capabilities.imageGeneration,
        sources: model.capabilities.sources?.imageGeneration
          ? { imageGeneration: model.capabilities.sources.imageGeneration }
          : undefined,
      },
      capabilitySources: model.capabilitySources?.imageGeneration
        ? { imageGeneration: model.capabilitySources.imageGeneration }
        : undefined,
    }
  }
  return {}
}
