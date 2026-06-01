<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import ModelBadgeGroup from '@/components/ModelBadgeGroup.vue'
import { QK_MODELS } from '@/composables/use-models-fetch'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import { findProviderTypeForModel } from '@/utils/model'
import { isEnabledTestableModel } from '@/utils/model-test-workbench'
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
import { useClipboard } from '@vueuse/core'
import { computed, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import ModelEditingModal from './ModelEditingModal.vue'
import ModelEnableSwitch from './ModelEnableSwitch.vue'

const props = defineProps<{
  model: AiModel
}>()

const queryClient = useQueryClient()
const router = useRouter()
const { copy, isSupported } = useClipboard()

const editingModalVisible = shallowRef(false)

function openWorkbench() {
  void router.push({
    name: AI_FOUNDATION_ROUTE_NAMES.TEST,
    query: {
      model: props.model.metadata.name,
    },
  })
}

async function handleCopyModelId() {
  const modelId = props.model.metadata.name
  if (!modelId) {
    Toast.error('内部模型 ID 为空')
    return
  }

  if (!isSupported.value) {
    Toast.error('当前浏览器不支持复制')
    return
  }

  try {
    await copy(modelId)
    Toast.success('内部模型 ID 已复制')
  } catch (error) {
    Toast.error('内部模型 ID 复制失败: ' + (error as Error).message)
  }
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

const { data: providerTypes } = useProviderTypesFetch()
const { data: providers } = useProvidersFetch()

const providerType = computed(() => {
  return findProviderTypeForModel(props.model, providers.value, providerTypes.value)
})

const canTest = computed(() => isEnabledTestableModel(props.model))
const modelDescription = computed(() => {
  return `供应商模型 ID: ${props.model.spec.modelId} · 内部模型 ID: ${props.model.metadata.name}`
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
      <VEntityField :title="model.spec.displayName" :description="modelDescription">
        <template #extra>
          <VTag>{{ providerType?.displayName }}</VTag>
        </template>
      </VEntityField>
    </template>
    <template #end>
      <div class=":uno: flex flex-wrap items-center justify-end gap-2">
        <ModelBadgeGroup :model="model" />
        <VStatusDot v-if="model.metadata.deletionTimestamp" animate state="warning" text="删除中" />
        <ModelEnableSwitch :model="model" />
      </div>
    </template>
    <template #dropdownItems>
      <VDropdownItem @click="editingModalVisible = true">编辑</VDropdownItem>
      <VDropdownItem v-if="canTest" @click="openWorkbench">测试</VDropdownItem>
      <VDropdownItem @click="handleCopyModelId">复制内部模型 ID</VDropdownItem>
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
