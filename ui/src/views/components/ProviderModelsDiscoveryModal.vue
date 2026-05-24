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
  modelFeatureOptionsForProviderType,
  modelImportFailureMessage,
  modelTypeOptionsForProviderType,
  summarizeModelImportResults,
  syncDiscoveredModelProfiles,
  type DiscoveredModelProfiles,
} from '@/utils/model'
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
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useFuse } from '@vueuse/integrations/useFuse'
import { computed, onMounted, ref, shallowRef, toRef, watch } from 'vue'

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

const selectedModels = ref<Set<string>>(new Set())
const profileOverrides = ref<DiscoveredModelProfiles>({})
const isImporting = shallowRef(false)
const selectedModelCount = computed(() => selectedModels.value.size)
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

function toggleFeature(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum, event: Event) {
  if (!featureOptions.value.some((item) => item.value === feature)) {
    return
  }
  const profile = profileFor(model)
  const checked = (event.target as HTMLInputElement).checked
  let features = profile.features
  if (checked && !profile.features.includes(feature)) {
    features = [...profile.features, feature]
  }
  if (!checked) {
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

function toggleSelection(model: DiscoveredModel) {
  if (selectedModels.value.has(model.modelId)) {
    selectedModels.value.delete(model.modelId)
  } else {
    selectedModels.value.add(model.modelId)
  }
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
    :width="800"
    mount-to-body
    :centered="false"
    :body-class="['!p-0']"
    @close="emit('close')"
  >
    <div class=":uno: max-h-[68vh] min-h-0 flex flex-col">
      <div class=":uno: flex-none border-b border-gray-100 bg-white p-4">
        <div class=":uno: mb-3 flex flex-col gap-1">
          <div class=":uno: text-sm text-gray-950 font-semibold">
            {{ selectedProviderType?.displayName || provider.spec.providerType }}
          </div>
          <div class=":uno: text-xs text-gray-500">
            {{ resultText }}，已选择 {{ selectedModelCount }} 个
          </div>
        </div>
        <SearchInput
          id="model-discovery-search-input"
          sync
          v-model="keyword"
          placeholder="搜索模型名称或 ID..."
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
        <VEntityContainer v-else>
          <VEntity
            @click="toggleSelection(model)"
            v-for="model in filteredModels"
            :key="model.modelId"
          >
            <template #checkbox>
              <input @click.stop type="checkbox" v-model="selectedModels" :value="model.modelId" />
            </template>
            <template #start>
              <VEntityField :title="model.displayName" :description="model.modelId" />
            </template>
            <template #end>
              <div class=":uno: max-w-xl flex flex-wrap items-center justify-end gap-2" @click.stop>
                <select
                  :value="profileFor(model).modelType"
                  class=":uno: h-8 border border-gray-200 rounded-md bg-white px-2 text-sm text-gray-700 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
                  @change="setModelType(model, $event)"
                >
                  <option v-for="item in modelTypeOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </option>
                </select>
                <label
                  v-for="item in featureOptions"
                  :key="item.value"
                  class=":uno: inline-flex items-center gap-1 border border-gray-200 rounded-md bg-white px-2 py-1 text-xs text-gray-600"
                >
                  <input
                    type="checkbox"
                    :checked="profileFor(model).features.includes(item.value)"
                    @change="toggleFeature(model, item.value, $event)"
                  />
                  {{ item.label }}
                </label>
              </div>
            </template>
          </VEntity>
        </VEntityContainer>
      </div>
    </div>
    <template #footer>
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
    </template>
  </VModal>
</template>
