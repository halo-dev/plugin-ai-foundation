<script setup lang="ts">
import type { UsageCallItem } from '@/api/generated'
import { formatDuration, formatTokens, usageQualityLabel, usageStatusLabel } from '@/utils/usage'
import { VStatusDot } from '@halo-dev/components'
import { computed } from 'vue'
import UsageCallExecutions from './UsageCallExecutions.vue'
const props = defineProps<{ call: UsageCallItem }>()
const fields = computed(() => [
  { label: '模型标识', value: props.call.modelName || '未知' },
  { label: '供应商标识', value: props.call.providerName || '未知' },
  {
    label: '请求 → 响应模型',
    value:
      props.call.responseModelId && props.call.responseModelId !== props.call.requestModelId
        ? `${props.call.requestModelId || '未知'} → ${props.call.responseModelId}`
        : props.call.requestModelId || '未知',
  },
  { label: '调用方', value: props.call.callerPluginName || '未知调用方' },
  { label: '调用方版本', value: props.call.callerPluginVersion || '未知' },
  {
    label: '响应方式',
    value: props.call.streaming == null ? '未知' : props.call.streaming ? '流式响应' : '非流式响应',
  },
  ...(props.call.feature ? [{ label: '功能标识', value: props.call.feature }] : []),
  ...(props.call.modelType === 'LANGUAGE'
    ? [{ label: '生成步骤', value: String(props.call.stepCount ?? '未知') }]
    : []),
])
const breakdown = computed(() => [
  { label: '缓存读取', value: props.call.usage?.cacheReadInputTokens },
  { label: '缓存创建', value: props.call.usage?.cacheCreationInputTokens },
  { label: '推理输出', value: props.call.usage?.reasoningOutputTokens },
])
</script>
<template>
  <div class=":uno: usage-call-details">
    <div class=":uno: usage-detail-layout">
      <section class=":uno: usage-consumption" aria-label="本次消耗">
        <h4>本次消耗</h4>
        <div class=":uno: usage-consumption-total">
          {{ formatTokens(call.usage?.accountedTotalTokens) }} <span>Token</span>
        </div>
        <dl class=":uno: usage-consumption-split">
          <div>
            <dt><i class=":uno: input-marker"></i>输入</dt>
            <dd>{{ formatTokens(call.usage?.inputTokens) }}</dd>
          </div>
          <div>
            <dt><i class=":uno: output-marker"></i>输出</dt>
            <dd>{{ formatTokens(call.usage?.outputTokens) }}</dd>
          </div>
        </dl>
        <dl v-if="breakdown.some((item) => item.value != null)" class=":uno: usage-consumption-extra">
          <template v-for="item in breakdown" :key="item.label"
            ><div v-if="item.value != null">
              <dt>{{ item.label }}</dt>
              <dd>{{ formatTokens(item.value) }}</dd>
            </div></template
          >
          <p>已包含在输入或输出中，不重复计入。</p>
        </dl>
        <p class=":uno: usage-consumption-note">
          {{ usageQualityLabel(call.usage?.quality)
          }}<template
            v-if="
              !call.usage || call.usage.quality === 'MISSING' || call.usage.quality === 'PARTIAL'
            "
            >。未报告的消耗无法确定，不代表没有消耗。</template
          >
        </p>
      </section>
      <div class=":uno: usage-process">
        <div class=":uno: usage-process-summary">
          <VStatusDot
            :state="
              call.status === 'SUCCEEDED'
                ? 'success'
                : call.status === 'FAILED'
                  ? 'error'
                  : call.status === 'TIMED_OUT' || call.status === 'ABANDONED'
                    ? 'warning'
                    : 'default'
            "
            :text="usageStatusLabel(call.status)"
          />
          <span>共 {{ call.attemptCount ?? '未知' }} 次执行</span
          ><span>总耗时 {{ formatDuration(call.durationMillis) }}</span>
          <span v-if="call.streaming">流式响应</span>
        </div>
        <p
          v-if="call.complete === false || call.missingExecutionCount"
          class=":uno: usage-process-warning"
        >
          {{ call.complete === false ? '统计数据不完整。' : ''
          }}<template v-if="call.missingExecutionCount"
            >{{ call.missingExecutionCount }} 条执行用量缺失，已报告 Token
            可能低于实际消耗。</template
          >
        </p>
        <UsageCallExecutions v-if="call.id" :call-id="call.id" />
      </div>
    </div>
    <details class=":uno: usage-technical-details">
      <summary>技术详情<span>模型标识、调用来源与诊断信息</span></summary>
      <dl class=":uno: usage-detail-fields">
        <div v-for="field in fields" :key="field.label">
          <dt>{{ field.label }}</dt>
          <dd>{{ field.value }}</dd>
        </div>
        <div>
          <dt>调用 ID</dt>
          <dd>{{ call.id || '未知' }}</dd>
        </div>
        <div v-if="call.errorType">
          <dt>错误类型</dt>
          <dd>
            {{ call.errorType }}<template v-if="call.errorCode">（{{ call.errorCode }}）</template>
          </dd>
        </div>
      </dl>
    </details>
  </div>
</template>
<style scoped>
.usage-call-details {
  border-top: 1px solid #e5e7eb;
  background: #f9fafb;
  padding: 20px 24px 0;
}
.usage-detail-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
  padding-bottom: 20px;
}
.usage-consumption {
  padding-right: 24px;
  border-right: 1px solid #e5e7eb;
}
.usage-consumption h4 {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}
.usage-consumption-total {
  margin: 10px 0 20px;
  color: #111827;
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.usage-consumption-total > span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 400;
}
.usage-consumption-split > div,
.usage-consumption-extra > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
}
.usage-consumption-split dt {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4b5563;
}
.usage-consumption-split dd {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  font-variant-numeric: tabular-nums;
}
.input-marker,
.output-marker {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: #7dd3fc;
}
.output-marker {
  background: #0284c7;
}
.usage-consumption-extra {
  border-top: 1px solid #e5e7eb;
  margin-top: 16px;
  padding-top: 6px;
  color: #6b7280;
}
.usage-consumption-extra p,
.usage-consumption-note {
  font-size: 12px;
  color: #6b7280;
  line-height: 20px;
  margin-top: 12px;
}
.usage-process {
  min-width: 0;
}
.usage-process-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #6b7280;
}
.usage-process-warning {
  font-size: 12px;
  line-height: 20px;
  color: #9a3412;
  background: #fff7ed;
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 12px;
}
.usage-technical-details {
  border-top: 1px solid #e5e7eb;
  padding: 12px 0;
  font-size: 12px;
  color: #4b5563;
}
.usage-technical-details summary {
  cursor: pointer;
  width: fit-content;
}
.usage-technical-details summary > span {
  margin-left: 12px;
  color: #9ca3af;
}
.usage-detail-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 24px;
  padding: 20px 0 8px;
}
.usage-detail-fields dt {
  color: #6b7280;
}
.usage-detail-fields dd {
  margin-top: 4px;
  line-height: 20px;
  overflow-wrap: anywhere;
  color: #374151;
}
@media (max-width: 1100px) {
  .usage-detail-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .usage-consumption {
    border-right: 0;
    padding-right: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    column-gap: 24px;
  }
  .usage-consumption h4 {
    grid-column: 1;
  }
  .usage-consumption-total {
    grid-column: 1;
    grid-row: 2;
    margin-bottom: 0;
  }
  .usage-consumption-split {
    grid-column: 2;
    grid-row: 1 / 3;
  }
  .usage-consumption-note,
  .usage-consumption-extra {
    grid-column: 1 / -1;
  }
  .usage-detail-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 639px) {
  .usage-call-details {
    padding: 16px 16px 0;
  }
  .usage-detail-fields {
    grid-template-columns: minmax(0, 1fr);
  }
  .usage-technical-details summary > span {
    display: none;
  }
}
</style>
