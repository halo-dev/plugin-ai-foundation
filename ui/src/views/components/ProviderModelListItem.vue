<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import {
  Dialog,
  Toast,
  VDropdownDivider,
  VDropdownItem,
  VEntity,
  VEntityField,
  VStatusDot,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useRouteQuery } from '@vueuse/router'
import { computed, ref } from 'vue'
import ModelEditingModal from './ModelEditingModal.vue'
import { isEnabledChatModel } from '@/utils/model-test-workbench'

const props = defineProps<{
  model: AiModel
}>()

const queryClient = useQueryClient()

const editingModalVisible = ref(false)
const tab = useRouteQuery<string | undefined>('tab')
const testModel = useRouteQuery<string | undefined>('model')
const canTest = computed(() => isEnabledChatModel(props.model))

function openWorkbench() {
  tab.value = 'test'
  testModel.value = props.model.metadata.name
}

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
</script>
<template>
  <VEntity>
    <template #start>
      <VEntityField :title="model.spec.displayName" :description="model.spec.modelId" />
    </template>
    <template #end>
      <div class=":uno: flex flex-wrap items-center justify-end gap-1.5">
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
      <VDropdownItem v-if="canTest" @click="openWorkbench">测试</VDropdownItem>
      <VDropdownDivider />
      <VDropdownItem type="danger" @click="handleDelete">删除</VDropdownItem>
    </template>
  </VEntity>

  <ModelEditingModal
    v-if="editingModalVisible"
    :model="model"
    @close="editingModalVisible = false"
  />
</template>
