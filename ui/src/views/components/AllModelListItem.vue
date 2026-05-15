<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import {
  Dialog,
  Toast,
  VAvatar,
  VDropdownDivider,
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import ModelEditingModal from './ModelEditingModal.vue'
import TestChatModal from './TestChatModal.vue'

const props = defineProps<{
  model: AiModel
}>()

const queryClient = useQueryClient()

const editingModalVisible = ref(false)
const testChatModalVisible = ref(false)
function handleDelete() {
  Dialog.warning({
    title: '删除模型',
    description: '确定要删除该模型吗？删除后调用方将无法使用该模型',
    confirmType: 'danger',
    onConfirm: async () => {
      await aiConsoleApiClient.model.deleteModel({
        name: props.model.metadata.name,
      })
      Toast.success('模型删除成功')
      queryClient.invalidateQueries({ queryKey: [QK_MODELS, props.model.spec.providerName] })
    },
  })
}

const { data: providerTypes } = useProviderTypesFetch()

const providerType = computed(() => {
  // TODO: 优化匹配 Provider Type 的方式
  return providerTypes.value?.find(
    (t) => t.providerType === props.model.spec.providerName.split('-')[0],
  )
})
</script>
<template>
  <VEntity>
    <template #start>
      <VEntityField>
        <template #description>
          <VAvatar :src="providerType?.iconUrl" />
        </template>
      </VEntityField>
      <VEntityField :title="model.spec.displayName" :description="model.spec.modelId">
        <template #extra>
          <VTag>{{ providerType?.displayName }}</VTag>
        </template>
      </VEntityField>
    </template>
    <template #end>
      <div class=":uno: flex items-center gap-1.5">
        <span
          v-if="!model.spec.enabled"
          class=":uno: rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500"
        >
          已禁用
        </span>
        <span
          v-for="cap in model.spec.capabilities"
          :key="cap"
          class=":uno: rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-600"
        >
          {{ cap }}
        </span>
        <VStatusDot v-if="model.metadata.deletionTimestamp" animate state="warning" text="删除中" />
      </div>
    </template>
    <template #dropdownItems>
      <VDropdownItem @click="editingModalVisible = true">编辑</VDropdownItem>
      <VDropdownItem @click="testChatModalVisible = true">测试</VDropdownItem>
      <VDropdownDivider />
      <VDropdownItem type="danger" @click="handleDelete">删除</VDropdownItem>
    </template>
  </VEntity>

  <ModelEditingModal
    v-if="editingModalVisible"
    :model="model"
    @close="editingModalVisible = false"
  />

  <TestChatModal
    v-if="testChatModalVisible"
    :model-name="model.metadata.name"
    :model-display-name="model.spec.displayName"
    @close="testChatModalVisible = false"
  />
</template>
