<script setup lang="ts">
import { useCallerPluginsFetch } from '@/composables/use-caller-plugins-fetch'
import { useModelsFetch } from '@/composables/use-models-fetch'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { useUsageFilters } from '@/composables/use-usage-filters'
import {
  useUsageCalls,
  useUsageHealth,
  useUsageSummary,
  useUsageTrends,
} from '@/composables/use-usage-statistics'
import UsageCallTable from '@/views/components/usage/UsageCallTable.vue'
import UsageFilterBar from '@/views/components/usage/UsageFilterBar.vue'
import UsageHealthAlert from '@/views/components/usage/UsageHealthAlert.vue'
import UsageResetModal from '@/views/components/usage/UsageResetModal.vue'
import UsageSummaryCards from '@/views/components/usage/UsageSummaryCards.vue'
import UsageTrendChart from '@/views/components/usage/UsageTrendChart.vue'
import { VAlert, VButton, VCard } from '@halo-dev/components'
import { computed, shallowRef } from 'vue'

const filters = useUsageFilters()

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
    summaryQuery.isFetching.value ||
    trendsQuery.isFetching.value ||
    callsQuery.isFetching.value,
)

function refresh() {
  filters.refreshAnchor()
  void healthQuery.refetch()
}
</script>

<template>
  <div class=":uno: flex flex-col gap-2 p-2">
    <UsageHealthAlert :health="healthQuery.data.value" :error="healthQuery.isError.value" />

    <VCard :body-class="['!p-0']">
      <div class=":uno: px-4 py-2.5">
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
    </VCard>

    <VAlert
      v-if="!filters.valid.value"
      type="warning"
      description="请选择有效的起止日期（开始日期需早于结束日期，且跨度不超过 3660 天）。"
      role="status"
    />

    <template v-else>
      <VCard :body-class="['!p-0']">
        <template #header>
          <div class=":uno: w-full px-4 py-3 text-sm text-gray-950 font-semibold">用量汇总</div>
        </template>
        <div class=":uno: px-4 py-3">
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
      </VCard>

      <VCard :body-class="['!p-0']">
        <template #header>
          <div class=":uno: w-full px-4 py-3 text-sm text-gray-950 font-semibold">用量趋势</div>
        </template>
        <div class=":uno: px-4 py-3">
          <div
            v-if="trendsQuery.isError.value"
            class=":uno: flex items-center gap-3 py-4 text-sm text-gray-500"
          >
            趋势数据加载失败
            <VButton size="sm" @click="trendsQuery.refetch()">重试</VButton>
          </div>
          <UsageTrendChart
            v-else
            :points="trendsQuery.data.value"
            :loading="trendsQuery.isLoading.value"
            :selected-resolution="filters.state.value.resolution"
            @change-resolution="filters.applyChange({ resolution: $event })"
          />
        </div>
      </VCard>

      <VCard :body-class="['!p-0']">
        <template #header>
          <div class=":uno: w-full px-4 py-3 text-sm text-gray-950 font-semibold">调用历史</div>
        </template>
        <UsageCallTable
          :items="callItems"
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
