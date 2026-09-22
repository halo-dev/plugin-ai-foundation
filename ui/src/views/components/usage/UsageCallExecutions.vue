<script setup lang="ts">
import { useUsageCallDetail } from '@/composables/use-usage-statistics'
import {
  formatDateTime,
  formatDuration,
  formatTokens,
  usageQualityLabel,
  usageStatusLabel,
  usageStatusTagTheme,
  usageUnitKindLabel,
} from '@/utils/usage'
import { VButton, VLoading, VTag } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'

const props = defineProps<{
  callId: string
}>()

const { data, isLoading, isError, refetch } = useUsageCallDetail(() => props.callId)
</script>

<template>
  <section class=":uno: usage-executions" aria-label="执行记录">
    <div class=":uno: usage-executions-heading">
      <h4>执行过程</h4>
      <span>每次请求的实际消耗</span>
    </div>
    <VLoading v-if="isLoading" />
    <div v-else-if="isError" class=":uno: usage-executions-message">
      执行详情加载失败 <VButton size="sm" @click="refetch()">重试</VButton>
    </div>
    <p v-else-if="!data?.executions?.length" class=":uno: usage-executions-message">
      暂无执行记录。执行明细仅保留 30 天；较早的调用或缓存命中的调用可能没有执行数据。
    </p>
    <div v-else class=":uno: usage-execution-list">
      <article v-for="execution in data.executions" :key="execution.id" class=":uno: usage-execution">
        <div class=":uno: usage-execution-title">
          <strong
            >{{ usageUnitKindLabel(execution.unitKind)
            }}<template v-if="execution.unitIndex !== undefined">
              #{{ execution.unitIndex + 1 }}</template
            ></strong
          >
          <span class=":uno: usage-attempt">{{
            execution.attemptIndex == null
              ? '尝试次数未知'
              : execution.attemptIndex === 0
                ? '首次请求'
                : `第 ${execution.attemptIndex} 次重试`
          }}</span>
          <VTag size="sm" :theme="usageStatusTagTheme(execution.status)">{{
            usageStatusLabel(execution.status)
          }}</VTag>
        </div>
        <dl class=":uno: usage-execution-metrics">
          <div>
            <dt>输入 Token</dt>
            <dd>{{ formatTokens(execution.usage?.inputTokens) }}</dd>
          </div>
          <div>
            <dt>输出 Token</dt>
            <dd>{{ formatTokens(execution.usage?.outputTokens) }}</dd>
          </div>
          <div>
            <dt>合计 Token</dt>
            <dd>{{ formatTokens(execution.usage?.accountedTotalTokens) }}</dd>
          </div>
          <div>
            <dt>耗时</dt>
            <dd>
              {{
                execution.startedAt && execution.completedAt
                  ? formatDuration(
                      utils.date.dayjs(execution.completedAt).diff(execution.startedAt),
                    )
                  : '未知'
              }}
            </dd>
          </div>
        </dl>
        <div class=":uno: usage-execution-footnote">
          <span>{{ usageQualityLabel(execution.usage?.quality) }}</span>
          <time :datetime="execution.startedAt">{{ formatDateTime(execution.startedAt) }}</time>
        </div>
        <p v-if="execution.error?.type" class=":uno: usage-execution-error">
          错误：{{ execution.error.type
          }}<template v-if="execution.error.code">（{{ execution.error.code }}）</template>
        </p>
      </article>
    </div>
  </section>
</template>
<style scoped>
.usage-executions-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 12px;
}
.usage-executions-heading h4 {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}
.usage-executions-heading > span {
  font-size: 12px;
  color: #9ca3af;
}
.usage-executions-message {
  font-size: 12px;
  line-height: 20px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.usage-execution-list {
  display: grid;
  gap: 12px;
}
.usage-execution {
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
}
.usage-execution-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 12px;
}
.usage-execution-title strong {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}
.usage-attempt {
  color: #6b7280;
  margin-right: auto;
}
.usage-execution-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}
.usage-execution-metrics dt {
  font-size: 11px;
  color: #6b7280;
}
.usage-execution-metrics dd {
  margin-top: 4px;
  color: #111827;
  font-size: 16px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.usage-execution-footnote {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 11px;
  color: #9ca3af;
}
.usage-execution-error {
  color: #b91c1c;
  font-size: 12px;
  line-height: 20px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #fee2e2;
  overflow-wrap: anywhere;
}
@media (max-width: 639px) {
  .usage-execution-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
