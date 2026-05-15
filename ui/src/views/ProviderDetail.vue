<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import { AiProviderStatusPhaseEnum } from '@/api/generated'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { QK_PROVIDERS } from '@/composables/use-providers-fetch'
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VLoading,
  VSpace,
  VStatusDot,
  type StatusDotState,
} from '@halo-dev/components'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouteQuery } from '@vueuse/router'
import { computed, ref } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiTestTubeLine from '~icons/ri/test-tube-line'
import ProviderEditingModal from './components/ProviderEditingModal.vue'
import ProviderModelList from './components/ProviderModelList.vue'

const queryClient = useQueryClient()

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

const providerType = useProviderType(provider)

const editingModalVisible = ref(false)

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
    queryClient.invalidateQueries({ queryKey: ['plugin:ai-foundation:provider', selectedProvider] })
  },
  onError: (error) => {
    Toast.error('连通性检查失败: ' + (error as Error).message)
  },
})
</script>

<template>
  <VLoading v-if="isLoading" />
  <div v-else-if="!provider">获取供应商失败</div>
  <div v-else class=":uno: space-y-4">
    <VCard :title="provider.spec.displayName" :body-class="['!p-0']">
      <template #actions>
        <div class=":uno: px-4">
          <VSpace>
            <VButton
              size="sm"
              :loading="testConnectivityMutation.isPending.value"
              @click="testConnectivityMutation.mutate()"
            >
              <template #icon>
                <RiTestTubeLine />
              </template>
              检查连通性
            </VButton>
            <VButton size="sm" @click="editingModalVisible = true">
              <template #icon>
                <RiEditLine />
              </template>
              编辑
            </VButton>
            <VButton type="danger" size="sm" @click="handleDelete" ghost>
              <template #icon>
                <RiDeleteBinLine />
              </template>
              删除
            </VButton>
          </VSpace>
        </div>
      </template>

      <div class=":uno: grid grid-cols-2 gap-2 px-4 py-3 lg:grid-cols-4">
        <div class=":uno: space-y-1">
          <div class=":uno: text-sm text-gray-500">资源名称</div>
          <div class=":uno: text-sm font-semibold">{{ provider.metadata.name }}</div>
        </div>
        <div class=":uno: space-y-1">
          <div class=":uno: text-sm text-gray-500">供应商类型</div>
          <div class=":uno: flex items-center gap-2 text-sm font-semibold">
            <img :src="providerType?.iconUrl" class=":uno: h-4 w-4" />
            {{ providerType?.displayName }}
          </div>
        </div>
        <div class=":uno: space-y-1">
          <div class=":uno: text-sm text-gray-500">状态</div>
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
        <div class=":uno: space-y-1">
          <div class=":uno: text-sm text-gray-500">上次检查</div>
          <div class=":uno: text-sm font-semibold">
            {{ provider.status?.lastCheckedAt ? new Date(provider.status.lastCheckedAt).toLocaleString() : '从未检查' }}
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
