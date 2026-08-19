<script setup lang="ts">
import { useUsageCallDetail } from '@/composables/use-usage-statistics'
import {
  formatDateTime,
  formatDuration,
  formatTokens,
  usageQualityTagTheme,
  usageQualityLabel,
  usageStatusTagTheme,
  usageStatusLabel,
  usageUnitKindLabel,
} from '@/utils/usage'
import { VLoading, VTag } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'

const props = defineProps<{
  callId: string
}>()

const { data, isLoading, isError } = useUsageCallDetail(() => props.callId)
</script>

<template>
  <div class=":uno: border-t border-gray-100 bg-gray-50/50 px-4 py-3">
    <VLoading v-if="isLoading" />

    <div v-else-if="isError" class=":uno: py-2 text-xs text-red-600">执行详情加载失败</div>

    <div v-else-if="!data?.executions?.length" class=":uno: py-2 text-xs text-gray-500">
      暂无执行记录。执行明细仅保留 30 天，更早的调用可能已没有执行数据。
    </div>

    <div v-else class=":uno: overflow-x-auto">
      <table class=":uno: w-full text-left text-xs">
        <thead>
          <tr class=":uno: text-gray-400">
            <th class=":uno: py-1.5 pr-3 font-medium">执行单元</th>
            <th class=":uno: py-1.5 pr-3 font-medium">尝试</th>
            <th class=":uno: py-1.5 pr-3 font-medium">状态</th>
            <th class=":uno: py-1.5 pr-3 font-medium">开始时间</th>
            <th class=":uno: py-1.5 pr-3 font-medium">耗时</th>
            <th class=":uno: py-1.5 pr-3 font-medium">计入 Token</th>
            <th class=":uno: py-1.5 pr-3 font-medium">输入 / 输出</th>
            <th class=":uno: py-1.5 pr-3 font-medium">用量质量</th>
            <th class=":uno: py-1.5 font-medium">错误</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="execution in data.executions"
            :key="execution.id"
            class=":uno: border-t border-gray-100 text-gray-600"
          >
            <td class=":uno: py-2 pr-3">
              {{ usageUnitKindLabel(execution.unitKind) }}
              <span v-if="execution.unitIndex !== undefined" class=":uno: text-gray-400">
                #{{ execution.unitIndex }}
              </span>
            </td>
            <td class=":uno: py-2 pr-3">{{ execution.attemptIndex ?? '-' }}</td>
            <td class=":uno: py-2 pr-3">
              <VTag size="sm" :theme="usageStatusTagTheme(execution.status)">
                {{ usageStatusLabel(execution.status) }}
              </VTag>
            </td>
            <td class=":uno: whitespace-nowrap py-2 pr-3">
              {{ formatDateTime(execution.startedAt) }}
            </td>
            <td class=":uno: py-2 pr-3">
              {{
                execution.startedAt && execution.completedAt
                  ? formatDuration(
                      utils.date.dayjs(execution.completedAt).diff(execution.startedAt),
                    )
                  : '-'
              }}
            </td>
            <td class=":uno: py-2 pr-3">{{ formatTokens(execution.usage?.accountedTotalTokens) }}</td>
            <td class=":uno: py-2 pr-3">
              {{ formatTokens(execution.usage?.inputTokens) }} /
              {{ formatTokens(execution.usage?.outputTokens) }}
            </td>
            <td class=":uno: py-2 pr-3">
              <VTag size="sm" :theme="usageQualityTagTheme(execution.usage?.quality)">
                {{ usageQualityLabel(execution.usage?.quality) }}
              </VTag>
            </td>
            <td class=":uno: py-2">
              <span v-if="execution.error?.type">
                {{ execution.error.type
                }}<span v-if="execution.error.code">（{{ execution.error.code }}）</span>
              </span>
              <span v-else>-</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
