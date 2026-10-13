<script setup lang="ts">
import { useCallerPluginsFetch } from '@/composables/use-caller-plugins-fetch'
import { useModelsFetch } from '@/composables/use-models-fetch'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { useUsageFilters } from '@/composables/use-usage-filters'
import {
  reloadUsageQueries,
  useUsageCalls,
  useUsageHealth,
  useUsageHeatmapTrends,
  useUsageSummary,
} from '@/composables/use-usage-statistics'
import UsageCallTable from '@/views/components/usage/UsageCallTable.vue'
import UsageFilterBar from '@/views/components/usage/UsageFilterBar.vue'
import UsageHealthAlert from '@/views/components/usage/UsageHealthAlert.vue'
import UsageHeatmap from '@/views/components/usage/UsageHeatmap.vue'
import UsageKpiStrip from '@/views/components/usage/UsageKpiStrip.vue'
import UsageResetModal from '@/views/components/usage/UsageResetModal.vue'
import UsageTrendSection from '@/views/components/usage/UsageTrendSection.vue'
import { VAlert, VButton, VCard } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, nextTick, shallowRef } from 'vue'

const filters = useUsageFilters()
const queryClient = useQueryClient()

const healthQuery = useUsageHealth()
const summaryQuery = useUsageSummary(filters.buildParams, filters.fingerprint, filters.valid)
const heatmapQuery = useUsageHeatmapTrends(filters.buildParams, filters.fingerprint, filters.valid)
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
    summaryQuery.isFetching.value || heatmapQuery.isFetching.value || callsQuery.isFetching.value,
)

async function refresh() {
  filters.refreshAnchor()
  await nextTick()
  reloadUsageQueries(queryClient)
}
</script>

<template>
  <div class=":uno: usage-page :uno: flex flex-col gap-3 p-2">
    <UsageHealthAlert :health="healthQuery.data.value" :error="healthQuery.isError.value" />

    <VCard :body-class="['!p-0']">
      <div class=":uno: px-4 py-3">
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
      <div class=":uno: border-t border-gray-100">
        <div
          v-if="summaryQuery.isError.value"
          class=":uno: flex items-center gap-3 px-4 py-4 text-sm text-gray-500"
        >
          汇总数据加载失败
          <VButton size="sm" @click="summaryQuery.refetch()">重试</VButton>
        </div>
        <UsageKpiStrip
          v-else
          :summary="summaryQuery.data.value"
          @filter-failed="filters.applyChange({ status: 'FAILED' })"
        />
      </div>
    </VCard>

    <VAlert
      v-if="!filters.valid.value"
      :closable="false"
      type="warning"
      title="提示"
      description="开始日期需早于结束日期。"
      role="status"
    />

    <VCard :body-class="['!p-0']" class=":uno: min-w-0">
      <UsageTrendSection
        :filters="filters"
        :models="models"
        :providers="providers"
        :callers="callers"
      />
    </VCard>

    <VCard :body-class="['!p-0']">
      <div
        v-if="heatmapQuery.isError.value"
        class=":uno: flex items-center gap-3 px-4 py-4 text-sm text-gray-500"
      >
        热力图数据加载失败
        <VButton size="sm" @click="heatmapQuery.refetch()">重试</VButton>
      </div>
      <UsageHeatmap v-else :points="heatmapQuery.data.value" />
    </VCard>

    <VCard :body-class="['!p-0']">
      <div class=":uno: flex flex-wrap items-center gap-2 px-4 py-3">
        <h3 class=":uno: text-sm text-gray-950 font-semibold">调用明细</h3>
        <span v-if="!callsQuery.isLoading.value" class=":uno: text-xs text-gray-400"
          >{{ callItems.length }} 条已加载</span
        >
      </div>
      <UsageCallTable
        :items="callItems"
        :models="models"
        :providers="providers"
        :callers="callers"
        :loading="callsQuery.isLoading.value && callItems.length === 0"
        :error="callsQuery.isError.value && callItems.length === 0"
        :load-more-error="callsQuery.isError.value && callItems.length > 0"
        :has-next-page="hasNextPage"
        :fetching-next-page="callsQuery.isFetchingNextPage.value"
        @load-more="callsQuery.fetchNextPage()"
        @retry="callsQuery.refetch()"
      />
    </VCard>

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
