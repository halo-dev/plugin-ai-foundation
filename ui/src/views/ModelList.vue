<script setup lang="ts">
import type { AiModel } from '@/api/generated'
import { useDeleteModel } from '@/composables/useModels'
import { CAPABILITY_OPTIONS } from '@/types'
import { Dialog, VButton, VCard, VEmpty, VModal, VTag } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import RiChat1Line from '~icons/ri/chat-1-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiSearchLine from '~icons/ri/search-line'
import ModelForm from './ModelForm.vue'
import TestChatModal from './TestChatModal.vue'

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
  <div>
    <div class=":uno: mb-4 flex flex-wrap gap-3">
      <div class=":uno: relative min-w-[200px] flex-1">
        <RiSearchLine class=":uno: absolute left-2.5 top-1/2 h-4 w-4 text-gray-400 -translate-y-1/2" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索模型..."
          class=":uno: w-full border border-gray-200 rounded-md px-3 py-2 pl-8 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
        />
      </div>
      <select v-model="capabilityFilter" class=":uno: min-w-[120px] border border-gray-200 rounded-md bg-white px-3 py-2 text-sm">
        <option value="">全部能力</option>
        <option v-for="cap in CAPABILITY_OPTIONS" :key="cap.value" :value="cap.value">
          {{ cap.label }}
        </option>
      </select>
    </div>

    <div v-if="Object.keys(groupedModels).length === 0" class=":uno: py-8">
      <VEmpty title="暂无模型" />
    </div>

    <div v-else class=":uno: flex flex-col gap-3">
      <div v-for="(models, group) in groupedModels" :key="group">
        <div class=":uno: flex cursor-pointer items-center gap-2 rounded px-1 py-2 transition-colors duration-200 hover:bg-gray-100" @click="toggleGroup(group)">
          <span class=":uno: w-4 text-center text-[10px] text-gray-500">{{ isExpanded(group) ? '▼' : '▶' }}</span>
          <span class=":uno: text-sm font-medium">{{ group }}</span>
          <span class=":uno: text-xs text-gray-400">({{ models.length }})</span>
        </div>

        <div v-show="isExpanded(group)" class=":uno: mt-1 flex flex-col gap-2 pl-6">
          <VCard v-for="model in models" :key="model.metadata.name">
            <div class=":uno: mb-2 flex items-center justify-between">
              <div class=":uno: flex flex-wrap items-center gap-2">
                <span class=":uno: text-sm font-medium">{{ model.spec.displayName }}</span>
                <span class=":uno: text-xs text-gray-400 font-mono">{{ model.spec.modelId }}</span>
                <VTag v-if="!model.spec.enabled" size="sm" type="warning">已禁用</VTag>
              </div>
              <div class=":uno: flex gap-1">
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
            <div v-if="model.spec.capabilities?.length" class=":uno: flex flex-wrap gap-1.5">
              <VTag v-for="cap in model.spec.capabilities" :key="cap" size="sm" type="primary">
                {{ capabilityLabel(cap) }}
              </VTag>
            </div>
            <div v-if="model.spec.endpointType" class=":uno: mt-1.5">
              <span class=":uno: text-xs text-gray-500">{{ model.spec.endpointType }}</span>
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
