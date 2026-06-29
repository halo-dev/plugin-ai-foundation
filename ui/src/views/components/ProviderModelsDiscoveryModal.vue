<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel, AiModelSpecFeaturesEnum, AiProvider } from '@/api/generated'
import {
  QK_MODELS,
  useDiscoverModelsFetch,
  type DiscoveredModel,
} from '@/composables/use-models-fetch'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { setFocus } from '@/utils/focus'
import {
  createModelFromDiscovered,
  discoveredModelProfileForProviderType,
  groupDiscoveredModels,
  modelFeatureOptionsForProviderType,
  modelImportFailureMessage,
  modelTypeOptionsForProviderType,
  summarizeModelImportResults,
  syncDiscoveredModelProfiles,
  type DiscoveredModelProfiles,
} from '@/utils/model'
import {
  capabilityDomainSource,
  capabilitySourceLabel,
  capabilitySummaryLabels,
} from '@/utils/capabilities'
import {
  Toast,
  VButton,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VSpace,
  VTag,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useFuse } from '@vueuse/integrations/useFuse'
import { computed, onMounted, ref, shallowRef, toRef, watch } from 'vue'
import RiCheckLine from '~icons/ri/check-line'

const props = defineProps<{
  provider: AiProvider
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = ref<InstanceType<typeof VModal> | null>(null)

const providerName = computed(() => props.provider.metadata.name || '')

const { data: models, isLoading } = useDiscoverModelsFetch(providerName)
const selectedProviderType = useProviderType(toRef(props, 'provider'))

const keyword = shallowRef('')

const allModels = computed(() => models.value?.models || [])

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

const filteredModelGroups = computed(() => groupDiscoveredModels(filteredModels.value))

const selectedModels = ref<Set<string>>(new Set())
const profileOverrides = ref<DiscoveredModelProfiles>({})
const isImporting = shallowRef(false)
const selectedModelCount = computed(() => selectedModels.value.size)
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

const modelTypeOptions = computed(() => modelTypeOptionsForProviderType(selectedProviderType.value))

const featureOptions = computed(() =>
  modelFeatureOptionsForProviderType(selectedProviderType.value),
)

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

function setModelType(model: DiscoveredModel, event: Event) {
  const current = profileFor(model)
  const next = discoveredModelProfileForProviderType(selectedProviderType.value, model, {
    ...current,
    modelType: (event.target as HTMLSelectElement).value as AiModel['spec']['modelType'],
  })
  profileOverrides.value = {
    ...profileOverrides.value,
    [model.modelId]: next,
  }
}

function isSelected(model: DiscoveredModel) {
  return selectedModels.value.has(model.modelId)
}

function isFeatureEnabled(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum) {
  return profileFor(model).features.includes(feature)
}

function toggleFeature(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum) {
  if (!featureOptions.value.some((item) => item.value === feature)) {
    return
  }
  const profile = profileFor(model)
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

function capabilitySourceLabels(model: DiscoveredModel) {
  return [
    ['language', '语言能力'],
    ['imageGeneration', '图像能力'],
  ]
    .map(([domain, label]) => {
      const source = capabilityDomainSource(
        model.capabilities,
        model.capabilitySources,
        domain as 'language' | 'imageGeneration',
      )
      return source ? `${label}: ${capabilitySourceLabel(source)}` : ''
    })
    .filter(Boolean)
}

function toggleSelection(model: DiscoveredModel) {
  if (selectedModels.value.has(model.modelId)) {
    selectedModels.value.delete(model.modelId)
  } else {
    selectedModels.value.add(model.modelId)
  }
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

async function handleImport() {
  if (isImporting.value) return

  const data = allModels.value.filter((model) => selectedModels.value.has(model.modelId))

  if (data.length === 0) return

  isImporting.value = true
  try {
    const results = await Promise.allSettled(
      data.map(async (model) => {
        const newModel = createModelFromDiscovered(providerName.value, model, profileFor(model))

        await aiConsoleApiClient.model.createModel({
          aiModel: newModel,
        })
        return model.modelId
      }),
    )

    const { succeeded, failed } = summarizeModelImportResults(data, results)

    if (failed.length > 0) {
      const failedIds = failed.map(modelImportFailureMessage).join('\n')
      Toast.warning(`导入完成：成功 ${succeeded} 个，失败 ${failed.length} 个\n${failedIds}`)
    } else {
      Toast.success(`成功导入 ${succeeded} 个模型`)
    }

    modal.value?.close()

    if (succeeded > 0) {
      queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
    }
  } finally {
    isImporting.value = false
  }
}

watch(
  [allModels, selectedProviderType],
  ([items, providerType]) => {
    const nextProfiles = syncDiscoveredModelProfiles(items, providerType, profileOverrides.value)
    const isUnchanged =
      Object.keys(nextProfiles).length === Object.keys(profileOverrides.value).length &&
      Object.entries(nextProfiles).every(([modelId, profile]) => {
        const existing = profileOverrides.value[modelId]
        return (
          existing &&
          existing.modelType === profile.modelType &&
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
      <div class=":uno: min-h-0 flex-1 overflow-y-auto">
        <VLoading v-if="isLoading" />
        <VEmpty v-else-if="allModels.length === 0" title="无数据" message="无法获取到模型列表" />
        <VEmpty
          v-else-if="filteredModels.length === 0"
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
                    class=":uno: max-w-[34rem] w-full flex flex-col items-stretch gap-2 sm:items-end"
                    @click.stop
                  >
                    <div class=":uno: flex flex-wrap items-center justify-end gap-2">
                      <select
                        :value="profileFor(model).modelType"
                        class=":uno: h-8 min-w-30 rounded-md bg-white text-xs text-gray-700 outline-none transition !border !border-gray-200 !border-solid !px-2 !py-0 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500"
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
                    </div>
                    <div class=":uno: flex flex-wrap justify-end gap-1.5">
                      <button
                        v-for="item in featureOptions"
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
                  </div>
                </template>
              </VEntity>
            </VEntityContainer>
          </section>
        </div>
      </div>
    </div>
    <template #footer>
      <div class=":uno: w-full flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class=":uno: text-sm text-gray-500">已选择 {{ selectedModelCount }} 个模型</div>
        <VSpace>
          <VButton
            type="secondary"
            @click="handleImport"
            :loading="isImporting"
            :disabled="selectedModelCount === 0 || isImporting"
          >
            {{ selectedModelCount > 0 ? `导入 ${selectedModelCount} 个模型` : '导入' }}
          </VButton>
          <VButton @click="modal?.close()">关闭</VButton>
        </VSpace>
      </div>
    </template>
  </VModal>
</template>
