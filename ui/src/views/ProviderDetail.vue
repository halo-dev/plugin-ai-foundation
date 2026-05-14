<script setup lang="ts">
import type { AiProvider } from '@/api/generated'
import { useModelsByProvider } from '@/composables/useModels'
import { useProviderTypes } from '@/composables/useProviderTypes'
import { useTestConnectivity } from '@/composables/useProviders'
import {
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VModal,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiDownloadCloudLine from '~icons/ri/download-cloud-line'
import RiEditLine from '~icons/ri/edit-line'
import RiTestTubeLine from '~icons/ri/test-tube-line'
import ModelDiscoveryModal from './ModelDiscoveryModal.vue'
import ModelForm from './ModelForm.vue'
import ModelList from './ModelList.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const emit = defineEmits<{
  (e: 'edit', provider: AiProvider): void
  (e: 'delete', provider: AiProvider): void
}>()

const testConnectivity = useTestConnectivity()
const { data: models } = useModelsByProvider(props.provider.metadata.name)
const { data: providerTypes } = useProviderTypes()
const queryClient = useQueryClient()

const modelFormVisible = ref(false)
const editingModel = ref(null)
const discoveryVisible = ref(false)

const providerTypeInfo = computed(() => {
  return providerTypes.value?.find((t) => t.providerType === props.provider.spec.providerType)
})

function providerTypeLabel(): string {
  return providerTypeInfo.value?.displayName || props.provider.spec.providerType
}

function onTest() {
  testConnectivity.mutate(props.provider.metadata.name)
}

function statusPhase(phase?: string) {
  switch (phase) {
    case 'OK':
      return 'success'
    case 'ERROR':
      return 'error'
    default:
      return 'default'
  }
}

function onModelFormSaved() {
  modelFormVisible.value = false
  editingModel.value = null
  queryClient.invalidateQueries({ queryKey: ['ai-models'] })
  queryClient.invalidateQueries({
    queryKey: ['ai-models', 'provider', props.provider.metadata.name],
  })
}
</script>

<template>
  <div>
    <VCard>
      <div class=":uno: mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class=":uno: flex flex-wrap items-center gap-2.5">
          <h2 class=":uno: text-lg font-semibold">{{ provider.spec.displayName }}</h2>
          <VTag size="sm">
            {{ providerTypeLabel() }}
          </VTag>
          <VStatusDot :state="statusPhase(provider.status?.phase)" />
          <span class=":uno: text-sm text-gray-500">{{ provider.status?.phase || 'UNKNOWN' }}</span>
          <VTag v-if="!provider.spec.enabled" size="sm" type="warning">已禁用</VTag>
        </div>
        <div class=":uno: flex flex-wrap gap-2">
          <VButton size="sm" :loading="testConnectivity.isPending.value" @click="onTest">
            <template #icon>
              <RiTestTubeLine />
            </template>
            测试连通性
          </VButton>
          <VButton type="secondary" size="sm" @click="emit('edit', provider)">
            <template #icon>
              <RiEditLine />
            </template>
            编辑
          </VButton>
          <VButton type="danger" size="sm" @click="emit('delete', provider)">
            <template #icon>
              <RiDeleteBinLine />
            </template>
            删除
          </VButton>
        </div>
      </div>

      <div v-if="testConnectivity.data.value" class=":uno: mb-3 flex items-center gap-2.5 rounded-md bg-slate-50 px-3 py-2.5">
        <VTag :type="(testConnectivity.data.value as any).phase === 'OK' ? 'success' : 'error'" size="sm">
          {{ (testConnectivity.data.value as any).phase === 'OK' ? '连通成功' : '连通失败' }}
        </VTag>
        <span class=":uno: text-sm text-gray-600">{{ (testConnectivity.data.value as any).message }}</span>
        <span v-if="(testConnectivity.data.value as any).lastCheckedAt" class=":uno: text-xs text-gray-400">
          检测时间: {{ (testConnectivity.data.value as any).lastCheckedAt }}
        </span>
      </div>

      <VDescription class=":uno: mt-4">
        <VDescriptionItem label="资源名称">
          {{ provider.metadata.name }}
        </VDescriptionItem>
        <VDescriptionItem label="Provider 类型">
          {{ provider.spec.providerType }}
        </VDescriptionItem>
        <VDescriptionItem v-if="provider.spec.baseUrl" label="Base URL">
          {{ provider.spec.baseUrl }}
        </VDescriptionItem>
        <VDescriptionItem label="API Key Secret">
          {{ provider.spec.apiKeySecretName || '未配置' }}
        </VDescriptionItem>
        <VDescriptionItem v-if="provider.spec.proxyHost" label="代理主机">
          {{ provider.spec.proxyHost }}:{{ provider.spec.proxyPort || '' }}
        </VDescriptionItem>
        <VDescriptionItem v-if="provider.status?.lastCheckedAt" label="上次检测">
          {{ provider.status.lastCheckedAt }}
        </VDescriptionItem>
      </VDescription>
    </VCard>

    <VCard class=":uno: mt-4">
      <div class=":uno: mb-4 flex items-center justify-between">
        <h3 class=":uno: text-base font-semibold">关联模型</h3>
        <div class=":uno: flex gap-2">
          <VButton type="secondary" size="sm" @click="discoveryVisible = true">
            <template #icon>
              <RiDownloadCloudLine />
            </template>
            从供应商获取
          </VButton>
          <VButton type="primary" size="sm" @click="modelFormVisible = true">
            <template #icon>
              <RiAddLine />
            </template>
            添加模型
          </VButton>
        </div>
      </div>
      <ModelList :models="models || []" :provider-name="provider.metadata.name" />
      <ModelDiscoveryModal
        v-if="discoveryVisible && providerTypeInfo"
        :provider-name="provider.metadata.name"
        :provider-type="providerTypeInfo"
        @close="discoveryVisible = false"
      />
      <VModal
        v-if="modelFormVisible"
        title="添加模型"
        :width="600"
        @close="modelFormVisible = false"
      >
        <ModelForm
          :provider-name="provider.metadata.name"
          @saved="onModelFormSaved"
          @cancel="modelFormVisible = false"
        />
      </VModal>
    </VCard>
  </div>
</template>
