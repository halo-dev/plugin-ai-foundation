<script setup lang="ts">
import type { AiModel, ProviderTypeInfo } from '@/api/generated'
import type { DiscoveredModel } from '@/composables/useModels'
import { useCreateModel, useProviderModels } from '@/composables/useModels'
import { VButton, VCard, VEmpty, VLoading } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import RiCheckLine from '~icons/ri/check-line'
import RiDownloadCloudLine from '~icons/ri/download-cloud-line'

const props = defineProps<{
  providerName: string
  providerType: ProviderTypeInfo
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { data: discovered, isLoading } = useProviderModels(props.providerName)
const createModel = useCreateModel()
const queryClient = useQueryClient()

const selectedModels = ref<Set<string>>(new Set())
const defaultGroup = ref('')
const adding = ref(false)

const availableModels = computed(() => {
  return discovered.value?.models || []
})

function inferEndpointType(dm: DiscoveredModel): string {
  const endpointTypes = props.providerType.supportedEndpointTypes || []
  const caps = dm.capabilities || []
  if (caps.includes('embedding')) {
    const embeddingType = endpointTypes.find((t) => t.includes('embedding'))
    return embeddingType || endpointTypes[0] || 'openai-embedding'
  }
  const chatType = endpointTypes.find((t) => t.includes('chat'))
  return chatType || endpointTypes[0] || 'openai-chat'
}

function toggleSelection(model: DiscoveredModel) {
  if (selectedModels.value.has(model.modelId)) {
    selectedModels.value.delete(model.modelId)
  } else {
    selectedModels.value.add(model.modelId)
  }
}

function isSelected(model: DiscoveredModel) {
  return selectedModels.value.has(model.modelId)
}

function capabilityLabel(cap: string): string {
  switch (cap) {
    case 'chat':
      return '对话'
    case 'embedding':
      return 'Embedding'
    default:
      return cap
  }
}

async function batchAdd() {
  const toAdd = availableModels.value.filter((m) => selectedModels.value.has(m.modelId))
  if (toAdd.length === 0) return

  adding.value = true
  try {
    for (const dm of toAdd) {
      const generateName =
        `${props.providerName}-${dm.modelId.replace(/\//g, '-')}-`.toLocaleLowerCase()
      const newModel: AiModel = {
        apiVersion: 'aifoundation.halo.run/v1alpha1',
        kind: 'AiModel',
        metadata: {
          generateName,
          name: '',
        },
        spec: {
          providerName: props.providerName,
          modelId: dm.modelId,
          displayName: dm.displayName || dm.modelId,
          enabled: true,
          group: defaultGroup.value.trim() || undefined,
          endpointType: inferEndpointType(dm),
        },
      }
      await createModel.mutateAsync(newModel)
    }

    Toast.success(`成功添加 ${toAdd.length} 个模型`)
    queryClient.invalidateQueries({ queryKey: ['ai-models'] })
    queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider', props.providerName] })
    emit('close')
  } catch (e) {
    Toast.error('添加模型失败: ' + (e as Error).message)
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <div class=":uno: py-2">
    <div class=":uno: mb-4 flex items-center justify-between">
      <h3 class=":uno: text-base font-semibold">从供应商获取模型</h3>
      <VButton type="secondary" size="sm" @click="emit('close')">关闭</VButton>
    </div>

    <VLoading v-if="isLoading" />

    <div v-else-if="availableModels.length === 0" class=":uno: py-8">
      <VEmpty title="未获取到模型列表" />
    </div>

    <div v-else>
      <div class=":uno: mb-4 flex flex-wrap gap-3">
        <div class=":uno: min-w-[150px] flex flex-1 flex-col gap-1">
          <label class=":uno: text-xs text-gray-500">默认分组</label>
          <input v-model="defaultGroup" type="text" placeholder="可选" class=":uno: border border-gray-200 rounded-md px-2.5 py-1.5 text-sm" />
        </div>
      </div>

      <div class=":uno: mb-3">
        <VButton
          type="primary"
          size="sm"
          :loading="adding"
          :disabled="selectedModels.size === 0"
          @click="batchAdd"
        >
          <template #icon>
            <RiDownloadCloudLine />
          </template>
          批量添加 ({{ selectedModels.size }})
        </VButton>
      </div>

      <div class=":uno: max-h-[400px] flex flex-col gap-2 overflow-y-auto">
        <VCard
          v-for="model in availableModels"
          :key="model.modelId"
          :class="[':uno: flex items-center gap-2.5 cursor-pointer transition-all duration-200 py-2.5 px-3 hover:border-blue-500', { ':uno: bg-blue-50 border-blue-500': isSelected(model) }]"
          @click="toggleSelection(model)"
        >
          <div class=":uno: shrink-0">
            <div v-if="isSelected(model)" class=":uno: h-5 w-5 flex items-center justify-center rounded-full bg-blue-500 text-xs text-white">
              <RiCheckLine />
            </div>
            <div v-else class=":uno: h-5 w-5 border-2 border-gray-300 rounded-full"></div>
          </div>
          <div class=":uno: min-w-0 flex flex-1 flex-col gap-0.5">
            <span class=":uno: text-sm font-medium">{{ model.displayName || model.modelId }}</span>
            <span class=":uno: text-xs text-gray-400 font-mono">{{ model.modelId }}</span>
          </div>
          <div class=":uno: flex shrink-0 gap-1">
            <span
              v-for="cap in model.capabilities"
              :key="cap"
              class=":uno: inline-block rounded px-2 py-0.5 text-[11px] font-500"
              :class="cap === 'chat' ? ':uno: bg-blue-100 text-blue-700' : cap === 'embedding' ? ':uno: bg-pink-100 text-pink-700' : ':uno: bg-gray-100 text-gray-700'"
            >
              {{ capabilityLabel(cap) }}
            </span>
          </div>
        </VCard>
      </div>
    </div>
  </div>
</template>
