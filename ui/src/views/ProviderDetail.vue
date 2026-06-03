<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import { AiProviderStatusPhaseEnum } from '@/api/generated'
import { useProviderQueryState } from '@/composables/use-provider-state'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { QK_PROVIDER, QK_PROVIDERS, reloadProviderQueries } from '@/composables/use-providers-fetch'
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VStatusDot,
  type StatusDotState,
} from '@halo-dev/components'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, shallowRef } from 'vue'
import MingcuteDelete2Line from '~icons/mingcute/delete-2-line'
import MingcuteEdit3Line from '~icons/mingcute/edit-3-line'
import MingcuteRadar2Line from '~icons/mingcute/radar-2-line'
import ProviderEditingModal from './components/ProviderEditingModal.vue'
import ProviderEnableSwitch from './components/ProviderEnableSwitch.vue'
import ProviderModelList from './components/ProviderModelList.vue'

const queryClient = useQueryClient()

const { selectedProvider } = useProviderQueryState()

const { data: provider, isLoading } = useQuery({
  queryKey: [QK_PROVIDER, selectedProvider],
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

const providerType = useProviderType(provider)

const editingModalVisible = shallowRef(false)

function handleDelete() {
  Dialog.warning({
    title: '删除供应商',
    description: '确定要删除该供应商吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      if (!provider.value) {
        Toast.error('供应商不存在')
        return
      }

      await aiConsoleApiClient.provider.deleteProvider({
        name: provider.value?.metadata.name,
      })

      Toast.success('供应商删除成功')
      queryClient.invalidateQueries({ queryKey: [QK_PROVIDERS] })
    },
  })
}

const STATUS_DOT_MAP: Record<AiProviderStatusPhaseEnum, { label: string; state: StatusDotState }> =
  {
    [AiProviderStatusPhaseEnum.Unknown]: {
      label: '未知',
      state: 'default',
    },
    [AiProviderStatusPhaseEnum.Ok]: {
      label: '正常',
      state: 'success',
    },
    [AiProviderStatusPhaseEnum.Error]: {
      label: '异常',
      state: 'error',
    },
  }

const statusDot = computed(() => {
  return STATUS_DOT_MAP[provider.value?.status?.phase || AiProviderStatusPhaseEnum.Unknown]
})

const lastCheckedAtText = computed(() => {
  return provider.value?.status?.lastCheckedAt
    ? new Date(provider.value.status.lastCheckedAt).toLocaleString()
    : '从未检查'
})

const baseUrlText = computed(() => {
  return (
    provider.value?.spec.baseUrl ||
    providerType.value?.defaultBaseUrl ||
    (providerType.value?.requiresBaseUrl ? '未配置' : '使用内置默认地址')
  )
})

const proxyText = computed(() => {
  const proxyHost = provider.value?.spec.proxyHost
  const proxyPort = provider.value?.spec.proxyPort
  if (!proxyHost) {
    return '未配置'
  }
  return proxyPort ? `${proxyHost}:${proxyPort}` : proxyHost
})

const credentialStatusText = computed(() => {
  return provider.value?.spec.apiKeySecretName ? '已配置' : '未配置'
})

const testConnectivityMutation = useMutation({
  mutationFn: async () => {
    if (!selectedProvider.value) {
      return
    }

    return await aiConsoleApiClient.provider.testProviderConnectivity({
      name: selectedProvider.value,
    })
  },
  onSuccess: () => {
    reloadProviderQueries(queryClient, selectedProvider)
  },
  onError: (error) => {
    Toast.error('连通性检查失败: ' + (error as Error).message)
  },
})
</script>

<template>
  <VLoading v-if="isLoading" />
  <VEmpty v-else-if="!provider" title="未选择供应商" message="请从左侧选择一个供应商" />
  <div v-else class=":uno: space-y-4">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: w-full flex flex-col gap-3 px-4 py-3 xl:flex-row xl:items-center">
          <div class=":uno: min-w-0 flex-1">
            <div class=":uno: min-w-0 flex flex-wrap items-center gap-2">
              <img :src="providerType?.iconUrl" class=":uno: h-5 w-5 flex-none" />
              <h2 class=":uno: min-w-0 truncate text-base text-gray-950 font-semibold">
                {{ provider.spec.displayName }}
              </h2>
            </div>
            <div class=":uno: truncate text-xs text-gray-500">
              {{ providerType?.displayName || provider.spec.providerType }} /
              {{ provider.metadata.name }}
            </div>
          </div>
          <div class=":uno: flex flex-wrap items-center gap-2 xl:justify-end">
            <ProviderEnableSwitch :provider="provider" />
            <VButton
              size="sm"
              :loading="testConnectivityMutation.isPending.value"
              @click="testConnectivityMutation.mutate()"
            >
              <template #icon>
                <MingcuteRadar2Line />
              </template>
              检查连通性
            </VButton>
            <VButton size="sm" @click="editingModalVisible = true">
              <template #icon>
                <MingcuteEdit3Line />
              </template>
              编辑
            </VButton>
            <VButton type="danger" size="sm" @click="handleDelete" ghost>
              <template #icon>
                <MingcuteDelete2Line />
              </template>
              删除
            </VButton>
          </div>
        </div>
      </template>

      <div class=":uno: divide-y divide-gray-100">
        <div class=":uno: grid grid-cols-1 gap-4 px-4 py-4 sm:grid-cols-2 xl:grid-cols-4">
          <div class=":uno: min-w-0 space-y-1">
            <div class=":uno: text-xs text-gray-500 font-medium">状态</div>
            <div>
              <VStatusDot
                v-if="testConnectivityMutation.isPending.value"
                state="warning"
                text="检查中..."
                animate
              />
              <VStatusDot v-else :state="statusDot.state" :text="statusDot.label" />
            </div>
          </div>
          <div class=":uno: min-w-0 space-y-1">
            <div class=":uno: text-xs text-gray-500 font-medium">上次检查</div>
            <div class=":uno: truncate text-sm text-gray-950 font-semibold">
              {{ lastCheckedAtText }}
            </div>
          </div>
          <div class=":uno: min-w-0 space-y-1">
            <div class=":uno: text-xs text-gray-500 font-medium">凭据状态</div>
            <div class=":uno: break-all text-sm text-gray-950 font-semibold">
              {{ credentialStatusText }}
            </div>
          </div>
          <div class=":uno: min-w-0 space-y-1">
            <div class=":uno: text-xs text-gray-500 font-medium">代理</div>
            <div class=":uno: break-all text-sm text-gray-950 font-semibold">
              {{ proxyText }}
            </div>
          </div>
        </div>

        <div class=":uno: grid grid-cols-1 gap-4 px-4 py-4 lg:grid-cols-[12rem_1fr]">
          <div class=":uno: text-xs text-gray-500 font-medium">Base URL</div>
          <div class=":uno: min-w-0 break-all text-xs text-gray-700 font-mono">
            {{ baseUrlText }}
          </div>
        </div>

        <div
          v-if="provider.status?.message"
          class=":uno: grid grid-cols-1 gap-4 px-4 py-4 lg:grid-cols-[12rem_1fr]"
        >
          <div class=":uno: text-xs text-gray-500 font-medium">状态信息</div>
          <div class=":uno: min-w-0">
            <VStatusDot :state="statusDot.state" :text="provider.status.message" />
          </div>
        </div>
      </div>
    </VCard>

    <ProviderModelList v-if="provider" :provider="provider" />
  </div>

  <ProviderEditingModal
    v-if="provider && editingModalVisible"
    :provider="provider"
    @close="editingModalVisible = false"
  />
</template>
