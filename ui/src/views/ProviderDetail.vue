<script setup lang="ts">
import type { AiProvider } from '@/api/generated'
import { useModelsByProvider } from '@/composables/useModels'
import { useTestConnectivity } from '@/composables/useProviders'
import { BUILT_IN_PROVIDERS, PROVIDER_TYPE_LABELS } from '@/types'
import {
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { computed, ref } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiDownloadCloudLine from '~icons/ri/download-cloud-line'
import RiEditLine from '~icons/ri/edit-line'
import RiTestTubeLine from '~icons/ri/test-tube-line'
import ModelDiscoveryModal from './ModelDiscoveryModal.vue'
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

const modelFormVisible = ref(false)
const editingModel = ref(null)
const discoveryVisible = ref(false)

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

const isBuiltIn = computed(() => BUILT_IN_PROVIDERS.includes(props.provider.spec.providerType))
</script>

<template>
  <div class="provider-detail">
    <VCard>
      <div class="provider-detail__header">
        <div class="provider-detail__title">
          <h2 class="text-lg font-semibold">{{ provider.spec.displayName }}</h2>
          <VTag size="sm">
            {{ PROVIDER_TYPE_LABELS[provider.spec.providerType] || provider.spec.providerType }}
          </VTag>
          <VStatusDot :state="statusPhase(provider.status?.phase)" />
          <span class="text-sm text-gray-500">{{ provider.status?.phase || 'UNKNOWN' }}</span>
          <VTag v-if="!provider.spec.enabled" size="sm" type="warning">已禁用</VTag>
        </div>
        <div class="provider-detail__actions">
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

      <div v-if="testConnectivity.data.value" class="connectivity-result">
        <VTag :type="testConnectivity.data.value.phase === 'OK' ? 'success' : 'error'" size="sm">
          {{ testConnectivity.data.value.phase === 'OK' ? '连通成功' : '连通失败' }}
        </VTag>
        <span class="text-sm text-gray-600">{{ testConnectivity.data.value.message }}</span>
        <span v-if="testConnectivity.data.value.lastCheckedAt" class="text-xs text-gray-400">
          检测时间: {{ testConnectivity.data.value.lastCheckedAt }}
        </span>
      </div>

      <VDescription class="mt-4">
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

    <VCard class="mt-4">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-base font-semibold">关联模型</h3>
        <div class="flex gap-2">
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
        v-if="discoveryVisible"
        :provider-name="provider.metadata.name"
        :provider-type="provider.spec.providerType"
        @close="discoveryVisible = false"
      />
    </VCard>
  </div>
</template>

<style lang="scss" scoped>
.provider-detail {
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 16px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  &__actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.connectivity-result {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 6px;
  margin-bottom: 12px;
}
</style>
