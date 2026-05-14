<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import { useTestConnectivity } from '@/composables/useProviders'
import {
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VLoading,
  VSpace,
} from '@halo-dev/components'
import { useQuery } from '@tanstack/vue-query'
import { useRouteQuery } from '@vueuse/router'
import { ref } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiTestTubeLine from '~icons/ri/test-tube-line'
import ProviderEditingModal from './components/ProviderEditingModal.vue'
import ProviderModelsList from './components/ProviderModelsList.vue'

const selectedProvider = useRouteQuery<string | undefined>('provider')

const { data: provider, isLoading } = useQuery({
  queryKey: ['plugin:ai-foundation:provider', selectedProvider],
  queryFn: async () => {
    if (!selectedProvider.value) {
      return null
    }

    const { data } = await aiConsoleApiClient.provider.getProvider({
      name: selectedProvider.value,
    })
    return data
  },
})

const testConnectivity = useTestConnectivity()

function onTest() {
  testConnectivity.mutate(provider.value?.metadata.name || '')
}

const editingModalVisible = ref(false)
</script>

<template>
  <VLoading v-if="isLoading" />
  <div v-else-if="!provider">获取供应商失败</div>
  <div v-else class=":uno: space-y-4">
    <VCard :title="provider.spec.displayName" :body-class="['!p-0']">
      <template #actions>
        <div class=":uno: px-4">
          <VSpace>
            <VButton size="sm" :loading="testConnectivity.isPending.value" @click="onTest">
              <template #icon>
                <RiTestTubeLine />
              </template>
              测试连通性
            </VButton>
            <VButton type="secondary" size="sm" @click="editingModalVisible = true">
              <template #icon>
                <RiEditLine />
              </template>
              编辑
            </VButton>
            <VButton type="danger" size="sm">
              <template #icon>
                <RiDeleteBinLine />
              </template>
              删除
            </VButton>
          </VSpace>
        </div>
      </template>

      <VDescription>
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
      </VDescription>
    </VCard>

    <ProviderModelsList v-if="provider" :provider="provider" />
  </div>

  <ProviderEditingModal
    v-if="provider && editingModalVisible"
    :provider="provider"
    @close="editingModalVisible = false"
  />
</template>
