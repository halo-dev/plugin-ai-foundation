<script setup lang="ts">
import { ref, computed } from 'vue'
import { VButton, VCard, VTag, VEmpty, VModal } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useDeleteModel } from '@/composables/useModels'
import { CAPABILITY_OPTIONS } from '@/types'
import ModelForm from './ModelForm.vue'
import TestChatModal from './TestChatModal.vue'
import RiEditLine from '~icons/ri/edit-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiSearchLine from '~icons/ri/search-line'
import RiChat1Line from '~icons/ri/chat-1-line'
import type { AiModel } from '@/api/generated'

const props = defineProps<{
  models: AiModel[]
  providerName: string
}>()

const queryClient = useQueryClient()
const deleteModel = useDeleteModel()

const searchQuery = ref('')
const capabilityFilter = ref('')
const modelFormVisible = ref(false)
const editingModel = ref<AiModel | null>(null)
const testChatVisible = ref(false)
const testChatModel = ref<AiModel | null>(null)

const filteredModels = computed(() => {
  let result = props.models
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(
      (m) =>
        m.spec.displayName.toLowerCase().includes(q) ||
        m.spec.modelId.toLowerCase().includes(q) ||
        (m.spec.group && m.spec.group.toLowerCase().includes(q)),
    )
  }
  if (capabilityFilter.value) {
    result = result.filter((m) => m.spec.capabilities?.includes(capabilityFilter.value))
  }
  return result
})

const groupedModels = computed(() => {
  const groups: Record<string, AiModel[]> = {}
  const defaultGroup = '默认分组'
  for (const model of filteredModels.value) {
    const group = model.spec.group || defaultGroup
    if (!groups[group]) groups[group] = []
    groups[group].push(model)
  }
  return groups
})

const expandedGroups = ref<Record<string, boolean>>({})

function isExpanded(group: string) {
  return expandedGroups.value[group] !== false
}

function toggleGroup(group: string) {
  expandedGroups.value[group] = !isExpanded(group)
}

function onEdit(model: AiModel) {
  editingModel.value = model
  modelFormVisible.value = true
}

function onDelete(model: AiModel) {
  // eslint-disable-next-line no-undef
  Dialog.warning({
    title: '确认删除',
    description: `确定要删除模型 "${model.spec.displayName}" 吗？`,
    onConfirm: async () => {
      await deleteModel.mutateAsync(model.metadata.name)
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
      queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider', props.providerName] })
    },
  })
}

function onFormSaved() {
  modelFormVisible.value = false
  editingModel.value = null
  queryClient.invalidateQueries({ queryKey: ['ai-models'] })
  queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider', props.providerName] })
}

function capabilityLabel(value: string) {
  return CAPABILITY_OPTIONS.find((c) => c.value === value)?.label || value
}

function openTestChat(model: AiModel) {
  testChatModel.value = model
  testChatVisible.value = true
}
</script>

<template>
  <div class="model-list">
    <div class="model-list__filters">
      <div class="filter-search">
        <RiSearchLine class="search-icon" />
        <input v-model="searchQuery" type="text" placeholder="搜索模型..." class="filter-input" />
      </div>
      <select v-model="capabilityFilter" class="filter-select">
        <option value="">全部能力</option>
        <option v-for="cap in CAPABILITY_OPTIONS" :key="cap.value" :value="cap.value">
          {{ cap.label }}
        </option>
      </select>
    </div>

    <div v-if="Object.keys(groupedModels).length === 0" class="model-list__empty">
      <VEmpty title="暂无模型" />
    </div>

    <div v-else class="model-list__groups">
      <div v-for="(models, group) in groupedModels" :key="group" class="model-group">
        <div class="model-group__header" @click="toggleGroup(group)">
          <span class="model-group__toggle">{{ isExpanded(group) ? '▼' : '▶' }}</span>
          <span class="model-group__name">{{ group }}</span>
          <span class="model-group__count">({{ models.length }})</span>
        </div>

        <div v-show="isExpanded(group)" class="model-group__items">
          <VCard v-for="model in models" :key="model.metadata.name" class="model-card">
            <div class="model-card__header">
              <div class="model-card__info">
                <span class="model-card__name">{{ model.spec.displayName }}</span>
                <span class="model-card__id">{{ model.spec.modelId }}</span>
                <VTag v-if="!model.spec.enabled" size="sm" type="warning">已禁用</VTag>
              </div>
              <div class="model-card__actions">
                <VButton type="default" size="sm" @click="openTestChat(model)">
                  <RiChat1Line />
                </VButton>
                <VButton type="default" size="sm" @click="onEdit(model)">
                  <RiEditLine />
                </VButton>
                <VButton type="danger" size="sm" @click="onDelete(model)">
                  <RiDeleteBinLine />
                </VButton>
              </div>
            </div>
            <div v-if="model.spec.capabilities?.length" class="model-card__tags">
              <VTag v-for="cap in model.spec.capabilities" :key="cap" size="sm" type="primary">
                {{ capabilityLabel(cap) }}
              </VTag>
            </div>
            <div v-if="model.spec.endpointType" class="model-card__meta">
              <span class="text-xs text-gray-500">{{ model.spec.endpointType }}</span>
            </div>
          </VCard>
        </div>
      </div>
    </div>

    <VModal
      v-if="modelFormVisible"
      :title="editingModel ? '编辑模型' : '添加模型'"
      :width="600"
      @close="modelFormVisible = false"
    >
      <ModelForm
        :model="editingModel"
        :provider-name="providerName"
        @saved="onFormSaved"
        @cancel="modelFormVisible = false"
      />
    </VModal>

    <VModal
      v-if="testChatVisible && testChatModel"
      title="测试对话"
      :width="700"
      @close="testChatVisible = false"
    >
      <TestChatModal
        :model-name="testChatModel.metadata.name"
        :model-display-name="testChatModel.spec.displayName"
        @close="testChatVisible = false"
      />
    </VModal>
  </div>
</template>

<style lang="scss" scoped>
.model-list {
  &__filters {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
    flex-wrap: wrap;
  }

  &__empty {
    padding: 32px 0;
  }

  &__groups {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}

.filter-search {
  position: relative;
  flex: 1;
  min-width: 200px;

  .search-icon {
    position: absolute;
    left: 10px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: #9ca3af;
  }
}

.filter-input {
  width: 100%;
  padding: 8px 12px 8px 32px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  outline: none;

  &:focus {
    border-color: #3b82f6;
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
  }
}

.filter-select {
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  background: #fff;
  min-width: 120px;
}

.model-group {
  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 4px;
    cursor: pointer;
    border-radius: 4px;
    transition: background 0.2s;

    &:hover {
      background: #f3f4f6;
    }
  }

  &__toggle {
    font-size: 10px;
    color: #6b7280;
    width: 16px;
    text-align: center;
  }

  &__name {
    font-weight: 500;
    font-size: 14px;
  }

  &__count {
    font-size: 12px;
    color: #9ca3af;
  }

  &__items {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-left: 24px;
    margin-top: 4px;
  }
}

.model-card {
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  &__info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__name {
    font-weight: 500;
    font-size: 14px;
  }

  &__id {
    font-size: 12px;
    color: #9ca3af;
    font-family: monospace;
  }

  &__actions {
    display: flex;
    gap: 4px;
  }

  &__tags {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }

  &__meta {
    margin-top: 6px;
  }
}
</style>
