<script setup lang="ts">
import { useCallerPluginsFetch } from '@/composables/use-caller-plugins-fetch'
import { useModelsFetch } from '@/composables/use-models-fetch'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { useUsageFilters } from '@/composables/use-usage-filters'
import {
  reloadUsageQueries,
  useUsageCalls,
  useUsageHealth,
  useUsageSummary,
  useUsageTrends,
} from '@/composables/use-usage-statistics'
import UsageCallTable from '@/views/components/usage/UsageCallTable.vue'
import UsageFilterBar from '@/views/components/usage/UsageFilterBar.vue'
import UsageHealthAlert from '@/views/components/usage/UsageHealthAlert.vue'
import UsageResetModal from '@/views/components/usage/UsageResetModal.vue'
import UsageStatusPanel from '@/views/components/usage/UsageStatusPanel.vue'
import UsageSummaryCards from '@/views/components/usage/UsageSummaryCards.vue'
import UsageTrendChart from '@/views/components/usage/UsageTrendChart.vue'
import { VAlert, VButton, VCard } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, shallowRef } from 'vue'

const filters = useUsageFilters()
const queryClient = useQueryClient()

const healthQuery = useUsageHealth()
const summaryQuery = useUsageSummary(filters.buildParams, filters.fingerprint, filters.valid)
const trendsQuery = useUsageTrends(filters.buildParams, filters.fingerprint, filters.valid)
const callsQuery = useUsageCalls(filters.buildParams, filters.fingerprint, filters.valid)

const { data: callers } = useCallerPluginsFetch()
const { data: providers } = useProvidersFetch()
const selectedProviderName = computed(() => filters.state.value.providerName)
const { data: models } = useModelsFetch({ providerName: selectedProviderName })

const resetModalVisible = shallowRef(false)

const callItems = computed(
  () => callsQuery.data.value?.pages.flatMap((page) => page.items || []) || [],
)

const hasNextPage = computed(() => !!callsQuery.hasNextPage?.value)

const fetching = computed(
  () =>
    summaryQuery.isFetching.value || trendsQuery.isFetching.value || callsQuery.isFetching.value,
)

async function refresh() {
  filters.refreshAnchor()
  await nextTick()
  reloadUsageQueries(queryClient)
}
</script>

<template>
  <div class=":uno: usage-page :uno: flex flex-col gap-4 p-2 md:p-4">
    <UsageHealthAlert :health="healthQuery.data.value" :error="healthQuery.isError.value" />

    <VCard :body-class="['!p-0']">
      <div class=":uno: px-5 py-4">
        <UsageFilterBar
          :state="filters.state.value"
          :callers="callers"
          :providers="providers"
          :models="models"
          :feature-invalid="filters.featureInvalid.value"
          :has-dimension-filters="filters.hasDimensionFilters.value"
          :fetching="fetching"
          @change="filters.applyChange"
          @clear="filters.clearDimensionFilters"
          @refresh="refresh"
          @reset="resetModalVisible = true"
        />
      </div>
      <div v-if="filters.valid.value" class=":uno: border-t border-gray-100">
        <div>
          <div
            v-if="summaryQuery.isError.value"
            class=":uno: flex items-center gap-3 py-4 text-sm text-gray-500"
          >
            汇总数据加载失败
            <VButton size="sm" @click="summaryQuery.refetch()">重试</VButton>
          </div>
          <UsageSummaryCards
            v-else
            :summary="summaryQuery.data.value"
            :loading="summaryQuery.isLoading.value"
          />
        </div>
      </div>
    </VCard>

    <VAlert
      v-if="!filters.valid.value"
      :closable="false"
      type="warning"
      description="请选择有效的起止日期（开始日期需早于结束日期，且跨度不超过 3660 天）。"
      role="status"
    />

    <template v-else>
      <div class=":uno: grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
        <VCard :body-class="['!p-0']" class=":uno: min-w-0">
          <div
            v-if="trendsQuery.isError.value"
            class=":uno: flex items-center gap-3 p-5 text-sm text-gray-500"
          >
            趋势数据加载失败 <VButton size="sm" @click="trendsQuery.refetch()">重试</VButton>
          </div>
          <UsageTrendChart
            v-else
            :points="trendsQuery.data.value"
            :loading="trendsQuery.isLoading.value"
            :selected-resolution="filters.state.value.resolution"
            :from="filters.buildParams()?.from"
            :to="filters.buildParams()?.to"
            @change-resolution="filters.applyChange({ resolution: $event })"
          />
        </VCard>
        <VCard :body-class="['!p-0']">
          <div v-if="summaryQuery.isError.value" class=":uno: p-5 text-sm text-gray-500">
            调用状态暂不可用
          </div>
          <UsageStatusPanel
            v-else
            :summary="summaryQuery.data.value"
            :loading="summaryQuery.isLoading.value"
            :selected-status="filters.state.value.status"
            @select="filters.applyChange({ status: $event })"
          />
        </VCard>
      </div>

      <VCard :body-class="['!p-0']">
        <template #header>
          <div class=":uno: w-full flex flex-wrap items-center justify-between gap-2 px-5 py-4">
            <div class=":uno: flex items-center gap-2">
              <span class=":uno: text-sm text-gray-950 font-semibold">调用明细</span
              ><span class=":uno: text-xs text-gray-400">{{ callItems.length }} 条已加载</span>
            </div>
            <span class=":uno: text-xs text-gray-400">明细保留 90 天 · 点击查看执行过程</span>
          </div>
        </template>
        <UsageCallTable
          :items="callItems"
          :models="models"
          :providers="providers"
          :callers="callers"
          :loading="callsQuery.isLoading.value"
          :error="callsQuery.isError.value && callItems.length === 0"
          :load-more-error="callsQuery.isError.value && callItems.length > 0"
          :has-next-page="hasNextPage"
          :fetching-next-page="callsQuery.isFetchingNextPage.value"
          @load-more="callsQuery.fetchNextPage()"
          @retry="callsQuery.refetch()"
        />
      </VCard>
    </template>

    <UsageResetModal v-if="resetModalVisible" @close="resetModalVisible = false" />
  </div>
</template>

<style scoped>
.usage-page {
  width: 100%;
  max-width: 1600px;
  margin-inline: auto;
}
</style>
