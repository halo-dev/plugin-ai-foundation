import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import {
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

export function modelFeatureOptionsForProviderType(providerType: ProviderTypeInfo | undefined) {
  if (!providerType) {
    return [...MODEL_FEATURE_OPTIONS]
  }
  const supportedFeatures = providerType.supportedFeatures || []
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

export function filterModelFeaturesForProviderType(
  providerType: ProviderTypeInfo | undefined,
  features: string[] = [],
) {
  const allowedFeatures = new Set<string>(
    modelFeatureOptionsForProviderType(providerType).map((item) => item.value),
  )
  return features.filter((feature) => allowedFeatures.has(feature)) as NonNullable<
    AiModel['spec']['features']
  >
}

export interface DiscoveredModelProfileOverride {
  modelType?: AiModel['spec']['modelType']
  features?: NonNullable<AiModel['spec']['features']>
}

export interface DiscoveredModelProfile {
  modelType: AiModel['spec']['modelType']
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
  return {
    modelType: defaultModelTypeForProviderType(
      providerType,
      existing?.modelType || model.modelType,
    ),
    features: filterModelFeaturesForProviderType(
      providerType,
      existing?.features || model.features || [],
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
  const spec = {
    providerName,
    modelId: model.modelId,
    displayName: model.displayName || model.modelId,
    enabled: true,
    modelType: override?.modelType || model.modelType || AiModelSpecModelTypeEnum.Language,
    features: override?.features || model.features || [],
    discoverySource: model.source || AiModelSpecDiscoverySourceEnum.Rule,
    discoveryConfidence: model.confidence || AiModelSpecDiscoveryConfidenceEnum.Low,
    ...(model.adapterType ? { adapterType: model.adapterType } : {}),
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
