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
  defaultModelTypeForProviderType,
  filterModelFeaturesForProviderType,
  modelFeatureOptionsForProviderType,
  modelTypeOptionsForProviderType,
} from '@/utils/model'
import {
  discoveryConfidenceLabel,
  discoverySourceLabel,
  modelFeatureLabel,
  modelTypeLabel,
} from '@/types'
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
import { computed, onMounted, ref, toRef } from 'vue'

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

const keyword = ref('')

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
const profileOverrides = ref<
  Record<
    string,
    {
      modelType: AiModel['spec']['modelType']
      features: NonNullable<AiModel['spec']['features']>
    }
  >
>({})

const modelTypeOptions = computed(() => modelTypeOptionsForProviderType(selectedProviderType.value))

const featureOptions = computed(() => modelFeatureOptionsForProviderType(selectedProviderType.value))

function featuresEqual(
  a: NonNullable<AiModel['spec']['features']>,
  b: NonNullable<AiModel['spec']['features']>,
) {
  return a.length === b.length && a.every((item, index) => item === b[index])
}

function profileFor(model: DiscoveredModel) {
  const existing = profileOverrides.value[model.modelId]

  const fallback = {
    modelType: defaultModelTypeForProviderType(
      selectedProviderType.value,
      existing?.modelType || model.modelType,
    ),
    features: filterModelFeaturesForProviderType(
      selectedProviderType.value,
      existing?.features || model.features || [],
    ),
  }

  if (
    existing &&
    existing.modelType === fallback.modelType &&
    featuresEqual(existing.features, fallback.features)
  ) {
    return existing
  }

  profileOverrides.value[model.modelId] = fallback
  return fallback
}

function setModelType(model: DiscoveredModel, event: Event) {
  profileFor(model).modelType = defaultModelTypeForProviderType(
    selectedProviderType.value,
    (event.target as HTMLSelectElement).value,
  )
}

function toggleFeature(model: DiscoveredModel, feature: AiModelSpecFeaturesEnum, event: Event) {
  if (!featureOptions.value.some((item) => item.value === feature)) {
    return
  }
  const profile = profileFor(model)
  const checked = (event.target as HTMLInputElement).checked
  if (checked && !profile.features.includes(feature)) {
    profile.features = [...profile.features, feature]
  }
  if (!checked) {
    profile.features = profile.features.filter((item) => item !== feature)
  }
  profileOverrides.value[model.modelId] = profile
}

function toggleSelection(model: DiscoveredModel) {
  if (selectedModels.value.has(model.modelId)) {
    selectedModels.value.delete(model.modelId)
  } else {
    selectedModels.value.add(model.modelId)
  }
}

async function handleImport() {
  const data = models.value?.models.filter((model) => selectedModels.value.has(model.modelId))

  if (!data || data.length === 0) return

  const results = await Promise.allSettled(
    data.map(async (model) => {
      const newModel = createModelFromDiscovered(providerName.value, model, profileFor(model))

      await aiConsoleApiClient.model.createModel({
        aiModel: newModel,
      })
      return model.modelId
    }),
  )

  const succeeded = results.filter((r) => r.status === 'fulfilled').length
  const failed = results.filter((r) => r.status === 'rejected')

  if (failed.length > 0) {
    const failedIds = failed
      .map(
        (r, i) =>
          `${data[i].modelId}: ${(r as PromiseRejectedResult).reason?.message || '未知错误'}`,
      )
      .join('\n')
    Toast.warning(`导入完成：成功 ${succeeded} 个，失败 ${failed.length} 个\n${failedIds}`)
  } else {
    Toast.success(`成功导入 ${succeeded} 个模型`)
  }

  modal.value?.close()

  if (succeeded > 0) {
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
  }
}

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
    <div>
      <div class=":uno: border-b border-gray-100 p-4">
        <SearchInput
          id="model-discovery-search-input"
          sync
          v-model="keyword"
          placeholder="搜索模型名称或 ID..."
        />
      </div>
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
            <VEntityField :title="model.displayName" :description="model.modelId">
              <template #extra>
                <span class=":uno: rounded bg-indigo-50 px-1.5 py-0.5 text-xs text-indigo-600">
                  {{ modelTypeLabel(profileFor(model).modelType) }}
                </span>
                <span
                  v-for="feature in profileFor(model).features"
                  :key="feature"
                  class=":uno: ml-1 rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-600"
                >
                  {{ modelFeatureLabel(feature) }}
                </span>
                <span class=":uno: ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">
                  {{ discoverySourceLabel(model.source) }} / {{ discoveryConfidenceLabel(model.confidence) }}
                </span>
              </template>
            </VEntityField>
          </template>
          <template #end>
            <div class=":uno: max-w-xl flex flex-wrap items-center justify-end gap-2" @click.stop>
              <select
                :value="profileFor(model).modelType"
                class=":uno: h-8 border border-gray-200 rounded bg-white px-2 text-sm"
                @change="setModelType(model, $event)"
              >
                <option v-for="item in modelTypeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <label
                v-for="item in featureOptions"
                :key="item.value"
                class=":uno: inline-flex items-center gap-1 text-xs text-gray-600"
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
    <template #footer>
      <VSpace>
        <VButton type="secondary" @click="handleImport" :disabled="selectedModels.size === 0">
          {{ selectedModels.size > 0 ? `导入 ${selectedModels.size} 个模型` : '导入' }}
        </VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
