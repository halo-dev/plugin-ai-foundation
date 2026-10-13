<script setup lang="ts">
import type { UsageSummary } from '@/api/generated'
import { formatCoverage, formatTokens } from '@/utils/usage'
import { VAlert } from '@halo-dev/components'
import NumberFlow from '@number-flow/vue'
import { computed } from 'vue'

const props = defineProps<{ summary?: UsageSummary | null }>()
const emit = defineEmits<{ (event: 'filterFailed'): void }>()

const successRate = computed(() => {
  const total = props.summary?.callCount
  if (!total) return undefined
  return (props.summary?.successCount ?? 0) / total
})

const cacheHitRate = computed(() => {
  // Render 0 placeholders before the summary arrives to avoid layout shifts
  if (!props.summary) return 0
  const input = props.summary.inputTokens
  const cacheRead = props.summary.cacheReadInputTokens
  if (input == null || cacheRead == null) return undefined
  const total = input + cacheRead
  if (total <= 0) return undefined
  return cacheRead / total
})

// Partial coverage must not round up to a misleading 100% (same guard as formatCoverage)
function cappedCoverage(value?: number | null) {
  if (value == null) return undefined
  if (value >= 1) return 1
  if (value >= 0.9995) return 0.999
  return value
}

const coverage = computed(() => {
  if (!props.summary) return 0
  return cappedCoverage(props.summary.usageCoverage)
})

// Missing fields render as unknown only after the summary arrives; everything is a 0 placeholder before that
function subTokens(value?: number | null) {
  if (value != null) return formatTokens(value)
  return props.summary ? '未知' : '0'
}

const INTEGER_FORMAT = { maximumFractionDigits: 0 } as const
const PERCENT_FORMAT = { style: 'percent', maximumFractionDigits: 1 } as const
</script>

<template>
  <div aria-label="用量汇总">
    <VAlert
      v-if="summary?.complete === false"
      class=":uno: m-4"
      type="warning"
      description="部分统计数据丢失，数值可能低于实际用量。"
      role="status"
    />
    <dl class=":uno: grid grid-cols-2 lg:grid-cols-5">
      <div class=":uno: usage-kpi">
        <dt class=":uno: usage-kpi-label">Token 总量</dt>
        <dd class=":uno: usage-kpi-value">
          <NumberFlow :value="summary?.accountedTotalTokens ?? 0" :format="INTEGER_FORMAT" />
        </dd>
        <dd class=":uno: usage-kpi-sub">
          输入 {{ subTokens(summary?.inputTokens) }} · 输出
          {{ subTokens(summary?.outputTokens) }}
        </dd>
      </div>
      <div class=":uno: usage-kpi">
        <dt class=":uno: usage-kpi-label">调用次数</dt>
        <dd class=":uno: usage-kpi-value">
          <NumberFlow :value="summary?.callCount ?? 0" :format="INTEGER_FORMAT" />
        </dd>
        <dd class=":uno: usage-kpi-sub">
          <button
            v-if="summary?.failedCount"
            type="button"
            class=":uno: usage-kpi-link"
            @click="emit('filterFailed')"
          >
            {{ summary.failedCount }} 次失败</button
          ><span v-else>0 次失败</span>
          <template v-if="successRate != null">
            · 成功率 {{ formatCoverage(successRate) }}</template
          >
        </dd>
      </div>
      <div class=":uno: usage-kpi">
        <dt class=":uno: usage-kpi-label">缓存命中率</dt>
        <dd class=":uno: usage-kpi-value">
          <NumberFlow v-if="cacheHitRate != null" :value="cacheHitRate" :format="PERCENT_FORMAT" />
          <template v-else>未知</template>
        </dd>
        <dd class=":uno: usage-kpi-sub">缓存读取 {{ subTokens(summary?.cacheReadInputTokens) }}</dd>
        <dd v-if="cacheHitRate != null" class=":uno: usage-kpi-meter" aria-hidden="true">
          <i :style="{ width: `${Math.min(100, cacheHitRate * 100)}%` }"></i>
        </dd>
      </div>
      <div class=":uno: usage-kpi">
        <dt class=":uno: usage-kpi-label">数据完整度</dt>
        <dd class=":uno: usage-kpi-value">
          <NumberFlow v-if="coverage != null" :value="coverage" :format="PERCENT_FORMAT" />
          <template v-else>未知</template>
        </dd>
        <dd class=":uno: usage-kpi-sub">
          缺失 {{ summary?.missingUsageCalls ?? 0 }} · 部分上报
          {{ summary?.partialUsageCalls ?? 0 }}
        </dd>
        <dd v-if="coverage != null" class=":uno: usage-kpi-meter" aria-hidden="true">
          <i :style="{ width: `${Math.min(100, coverage * 100)}%` }"></i>
        </dd>
      </div>
      <div class=":uno: usage-kpi">
        <dt class=":uno: usage-kpi-label">进行中</dt>
        <dd class=":uno: usage-kpi-value">
          <NumberFlow :value="summary?.inProgressCount ?? 0" :format="INTEGER_FORMAT" />
        </dd>
        <dd class=":uno: usage-kpi-sub">
          缓存创建 {{ subTokens(summary?.cacheCreationInputTokens) }} · 推理
          {{ subTokens(summary?.reasoningOutputTokens) }}
        </dd>
      </div>
    </dl>
  </div>
</template>

<style scoped>
.usage-kpi {
  min-width: 0;
  padding: 11px 15px 10px;
}
.usage-kpi + .usage-kpi {
  border-left: 1px solid #f3f4f6;
}
@media (max-width: 1023px) {
  .usage-kpi:nth-child(3) {
    border-left: 0;
  }
  .usage-kpi:nth-child(n + 3) {
    border-top: 1px solid #f3f4f6;
  }
}
@media (max-width: 1023px) and (min-width: 640px) {
  .usage-kpi:nth-child(5) {
    border-left: 0;
  }
}
.usage-kpi-label {
  font-size: 11px;
  color: #6b7280;
}
.usage-kpi-value {
  margin-top: 2px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: #111827;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usage-kpi-sub {
  margin-top: 3px;
  font-size: 11px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usage-kpi-link {
  padding: 0;
  border: 0;
  background: transparent;
  font-size: inherit;
  color: #dc2626;
  cursor: pointer;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}
.usage-kpi-link:hover {
  color: #b91c1c;
}
.usage-kpi-meter {
  height: 5px;
  margin-top: 8px;
  border-radius: 3px;
  background: #f3f4f6;
  overflow: hidden;
}
.usage-kpi-meter i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #4ccba0;
}
</style>
