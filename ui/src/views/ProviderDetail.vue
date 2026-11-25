<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import {
  AiProviderStatusPhaseEnum,
  ProviderHealthCheckResultEnum,
  type ProviderHealthReport,
} from '@/api/generated'
import { useProviderQueryState } from '@/composables/use-provider-state'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { QK_PROVIDER, QK_PROVIDERS, reloadProviderQueries } from '@/composables/use-providers-fetch'
import {
  formatProviderCheckMeta,
  orderedProviderChecks,
  providerCheckResultDotState,
  providerCheckResultLabel,
  providerCheckSummary,
  providerCheckTargetLabel,
  providerFailureCategoryLabel,
  providerFailureHint,
} from '@/utils/provider-health'
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VDescription,
  VDescriptionItem,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VLoading,
  VStatusDot,
  type StatusDotState,
} from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, shallowRef } from 'vue'
import MingcuteDelete2Line from '~icons/mingcute/delete-2-line'
import MingcuteDownLine from '~icons/mingcute/down-line'
import MingcuteEdit3Line from '~icons/mingcute/edit-3-line'
import MingcuteMore2Line from '~icons/mingcute/more-2-line'
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
    ? utils.date.format(provider.value.status.lastCheckedAt, 'YYYY-MM-DD HH:mm:ss')
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
  mutationFn: async (deep: boolean) => {
    if (!selectedProvider.value) {
      return
    }

    return await aiConsoleApiClient.provider.testProviderConnectivity({
      name: selectedProvider.value,
      deep,
    })
  },
  onSuccess: (response) => {
    if (response) {
      applyReport(response.data)
    }
    reloadProviderQueries(queryClient, selectedProvider)
  },
  onError: (error) => {
    Toast.error('连通性检查失败: ' + (error as Error).message)
  },
})

function applyReport(report: ProviderHealthReport) {
  if (!provider.value || provider.value.metadata.name !== report.providerName) {
    return
  }
  queryClient.setQueryData([QK_PROVIDER, selectedProvider.value], {
    ...provider.value,
    status: {
      phase: report.phase,
      failureCategory: report.failureCategory,
      message: report.message,
      lastCheckedAt: report.checkedAt,
      checks: report.checks,
    },
  })
}

function runConnectivityCheck(deep = false) {
  testConnectivityMutation.mutate(deep)
}

const orderedChecks = computed(() => orderedProviderChecks(provider.value?.status?.checks))

const hasChecks = computed(() => orderedChecks.value.length > 0)

const checksExpanded = ref(false)

const diagnosticsSummary = computed(() => {
  if (!hasChecks.value) {
    return '尚未执行检查'
  }
  const failed = orderedChecks.value.find(
    (check) => check.result === ProviderHealthCheckResultEnum.Failed,
  )
  if (failed) {
    const detail = providerCheckSummary(failed)
    return `${providerCheckTargetLabel(failed.target)}失败${detail ? `：${detail}` : ''}`
  }
  return '全部执行项通过'
})

const statusInfoText = computed(() => {
  if (hasChecks.value) {
    return diagnosticsSummary.value
  }
  return provider.value?.status?.message || '尚未执行检查'
})

const failureCategoryText = computed(
  () => providerFailureCategoryLabel(provider.value?.status?.failureCategory) || '—',
)

const failureHint = computed(() => providerFailureHint(provider.value?.status?.failureCategory))

const expandedDetails = ref<Record<string, boolean>>({})

function isDetailExpanded(target?: string) {
  return !!target && !!expandedDetails.value[target]
}

function toggleDetail(target: string | undefined) {
  if (!target) {
    return
  }
  expandedDetails.value[target] = !expandedDetails.value[target]
}
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
              <img :src="providerType?.iconUrl ?? undefined" class=":uno: size-5 flex-none" />
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
            <div class=":uno: flex items-center gap-1">
              <VButton
                size="sm"
                :loading="testConnectivityMutation.isPending.value"
                @click="runConnectivityCheck(false)"
              >
                <template #icon>
                  <MingcuteRadar2Line />
                </template>
                检查连通性
              </VButton>
              <VDropdown>
                <VButton
                  size="sm"
                  type="secondary"
                  :disabled="testConnectivityMutation.isPending.value"
                  aria-label="更多检查选项"
                >
                  <template #icon>
                    <MingcuteMore2Line />
                  </template>
                </VButton>
                <template #popper>
                  <VDropdownItem @click="runConnectivityCheck(true)">
                    深度检查（会实际调用模型）
                  </VDropdownItem>
                </template>
              </VDropdown>
            </div>
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
        <div class=":uno: grid grid-cols-1 gap-4 px-4 py-4 sm:grid-cols-2 xl:grid-cols-5">
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
            <div class=":uno: text-xs text-gray-500 font-medium">失败分类</div>
            <div class=":uno: truncate text-sm text-gray-950 font-semibold">
              {{ failureCategoryText }}
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

        <VDescription class=":uno: provider-detail-description">
          <VDescriptionItem label="Base URL" class=":uno: !px-4 !py-4">
            <div class=":uno: min-w-0 break-all text-xs text-gray-700 font-mono">
              {{ baseUrlText }}
            </div>
          </VDescriptionItem>
          <VDescriptionItem label="状态信息" class=":uno: !px-4 !py-4">
            <div class=":uno: min-w-0 space-y-2">
              <div class=":uno: flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <div class=":uno: min-w-0">
                  <VStatusDot
                    v-if="testConnectivityMutation.isPending.value"
                    state="warning"
                    text="检查中..."
                    animate
                  />
                  <VStatusDot v-else :state="statusDot.state" :text="statusInfoText" />
                </div>
                <button
                  v-if="hasChecks"
                  type="button"
                  class=":uno: inline-flex flex-none items-center gap-0.5 text-xs text-blue-600 hover:underline"
                  :aria-expanded="checksExpanded"
                  @click="checksExpanded = !checksExpanded"
                >
                  {{ checksExpanded ? '收起' : '展开' }}
                  <MingcuteDownLine
                    class=":uno: size-3.5 transition-transform"
                    :class="{ ':uno: rotate-180': checksExpanded }"
                  />
                </button>
              </div>

              <div
                v-if="hasChecks && checksExpanded"
                class=":uno: grid grid-cols-1 gap-x-6 gap-y-3 pt-1 sm:grid-cols-2"
              >
                <div
                  v-for="(check, index) in orderedChecks"
                  :key="check.target || index"
                  class=":uno: min-w-0"
                >
                  <div class=":uno: flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span class=":uno: text-sm text-gray-950 font-medium">
                      {{ providerCheckTargetLabel(check.target) }}
                    </span>
                    <VStatusDot
                      :state="providerCheckResultDotState(check.result)"
                      :text="providerCheckResultLabel(check.result)"
                    />
                    <span v-if="formatProviderCheckMeta(check)" class=":uno: text-xs text-gray-400">
                      {{ formatProviderCheckMeta(check) }}
                    </span>
                  </div>
                  <div
                    v-if="providerCheckSummary(check)"
                    class=":uno: mt-0.5 truncate text-xs text-gray-600"
                    :title="providerCheckSummary(check)"
                  >
                    {{ providerCheckSummary(check) }}
                  </div>
                  <div v-if="check.detail" class=":uno: mt-1">
                    <button
                      type="button"
                      class=":uno: text-xs text-blue-600 hover:underline"
                      @click="toggleDetail(check.target)"
                    >
                      {{ isDetailExpanded(check.target) ? '收起原始错误摘要' : '查看原始错误摘要' }}
                    </button>
                    <pre
                      v-if="isDetailExpanded(check.target)"
                      class=":uno: mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded-md bg-gray-50 p-2 text-xs text-gray-700"
                      >{{ check.detail }}</pre>
                  </div>
                </div>
              </div>

              <div
                v-if="failureHint && checksExpanded"
                class=":uno: rounded-md bg-red-50/60 px-3 py-2 text-xs text-red-700"
              >
                {{ failureHint }}
              </div>
            </div>
          </VDescriptionItem>
        </VDescription>
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

<style scoped>
/* 标签列固定宽度、值列占满剩余空间，展开检查项时不被挤在半宽内 */
.provider-detail-description :deep(.description-item__content) {
  min-width: 0;
}

/* VStatusDot 默认不换行，长状态信息在窄屏会横向溢出 */
.provider-detail-description :deep(.status-dot-text) {
  white-space: normal;
  overflow-wrap: anywhere;
}

@media (min-width: 1024px) {
  .provider-detail-description :deep(.description-item-wrapper) {
    grid-template-columns: 7rem minmax(0, 1fr);
  }

  .provider-detail-description :deep(.description-item__content) {
    grid-column: auto;
  }
}

/* 窄屏时详情区域很窄，标签列会被压到逐字换行，改为上下堆叠 */
@media (max-width: 1023.98px) {
  .provider-detail-description :deep(.description-item-wrapper) {
    grid-template-columns: minmax(0, 1fr);
  }

  .provider-detail-description :deep(.description-item__content) {
    margin-top: 0.25rem;
    grid-column: auto;
  }
}
</style>
