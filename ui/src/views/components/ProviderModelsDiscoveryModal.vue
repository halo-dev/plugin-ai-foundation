<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type {
  AiModel,
  AiModelSpecAdapterTypeEnum,
  AiModelSpecFeaturesEnum,
  AiProvider,
  ModelParameterConstraints,
  ModelSyncOperation,
  ModelSyncResult,
} from '@/api/generated'
import { ModelSyncOperationActionEnum, ModelSyncResultOutcomeEnum } from '@/api/generated'
import {
  QK_MODELS,
  useDiscoverModelsFetch,
  useModelCatalogStatusFetch,
  type DiscoveredModel,
} from '@/composables/use-models-fetch'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import {
  capabilityDomainSource,
  capabilitySourceLabel,
  capabilitySummaryLabels,
} from '@/utils/capabilities'
import { setFocus } from '@/utils/focus'
import {
  adapterOptionsForProviderType,
  createModelFromDiscovered,
  discoveredModelProfileForProviderType,
  modelFeatureOptionsForProviderType,
  modelTypeOptionsForProviderType,
  type DiscoveredModelProfiles,
} from '@/utils/model'
import { parameterFeatures, withFeatureSupport } from '@/utils/model-feature-support'
import {
  appliedDifferenceCount,
  groupDiscoveredModelsBySyncStatus,
  hasSyncDifferences,
  isNewDiscoveredModel,
  syncDifferenceLabel,
  syncDifferenceValue,
  syncStatusOf,
} from '@/utils/model-sync'
import {
  Dialog,
  Toast,
  VButton,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VTag,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useFuse } from '@vueuse/integrations/useFuse'
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import RiCheckLine from '~icons/ri/check-line'
import ModelCatalogProvenance from './ModelCatalogProvenance.vue'
import ModelCatalogStatus from './ModelCatalogStatus.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = ref<InstanceType<typeof VModal> | null>(null)

const providerName = computed(() => props.provider.metadata.name || '')

const { data: models, isLoading, refetch: refetchModels } = useDiscoverModelsFetch(providerName)
const isRefreshingCatalog = ref(false)
const { data: catalogStatus, refetch: refetchCatalogStatus } = useModelCatalogStatusFetch()
const editedModelIds = ref(new Set<string>())
const featureChoices = ref<Record<string, Partial<Record<AiModelSpecFeaturesEnum, boolean>>>>({})

async function refreshCatalog() {
  if (isRefreshingCatalog.value || isSyncing.value) return
  isRefreshingCatalog.value = true
  try {
    const { data } = await aiConsoleApiClient.provider.refreshModelCatalog()
    await refetchCatalogStatus()
    if (data.error) {
      Toast.warning(data.error)
    } else {
      Toast.success('能力目录已刷新')
    }
    // Only refresh discovery. Saved models are never updated by this action.
    await refetchModels()
  } catch {
    Toast.warning('能力目录刷新失败，仍可使用当前模型列表')
  } finally {
    isRefreshingCatalog.value = false
  }
}
const {
  data: providerTypes,
  isLoading: isLoadingProviderTypes,
  isError: isProviderTypesError,
  refetch: refetchProviderTypes,
} = useProviderTypesFetch()
const selectedProviderType = computed(() =>
  providerTypes.value?.find((type) => type.providerType === props.provider.spec.providerType),
)

const keyword = shallowRef('')

const allModels = computed(() => models.value?.models || [])
const allMissingModels = computed(() => models.value?.missingModels || [])

const { results } = useFuse(keyword, allModels, {
  fuseOptions: {
    keys: ['displayName', 'modelId'],
    threshold: 0.3,
  },
})

const filteredModels = computed(() => {
  if (!keyword.value) {
    return allModels.value
  }
  return results.value.map((r) => r.item)
})

const filteredMissingModels = computed(() => {
  if (!keyword.value) {
    return allMissingModels.value
  }
  const query = keyword.value.toLowerCase()
  return allMissingModels.value.filter(
    (model) =>
      model.modelId?.toLowerCase().includes(query) ||
      model.displayName?.toLowerCase().includes(query),
  )
})

const filteredModelGroups = computed(() => groupDiscoveredModelsBySyncStatus(filteredModels.value))

const selectedModels = ref<Set<string>>(new Set())
const profileOverrides = ref<DiscoveredModelProfiles>({})
const isSyncing = shallowRef(false)
const selectionInitialized = ref(false)
const selectedModelCount = computed(() => selectedModels.value.size)
watch([() => catalogStatus.value?.fetchedAt, isLoading], ([fetchedAt, loading]) => {
  if (
    fetchedAt &&
    fetchedAt !== models.value?.catalog?.fetchedAt &&
    !loading &&
    !isRefreshingCatalog.value &&
    !isSyncing.value
  ) {
    void refetchModels()
  }
})
const selectedModelsNeedAdapter = computed(() =>
  allModels.value.some(
    (model) =>
      isNewDiscoveredModel(model) &&
      selectedModels.value.has(model.modelId) &&
      !profileFor(model).adapterType,
  ),
)
const selectedVisibleModelCount = computed(() => {
  return filteredModels.value.filter((model) => selectedModels.value.has(model.modelId)).length
})
const allFilteredSelected = computed(() => {
  return (
    filteredModels.value.length > 0 &&
    selectedVisibleModelCount.value === filteredModels.value.length
  )
})
const resultText = computed(() => {
  return keyword.value
    ? `找到 ${filteredModels.value.length} 个模型`
    : `共 ${allModels.value.length} 个模型`
})

const selectedList = computed(() =>
  allModels.value.filter((model) => selectedModels.value.has(model.modelId)),
)
const selectedNewModels = computed(() => selectedList.value.filter(isNewDiscoveredModel))
const selectedUpdateModels = computed(() => selectedList.value.filter(hasSyncDifferences))
const selectedEnableModels = computed(() =>
  selectedList.value.filter(
    (model) => !isNewDiscoveredModel(model) && model.localEnabled === false,
  ),
)
const selectedDisableModels = computed(() =>
  selectedList.value.filter((model) => !isNewDiscoveredModel(model) && model.localEnabled === true),
)

const modelTypeOptions = computed(() => modelTypeOptionsForProviderType(selectedProviderType.value))

function featuresEqual(
  a: NonNullable<AiModel['spec']['features']>,
  b: NonNullable<AiModel['spec']['features']>,
) {
  return a.length === b.length && a.every((item, index) => item === b[index])
}

function profileFor(model: DiscoveredModel) {
  return (
    profileOverrides.value[model.modelId] ||
    discoveredModelProfileForProviderType(selectedProviderType.value, model)
  )
}

function featureOptionsFor(model: DiscoveredModel) {
  return modelFeatureOptionsForProviderType(
    selectedProviderType.value,
    profileFor(model).adapterType,
  )
}

function adapterOptionsFor(model: DiscoveredModel) {
  return adapterOptionsForProviderType(selectedProviderType.value, profileFor(model).modelType)
}

function featuresWithChoices(modelId: string, recommended: AiModelSpecFeaturesEnum[] = []) {
  const features = new Set(recommended)
  const choices = featureChoices.value[modelId] || {}
  for (const feature of Object.keys(choices) as AiModelSpecFeaturesEnum[]) {
    if (choices[feature]) features.add(feature)
    else features.delete(feature)
  }
  return [...features]
}

function setModelType(model: DiscoveredModel, event: Event) {
  editedModelIds.value.add(model.modelId)
  const current = profileFor(model)
  const next = discoveredModelProfileForProviderType(selectedProviderType.value, model, {
    ...current,
    modelType: (event.target as HTMLSelectElement).value as AiModel['spec']['modelType'],
    features: featuresWithChoices(model.modelId, current.features),
  })
  profileOverrides.value = {
    ...profileOverrides.value,
    [model.modelId]: next,
  }
}

function setAdapterType(model: DiscoveredModel, event: Event) {
  editedModelIds.value.add(model.modelId)
  const current = profileFor(model)
  profileOverrides.value = {
    ...profileOverrides.value,
    [model.modelId]: discoveredModelProfileForProviderType(selectedProviderType.value, model, {
      ...current,
      adapterType: (event.target as HTMLSelectElement).value as AiModelSpecAdapterTypeEnum,
      features: featuresWithChoices(model.modelId, current.features),
    }),
  }
}

function isSelected(model: DiscoveredModel) {
  return selectedModels.value.has(model.modelId)
}

function isFeatureEnabled(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum) {
  return profileFor(model).features.includes(feature)
}

function toggleFeature(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum) {
  if (!featureOptionsFor(model).some((item) => item.value === feature)) {
    return
  }
  const profile = profileFor(model)
  const enabled = !profile.features.includes(feature)
  featureChoices.value[model.modelId] = {
    ...featureChoices.value[model.modelId],
    [feature]: enabled,
  }
  let features = profile.features
  if (!profile.features.includes(feature)) {
    features = [...profile.features, feature]
  } else {
    features = profile.features.filter((item) => item !== feature)
  }
  profileOverrides.value = {
    ...profileOverrides.value,
    [model.modelId]: {
      ...profile,
      features,
    },
  }
}

function manualFeatureConstraints(model: DiscoveredModel) {
  if (profileFor(model).modelType !== 'language') return undefined
  let constraints: ModelParameterConstraints | undefined
  const supportedFeatures = featureOptionsFor(model).map((option) => option.value)
  for (const feature of parameterFeatures) {
    const enabled = featureChoices.value[model.modelId]?.[feature]
    if (enabled !== undefined && supportedFeatures.includes(feature)) {
      constraints = withFeatureSupport(constraints, feature, enabled)
    }
  }
  return constraints
}

function capabilitySourceLabels(model: DiscoveredModel) {
  const domains = [
    ['language', '语言能力'],
    ['imageGeneration', '图像能力'],
  ] as const
  return domains
    .map(([domain, label]) => {
      const source = capabilityDomainSource(model.capabilities, model.capabilitySources, domain)
      return source ? `${label}: ${capabilitySourceLabel(source, model.catalogMatch, domain)}` : ''
    })
    .filter(Boolean)
}

function toggleSelection(model: DiscoveredModel) {
  const next = new Set(selectedModels.value)
  if (next.has(model.modelId)) {
    next.delete(model.modelId)
  } else {
    next.add(model.modelId)
  }
  selectedModels.value = next
}

function toggleFilteredSelection() {
  const next = new Set(selectedModels.value)
  if (allFilteredSelected.value) {
    filteredModels.value.forEach((model) => next.delete(model.modelId))
  } else {
    filteredModels.value.forEach((model) => next.add(model.modelId))
  }
  selectedModels.value = next
}

function clearSelection() {
  selectedModels.value = new Set()
}

function resetSelectionToNewModels() {
  selectedModels.value = new Set(
    allModels.value.filter(isNewDiscoveredModel).map((model) => model.modelId),
  )
}

watch(
  models,
  (value) => {
    if (!selectionInitialized.value && value) {
      selectionInitialized.value = true
      resetSelectionToNewModels()
    }
  },
  { immediate: true },
)

function createOperationFor(model: DiscoveredModel): ModelSyncOperation {
  return {
    action: ModelSyncOperationActionEnum.Create,
    modelId: model.modelId,
    model: createModelFromDiscovered(
      providerName.value,
      model,
      {
        ...profileFor(model),
        parameterConstraints: manualFeatureConstraints(model),
      },
      selectedProviderType.value,
    ),
  }
}

function updateOperationFor(model: DiscoveredModel): ModelSyncOperation {
  return {
    action: ModelSyncOperationActionEnum.Update,
    modelId: model.modelId,
    localModelName: model.localModelName,
    discoveredModel: model,
  }
}

function enabledOperationFor(model: DiscoveredModel, enabled: boolean): ModelSyncOperation {
  return {
    action: enabled ? ModelSyncOperationActionEnum.Enable : ModelSyncOperationActionEnum.Disable,
    modelId: model.modelId,
    localModelName: model.localModelName,
  }
}

function applySync() {
  if (isSyncing.value) return
  const creates = selectedNewModels.value
  const updates = selectedUpdateModels.value
  if (creates.length === 0 && updates.length === 0) return
  Dialog.warning({
    title: '应用模型同步',
    description: `将创建 ${creates.length} 个模型，并更新 ${updates.length} 个已存在模型。显示名称、启用状态、参数映射、参数约束和原生选项不会被覆盖；手动维护的能力配置也会保留。`,
    onConfirm: async () => {
      await runSync([...creates.map(createOperationFor), ...updates.map(updateOperationFor)])
    },
  })
}

function changeEnabled(enabled: boolean) {
  if (isSyncing.value) return
  const targets = enabled ? selectedEnableModels.value : selectedDisableModels.value
  if (targets.length === 0) return
  Dialog.warning({
    title: enabled ? '批量启用模型' : '批量禁用模型',
    description: `将${enabled ? '启用' : '禁用'} ${targets.length} 个模型，仅修改模型的启用状态。`,
    onConfirm: async () => {
      await runSync(targets.map((model) => enabledOperationFor(model, enabled)))
    },
  })
}

function formatSyncFailure(result: ModelSyncResult) {
  return `${result.modelId || result.modelName || '未知模型'}: ${result.message || '未知错误'}`
}

async function runSync(operations: ModelSyncOperation[]) {
  if (isSyncing.value || operations.length === 0) return
  isSyncing.value = true
  try {
    const { data } = await aiConsoleApiClient.provider.syncProviderModels({
      name: providerName.value,
      modelSyncRequest: { operations },
    })
    const results = data?.results || []
    const failed = results.filter((result) => result.outcome === ModelSyncResultOutcomeEnum.Failed)
    const skipped = results.filter(
      (result) => result.outcome === ModelSyncResultOutcomeEnum.Skipped,
    )
    const succeeded = results.length - failed.length - skipped.length
    if (failed.length > 0) {
      Toast.warning(
        `同步完成：成功 ${succeeded} 个，失败 ${failed.length} 个\n${failed
          .map(formatSyncFailure)
          .join('\n')}`,
      )
    } else if (skipped.length > 0) {
      Toast.success(`同步完成：成功 ${succeeded} 个，跳过 ${skipped.length} 个`)
    } else {
      Toast.success(`同步完成：成功 ${succeeded} 个`)
    }
    await refetchModels()
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
    resetSelectionToNewModels()
  } catch (error) {
    Toast.error('模型同步失败: ' + (error as Error).message)
  } finally {
    isSyncing.value = false
  }
}

watch(
  [allModels, selectedProviderType],
  ([items, providerType]) => {
    const nextProfiles: DiscoveredModelProfiles = {}
    for (const model of items) {
      const previous = editedModelIds.value.has(model.modelId)
        ? profileOverrides.value[model.modelId]
        : undefined
      nextProfiles[model.modelId] = discoveredModelProfileForProviderType(providerType, model, {
        modelType: previous?.modelType || model.modelType,
        adapterType: previous?.adapterType || model.adapterType,
        features: featuresWithChoices(model.modelId, model.features),
      })
    }
    selectedModels.value = new Set(
      [...selectedModels.value].filter((id) => items.some((item) => item.modelId === id)),
    )
    const isUnchanged =
      Object.keys(nextProfiles).length === Object.keys(profileOverrides.value).length &&
      Object.entries(nextProfiles).every(([modelId, profile]) => {
        const existing = profileOverrides.value[modelId]
        return (
          existing &&
          existing.modelType === profile.modelType &&
          existing.adapterType === profile.adapterType &&
          featuresEqual(existing.features, profile.features)
        )
      })
    if (!isUnchanged) {
      profileOverrides.value = nextProfiles
    }
  },
  { immediate: true },
)

onMounted(() => {
  setFocus('model-discovery-search-input')
})
</script>

<template>
  <VModal
    title="从供应商获取模型"
    ref="modal"
    :width="920"
    mount-to-body
    :centered="false"
    :body-class="['!p-0']"
    @close="emit('close')"
  >
    <div class=":uno: max-h-[72vh] min-h-0 flex flex-col bg-gray-50/60">
      <div class=":uno: flex-none border-b border-gray-100 bg-white px-4 py-3">
        <div class=":uno: mb-3 flex flex-col gap-3 sm:flex-row sm:items-start">
          <div class=":uno: min-w-0 flex-1">
            <div class=":uno: text-sm text-gray-950 font-semibold">
              {{ selectedProviderType?.displayName || provider.spec.providerType }}
            </div>
            <div class=":uno: mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              <span>{{ resultText }}</span>
              <span class=":uno: h-1 w-1 rounded-full bg-gray-300"></span>
              <span>已选择 {{ selectedModelCount }} 个</span>
            </div>
          </div>
          <div class=":uno: flex flex-wrap items-center gap-2">
            <VButton
              size="sm"
              @click="toggleFilteredSelection"
              :disabled="filteredModels.length === 0"
            >
              {{ allFilteredSelected ? '取消当前选择' : '选择所有' }}
            </VButton>
            <VButton size="sm" @click="clearSelection" :disabled="selectedModelCount === 0">
              清空选择
            </VButton>
          </div>
        </div>
        <SearchInput
          id="model-discovery-search-input"
          sync
          v-model="keyword"
          placeholder="搜索模型名称或供应商模型 ID..."
        />
      </div>
      <ModelCatalogStatus
        :status="catalogStatus"
        :refreshing="isRefreshingCatalog"
        :disabled="isSyncing"
        @refresh="refreshCatalog"
      />
      <div class=":uno: min-h-0 flex-1 overflow-y-auto">
        <VLoading v-if="isLoading || isLoadingProviderTypes" />
        <div
          v-else-if="isProviderTypesError || !selectedProviderType"
          class=":uno: flex flex-col items-center gap-3 px-4 py-12 text-sm text-gray-500"
        >
          <span>无法加载供应商接口信息</span>
          <VButton size="sm" @click="refetchProviderTypes()">重试</VButton>
        </div>
        <VEmpty
          v-else-if="allModels.length === 0 && allMissingModels.length === 0"
          title="无数据"
          message="无法获取到模型列表"
        />
        <VEmpty
          v-else-if="filteredModels.length === 0 && filteredMissingModels.length === 0"
          title="无匹配结果"
          message="未找到匹配的模型"
        />
        <div v-else class=":uno: flex flex-col gap-3 p-3">
          <section
            v-for="group in filteredModelGroups"
            :key="group.key"
            class=":uno: overflow-hidden border border-gray-100 rounded-lg bg-white"
          >
            <div
              class=":uno: sticky top-0 z-1 flex items-center justify-between border-b border-gray-100 bg-gray-50/90 px-4 py-2.5 backdrop-blur"
            >
              <div class=":uno: text-xs text-gray-900 font-medium">
                {{ group.label }}
              </div>
              <VTag>{{ group.models.length }} 个</VTag>
            </div>
            <VEntityContainer>
              <VEntity
                @click="toggleSelection(model)"
                v-for="model in group.models"
                :key="model.modelId"
                :class="{
                  ':uno: bg-blue-50/40 hover:bg-blue-50/60': isSelected(model),
                }"
              >
                <template #checkbox>
                  <input
                    @click.stop
                    type="checkbox"
                    :checked="isSelected(model)"
                    @change="toggleSelection(model)"
                  />
                </template>
                <template #start>
                  <VEntityField
                    :title="model.displayName || model.modelId"
                    :description="
                      model.displayName && model.displayName !== model.modelId
                        ? `供应商模型 ID: ${model.modelId}`
                        : ''
                    "
                  />
                </template>
                <template #end>
                  <div
                    v-if="isNewDiscoveredModel(model)"
                    class=":uno: max-w-[34rem] w-full flex flex-col items-stretch gap-2 sm:items-end"
                    @click.stop
                  >
                    <div class=":uno: flex flex-wrap items-center justify-end gap-2">
                      <select
                        :value="profileFor(model).modelType"
                        class=":uno: min-w-30 select-default"
                        @change="setModelType(model, $event)"
                      >
                        <option
                          v-for="item in modelTypeOptions"
                          :key="item.value"
                          :value="item.value"
                        >
                          {{ item.label }}
                        </option>
                      </select>
                      <select
                        v-if="adapterOptionsFor(model).length > 1"
                        :value="profileFor(model).adapterType || ''"
                        :aria-label="`${model.displayName || model.modelId} 调用接口`"
                        class=":uno: min-w-36 select-default"
                        @change="setAdapterType(model, $event)"
                      >
                        <option disabled value="">选择调用接口</option>
                        <option
                          v-for="item in adapterOptionsFor(model)"
                          :key="item.value"
                          :value="item.value"
                        >
                          {{ item.label }}
                        </option>
                      </select>
                    </div>
                    <div class=":uno: flex flex-wrap justify-end gap-1.5">
                      <button
                        v-for="item in featureOptionsFor(model)"
                        :key="item.value"
                        type="button"
                        class=":uno: h-7 inline-flex items-center gap-1 border rounded-md px-2 text-xs transition"
                        :class="
                          isFeatureEnabled(model, item.value)
                            ? ':uno: border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100'
                            : ':uno: border-gray-200 bg-white text-gray-500 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700'
                        "
                        :aria-pressed="isFeatureEnabled(model, item.value)"
                        @click="toggleFeature(model, item.value)"
                      >
                        <RiCheckLine
                          v-if="isFeatureEnabled(model, item.value)"
                          class=":uno: size-3.5"
                        />
                        {{ item.label }}
                      </button>
                    </div>
                    <div
                      v-if="
                        capabilitySummaryLabels(model.capabilities).length ||
                        capabilitySourceLabels(model).length
                      "
                      class=":uno: flex flex-wrap justify-end gap-1.5"
                    >
                      <VTag
                        v-for="capability in capabilitySummaryLabels(model.capabilities)"
                        :key="capability"
                      >
                        {{ capability }}
                      </VTag>
                      <span
                        v-for="source in capabilitySourceLabels(model)"
                        :key="source"
                        class=":uno: h-6 inline-flex items-center rounded bg-gray-50 px-2 text-xs text-gray-500 leading-6"
                      >
                        {{ source }}
                      </span>
                    </div>
                    <ModelCatalogProvenance v-if="model.catalogMatch" :match="model.catalogMatch" />
                  </div>
                  <div
                    v-else
                    class=":uno: max-w-[34rem] w-full flex flex-col items-stretch gap-2 sm:items-end"
                    @click.stop
                  >
                    <div class=":uno: flex flex-wrap items-center justify-end gap-1.5">
                      <VTag>
                        {{ syncStatusOf(model) === 'disabled' ? '本地已禁用' : '已存在' }}
                      </VTag>
                      <VTag v-if="model.localDisplayName">
                        本地显示名: {{ model.localDisplayName }}
                      </VTag>
                      <VTag v-if="model.syncable === false">手动配置，不参与同步</VTag>
                      <VTag v-else-if="!model.differences?.length">无差异</VTag>
                      <VTag v-else-if="!appliedDifferenceCount(model)">
                        {{ model.differences.length }} 项差异，均保留本地
                      </VTag>
                      <VTag v-else>{{ appliedDifferenceCount(model) }} 项差异</VTag>
                    </div>
                    <div
                      v-if="model.differences?.length"
                      class=":uno: border border-gray-100 rounded bg-gray-50/60 p-2 text-xs"
                    >
                      <div
                        v-for="difference in model.differences"
                        :key="difference.field"
                        class=":uno: flex flex-wrap items-center justify-end gap-1.5 py-0.5"
                      >
                        <span class=":uno: text-gray-500">
                          {{ syncDifferenceLabel(difference.field) }}
                        </span>
                        <span class=":uno: text-gray-400">
                          {{
                            syncDifferenceValue(
                              difference.field,
                              difference.localValue,
                              selectedProviderType,
                            )
                          }}
                        </span>
                        <span class=":uno: text-gray-300">→</span>
                        <span
                          :class="
                            difference.applied ? ':uno: text-blue-700' : ':uno: text-gray-700'
                          "
                        >
                          {{
                            syncDifferenceValue(
                              difference.field,
                              difference.remoteValue,
                              selectedProviderType,
                            )
                          }}
                        </span>
                        <span
                          v-if="!difference.applied"
                          class=":uno: border border-amber-200 rounded bg-white px-1.5 py-0.5 text-[11px] text-amber-700"
                        >
                          本地保留
                        </span>
                      </div>
                    </div>
                  </div>
                </template>
              </VEntity>
            </VEntityContainer>
          </section>

          <section
            v-if="filteredMissingModels.length"
            class=":uno: overflow-hidden border border-amber-200 rounded-lg bg-amber-50/50"
          >
            <div
              class=":uno: flex items-center justify-between border-b border-amber-100 px-4 py-2.5"
            >
              <div>
                <div class=":uno: text-xs text-amber-900 font-medium">远端已消失</div>
                <div class=":uno: mt-0.5 text-xs text-amber-700">
                  供应商不再返回这些模型，本地模型不会被自动删除。
                </div>
              </div>
              <VTag>{{ filteredMissingModels.length }} 个</VTag>
            </div>
            <VEntityContainer>
              <VEntity v-for="model in filteredMissingModels" :key="model.modelName">
                <template #start>
                  <VEntityField
                    :title="model.displayName || model.modelId"
                    :description="`供应商模型 ID: ${model.modelId} · 本地模型 ID: ${model.modelName}`"
                  />
                </template>
                <template #end>
                  <div class=":uno: flex flex-wrap items-center justify-end gap-1.5">
                    <VTag>本地保留</VTag>
                    <VTag>{{ model.enabled ? '已启用' : '已禁用' }}</VTag>
                  </div>
                </template>
              </VEntity>
            </VEntityContainer>
          </section>
        </div>
      </div>
    </div>
    <template #footer>
      <div class=":uno: w-full flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div class=":uno: text-sm text-gray-500">
          新增 {{ selectedNewModels.length }} · 待更新 {{ selectedUpdateModels.length }} · 可启用
          {{ selectedEnableModels.length }} · 可禁用 {{ selectedDisableModels.length }}
          <span v-if="selectedModelsNeedAdapter"> · 请为新增模型选择调用接口</span>
        </div>
        <div class=":uno: flex flex-wrap items-center gap-2 sm:justify-end">
          <VButton
            type="secondary"
            @click="applySync"
            :loading="isSyncing"
            :disabled="
              selectedNewModels.length + selectedUpdateModels.length === 0 ||
              selectedModelsNeedAdapter ||
              isSyncing ||
              isRefreshingCatalog
            "
          >
            应用同步（{{ selectedNewModels.length + selectedUpdateModels.length }}）
          </VButton>
          <VButton
            type="secondary"
            @click="changeEnabled(true)"
            :disabled="selectedEnableModels.length === 0 || isSyncing || isRefreshingCatalog"
          >
            启用 {{ selectedEnableModels.length }}
          </VButton>
          <VButton
            type="secondary"
            @click="changeEnabled(false)"
            :disabled="selectedDisableModels.length === 0 || isSyncing || isRefreshingCatalog"
          >
            禁用 {{ selectedDisableModels.length }}
          </VButton>
          <VButton @click="modal?.close()">关闭</VButton>
        </div>
      </div>
    </template>
  </VModal>
</template>
