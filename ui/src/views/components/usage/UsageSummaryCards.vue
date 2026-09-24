<script setup lang="ts">
import type { UsageSummary } from '@/api/generated'
import { formatDateTime, formatTokens } from '@/utils/usage'
import { VAlert, VLoading } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{ summary?: UsageSummary | null; loading?: boolean }>()
const cards = computed(() => [
  {
    label: '已报告 Token 总量',
    value: props.summary?.accountedTotalTokens,
    detail: '所有调用的已知消耗',
    key: 'total',
  },
  {
    label: '调用次数',
    value: props.summary?.callCount,
    detail: '包含成功、失败与取消',
    key: 'calls',
  },
  {
    label: '输入 Token',
    value: props.summary?.inputTokens,
    detail: `缓存读取 ${formatTokens(props.summary?.cacheReadInputTokens)}`,
    key: 'input',
  },
  {
    label: '输出 Token',
    value: props.summary?.outputTokens,
    detail: `推理输出 ${formatTokens(props.summary?.reasoningOutputTokens)}`,
    key: 'output',
  },
])
</script>

<template>
  <div aria-label="用量汇总">
    <VLoading v-if="loading" />
    <template v-else>
      <VAlert
        v-if="summary?.complete === false"
        class=":uno: m-4"
        type="warning"
        description="数据可能不完整：部分统计事件丢失，以下数值可能低于实际用量。"
        role="status"
      />
      <VAlert
        v-if="summary?.preciseRange === false"
        class=":uno: m-4"
        type="warning"
        description="部分日期已扩展为 UTC 整天，请以实际统计区间为准。"
        role="status"
      />
      <dl class=":uno: grid grid-cols-2 lg:grid-cols-4">
        <div
          v-for="card in cards"
          :key="card.key"
          class=":uno: usage-metric :uno: min-w-0 px-5 py-5 sm:px-6"
        >
          <dt class=":uno: text-xs text-gray-500 font-medium">{{ card.label }}</dt>
          <dd
            class=":uno: my-3 truncate text-3xl text-gray-950 font-semibold tracking-tight tabular-nums"
            :title="formatTokens(card.value)"
          >
            {{ formatTokens(card.value) }}
          </dd>
          <dd class=":uno: text-xs text-gray-500">{{ card.detail }}</dd>
        </div>
      </dl>
      <details
        class=":uno: usage-notes :uno: border-t border-gray-100 px-5 py-3 text-xs text-gray-500 sm:px-6"
      >
        <summary class=":uno: w-fit cursor-pointer select-none">统计口径与数据范围</summary>
        <div class=":uno: grid gap-2 pt-3 leading-6 md:grid-cols-2 md:gap-6">
          <p>
            按调用开始时间和最终状态统计，包含所有重试、步骤与批次。输入与输出已知时取两者之和，否则取供应商报告总量；未知不记为零。缓存与推理是子项，不重复计入。
          </p>
          <div>
            <p>
              缓存创建 {{ formatTokens(summary?.cacheCreationInputTokens) }} · 数据分辨率：{{
                summary?.resolution === 'DAY' ? '按天（UTC）' : '精确'
              }}
            </p>
            <p v-if="summary?.dataFrom && summary?.dataTo">
              实际统计区间：{{ formatDateTime(summary.dataFrom) }} —
              {{ formatDateTime(summary.dataTo) }}
            </p>
          </div>
        </div>
      </details>
    </template>
  </div>
</template>

<style scoped>
.usage-metric + .usage-metric {
  border-left: 1px solid #f3f4f6;
}
@media (max-width: 1023px) {
  .usage-metric:nth-child(3) {
    border-left: 0;
  }
  .usage-metric:nth-child(n + 3) {
    border-top: 1px solid #f3f4f6;
  }
}
.usage-notes summary::marker {
  color: #9ca3af;
}
</style>
