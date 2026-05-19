<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import { AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel, DefaultModelSlots } from '@/api/generated'
import {
  QK_DEFAULT_MODEL_SLOTS,
  useDefaultModelSlotsFetch,
} from '@/composables/use-default-model-slots-fetch'
import { QK_MODELS, useModelsFetch } from '@/composables/use-models-fetch'
import { modelTypeLabel } from '@/types'
import { Toast, VButton, VCard, VEmpty, VLoading, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, watch } from 'vue'

type SlotKey = keyof DefaultModelSlots

const slotDefinitions: Array<{
  key: SlotKey
  label: string
  modelType: AiModel['spec']['modelType']
}> = [
  {
    key: 'languageModelName',
    label: '默认语言模型',
    modelType: AiModelSpecModelTypeEnum.Language,
  },
  {
    key: 'embeddingModelName',
    label: '默认 Embedding 模型',
    modelType: AiModelSpecModelTypeEnum.Embedding,
  },
  {
    key: 'rerankModelName',
    label: '默认 Rerank 模型',
    modelType: AiModelSpecModelTypeEnum.Rerank,
  },
  {
    key: 'imageGenerationModelName',
    label: '默认图像生成模型',
    modelType: AiModelSpecModelTypeEnum.ImageGeneration,
  },
]

const queryClient = useQueryClient()
const { data: models, isLoading: modelsLoading } = useModelsFetch({})
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

const isLoading = computed(() => modelsLoading.value || slotsLoading.value)

function modelsForType(modelType: AiModel['spec']['modelType']) {
  return (models.value || []).filter(
    (model) => model.spec.enabled !== false && model.spec.modelType === modelType,
  )
}

function modelLabel(model: AiModel) {
  return `${model.spec.displayName || model.spec.modelId} / ${model.spec.providerName}`
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
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
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
            <div class=":uno: text-sm font-medium text-gray-900">{{ slot.label }}</div>
            <div class=":uno: text-xs text-gray-500">{{ modelTypeLabel(slot.modelType) }}</div>
          </div>
          <div class=":uno: min-w-0">
            <select
              v-model="formState[slot.key]"
              class=":uno: h-9 w-full rounded border border-gray-200 bg-white px-2 text-sm"
            >
              <option value="">不配置</option>
              <option
                v-for="model in modelsForType(slot.modelType)"
                :key="model.metadata.name"
                :value="model.metadata.name"
              >
                {{ modelLabel(model) }}
              </option>
            </select>
            <VEmpty
              v-if="modelsForType(slot.modelType).length === 0"
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
