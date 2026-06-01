<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import type { DefaultModelSlots, ModelOption } from '@/api/generated'
import { ModelOptionModelTypeEnum } from '@/api/generated'
import {
  QK_DEFAULT_MODEL_SLOTS,
  useDefaultModelSlotsFetch,
} from '@/composables/use-default-model-slots-fetch'
import { QK_MODEL_OPTIONS, useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import AiModelSelector from '@/formkit/AiModelSelector.vue'
import { modelTypeLabel } from '@/types'
import { Toast, VButton, VCard, VLoading, VSpace } from '@halo-dev/components'
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
const assignedSlotCount = computed(() => {
  return slotDefinitions.filter((slot) => !!formState[slot.key]).length
})
const modelOptionCount = computed(() => modelOptions.value?.length || 0)

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
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: w-full flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
          <div class=":uno: min-w-0 flex-1">
            <div class=":uno: text-sm text-gray-950 font-semibold">默认模型</div>
            <div class=":uno: text-xs text-gray-500">
              已配置 {{ assignedSlotCount }} / {{ slotDefinitions.length }} 个默认模型，可选模型
              {{ modelOptionCount }} 个
            </div>
          </div>
          <label class=":uno: inline-flex cursor-pointer items-center gap-2 text-sm text-gray-600">
            <input v-model="availableOnly" type="checkbox" class=":uno: border-gray-300 rounded" />
            仅显示可用模型
          </label>
        </div>
      </template>
      <VLoading v-if="isLoading" />
      <div v-else class=":uno: divide-y divide-gray-100">
        <div
          v-for="slot in slotDefinitions"
          :key="slot.key"
          class=":uno: grid grid-cols-1 gap-3 px-4 py-4 sm:grid-cols-[13rem_1fr]"
        >
          <div class=":uno: min-w-0">
            <div class=":uno: text-sm text-gray-950 font-medium">{{ slot.label }}</div>
            <div class=":uno: mt-1 text-xs text-gray-500">{{ modelTypeLabel(slot.modelType) }}</div>
          </div>
          <div class=":uno: min-w-0">
            <AiModelSelector
              v-model="formState[slot.key]"
              :model-type="slot.modelType"
              :available="availableOnly"
              placeholder="不配置"
              search-placeholder="搜索模型..."
              full-width
              class=":uno: !py-0"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <VSpace class=":uno: justify-end">
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
