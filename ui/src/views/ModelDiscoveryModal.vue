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
    // eslint-disable-next-line no-undef
    Toast.success(`成功添加 ${toAdd.length} 个模型`)
    queryClient.invalidateQueries({ queryKey: ['ai-models'] })
    queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider', props.providerName] })
    emit('close')
  } catch (e) {
    // eslint-disable-next-line no-undef
    Toast.error('添加模型失败: ' + (e as Error).message)
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <div class="discovery-modal">
    <div class="discovery-header">
      <h3 class="text-base font-semibold">从供应商获取模型</h3>
      <VButton type="secondary" size="sm" @click="emit('close')">关闭</VButton>
    </div>

    <VLoading v-if="isLoading" />

    <div v-else-if="availableModels.length === 0" class="discovery-empty">
      <VEmpty title="未获取到模型列表" />
    </div>

    <div v-else class="discovery-content">
      <div class="discovery-defaults">
        <div class="default-field">
          <label>默认分组</label>
          <input v-model="defaultGroup" type="text" placeholder="可选" />
        </div>
      </div>

      <div class="discovery-actions">
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

      <div class="discovery-list">
        <VCard
          v-for="model in availableModels"
          :key="model.modelId"
          :class="['discovery-item', { 'discovery-item--selected': isSelected(model) }]"
          @click="toggleSelection(model)"
        >
          <div class="discovery-item__check">
            <div v-if="isSelected(model)" class="check-icon">
              <RiCheckLine />
            </div>
            <div v-else class="check-placeholder"></div>
          </div>
          <div class="discovery-item__info">
            <span class="discovery-item__name">{{ model.displayName || model.modelId }}</span>
            <span class="discovery-item__id">{{ model.modelId }}</span>
          </div>
          <div class="discovery-item__capabilities">
            <span
              v-for="cap in model.capabilities"
              :key="cap"
              class="capability-tag"
              :class="`capability-tag--${cap}`"
            >
              {{ capabilityLabel(cap) }}
            </span>
          </div>
        </VCard>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.discovery-modal {
  padding: 8px 0;
}

.discovery-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.discovery-empty {
  padding: 32px 0;
}

.discovery-defaults {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  .default-field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
    min-width: 150px;

    label {
      font-size: 12px;
      color: #6b7280;
    }

    input,
    select {
      padding: 6px 10px;
      border: 1px solid #e5e7eb;
      border-radius: 6px;
      font-size: 14px;
    }
  }
}

.discovery-actions {
  margin-bottom: 12px;
}

.discovery-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}

.discovery-item {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s;
  padding: 10px 12px;

  &:hover {
    border-color: #3b82f6;
  }

  &--selected {
    background: #eff6ff;
    border-color: #3b82f6;
  }

  &__check {
    flex-shrink: 0;
  }

  .check-icon {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #3b82f6;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
  }

  .check-placeholder {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid #d1d5db;
  }

  &__info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-size: 14px;
    font-weight: 500;
  }

  &__id {
    font-size: 12px;
    color: #9ca3af;
    font-family: monospace;
  }

  &__capabilities {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }
}

.capability-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;

  &--chat {
    background: #dbeafe;
    color: #1d4ed8;
  }

  &--embedding {
    background: #fce7f3;
    color: #be185d;
  }
}
</style>
