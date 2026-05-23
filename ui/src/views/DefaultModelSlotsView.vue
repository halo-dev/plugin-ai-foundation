<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import type { DefaultModelSlots, ModelOption } from '@/api/generated'
import { ModelOptionModelTypeEnum } from '@/api/generated'
import {
  QK_DEFAULT_MODEL_SLOTS,
  useDefaultModelSlotsFetch,
} from '@/composables/use-default-model-slots-fetch'
import { QK_MODEL_OPTIONS, useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { modelTypeLabel } from '@/types'
import {
  groupModelOptionsByProvider,
  modelOptionLabel,
  type ModelOptionGroup,
} from '@/utils/model-options'
import { Toast, VButton, VCard, VEmpty, VLoading, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, shallowRef, watch } from 'vue'

type SlotKey = keyof DefaultModelSlots
type SlotModelType = NonNullable<ModelOption['modelType']>

const slotDefinitions: Array<{
  key: SlotKey
  label: string
  modelType: SlotModelType
}> = [
  {
    key: 'languageModelName',
    label: '默认语言模型',
    modelType: ModelOptionModelTypeEnum.Language,
  },
  {
    key: 'embeddingModelName',
    label: '默认 Embedding 模型',
    modelType: ModelOptionModelTypeEnum.Embedding,
  },
  {
    key: 'rerankModelName',
    label: '默认 Rerank 模型',
    modelType: ModelOptionModelTypeEnum.Rerank,
  },
  {
    key: 'imageGenerationModelName',
    label: '默认图像生成模型',
    modelType: ModelOptionModelTypeEnum.ImageGeneration,
  },
]

const queryClient = useQueryClient()
const availableOnly = shallowRef(true)
const { data: modelOptions, isLoading: modelOptionsLoading } = useModelOptionsFetch({
  available: availableOnly,
})
const { data: slots, isLoading: slotsLoading } = useDefaultModelSlotsFetch()

const formState = reactive<DefaultModelSlots>({})

watch(
  slots,
  (value) => {
    Object.assign(formState, {
      languageModelName: value?.languageModelName || '',
      embeddingModelName: value?.embeddingModelName || '',
      rerankModelName: value?.rerankModelName || '',
      imageGenerationModelName: value?.imageGenerationModelName || '',
    })
  },
  { immediate: true },
)

const isLoading = computed(() => modelOptionsLoading.value || slotsLoading.value)

function modelOptionsForType(modelType: SlotModelType) {
  return (modelOptions.value || []).filter((model) => model.modelType === modelType)
}

const modelOptionGroupsByType = computed(() => {
  return slotDefinitions.reduce(
    (groups, slot) => {
      groups[slot.modelType] = groupModelOptionsByProvider(modelOptionsForType(slot.modelType))
      return groups
    },
    {} as Record<SlotModelType, ModelOptionGroup[]>,
  )
})

function modelOptionGroupsForType(modelType: SlotModelType) {
  return modelOptionGroupsByType.value[modelType] || []
}

function normalizeSlots(): DefaultModelSlots {
  return {
    languageModelName: formState.languageModelName || undefined,
    embeddingModelName: formState.embeddingModelName || undefined,
    rerankModelName: formState.rerankModelName || undefined,
    imageGenerationModelName: formState.imageGenerationModelName || undefined,
  }
}

const updateMutation = useMutation({
  mutationFn: async () => {
    return await aiConsoleApiClient.defaultModelSlot.updateDefaultModelSlots({
      defaultModelSlots: normalizeSlots(),
    })
  },
  onSuccess: () => {
    Toast.success('默认模型已更新')
    queryClient.invalidateQueries({ queryKey: [QK_DEFAULT_MODEL_SLOTS] })
    queryClient.invalidateQueries({ queryKey: [QK_MODEL_OPTIONS] })
  },
  onError: (error) => {
    Toast.error('默认模型更新失败: ' + (error as Error).message)
  },
})
</script>

<template>
  <div class=":uno: p-2">
    <VCard title="默认模型" :body-class="['!p-0']">
      <VLoading v-if="isLoading" />
      <div v-else class=":uno: divide-y divide-gray-100">
        <div
          v-for="slot in slotDefinitions"
          :key="slot.key"
          class=":uno: grid grid-cols-1 gap-3 px-4 py-3 sm:grid-cols-[12rem_1fr]"
        >
          <div class=":uno: min-w-0">
            <div class=":uno: text-sm text-gray-900 font-medium">{{ slot.label }}</div>
            <div class=":uno: text-xs text-gray-500">{{ modelTypeLabel(slot.modelType) }}</div>
          </div>
          <div class=":uno: min-w-0">
            <select
              v-model="formState[slot.key]"
              class=":uno: h-9 w-full border border-gray-200 rounded bg-white px-2 text-sm"
            >
              <option value="">不配置</option>
              <optgroup
                v-for="group in modelOptionGroupsForType(slot.modelType)"
                :key="group.key"
                :label="group.label"
              >
                <option v-for="model in group.models" :key="model.name" :value="model.name">
                  {{ modelOptionLabel(model) }}
                </option>
              </optgroup>
            </select>
            <VEmpty
              v-if="modelOptionGroupsForType(slot.modelType).length === 0"
              class=":uno: mt-2"
              title="暂无可选模型"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <VSpace>
          <VButton
            type="secondary"
            :loading="updateMutation.isPending.value"
            @click="updateMutation.mutate()"
          >
            保存
          </VButton>
        </VSpace>
      </template>
    </VCard>
  </div>
</template>
