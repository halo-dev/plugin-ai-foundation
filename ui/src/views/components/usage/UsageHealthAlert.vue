<script setup lang="ts">
import type { UsageHealth } from '@/api/generated'
import { formatDateTime, formatTokens } from '@/utils/usage'
import { VAlert } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  health?: UsageHealth
  error?: boolean
}>()

const unavailable = computed(() => props.health?.available === false)
const incomplete = computed(() => props.health?.complete === false)
const degraded = computed(() => unavailable.value || incomplete.value)

const title = computed(() => {
  if (unavailable.value) {
    return '统计存储不可用'
  }
  if (incomplete.value) {
    return '统计数据可能不完整'
  }
  return '健康状态检查失败'
})

const storageErrors = computed(() => {
  return [props.health?.migrationError, props.health?.integrityError].filter(
    (item): item is string => !!item,
  )
})

const lossDetails = computed(() => {
  const health = props.health
  if (!health) {
    return []
  }
  const details: string[] = []
  if (health.droppedEvents) {
    details.push(`丢弃事件 ${formatTokens(health.droppedEvents)} 条`)
  }
  if (health.incompleteCalls) {
    details.push(`不完整调用 ${formatTokens(health.incompleteCalls)} 条`)
  }
  if (health.writeFailures) {
    details.push(`写入失败 ${formatTokens(health.writeFailures)} 次`)
  }
  if (health.queueDepth) {
    details.push(`当前队列积压 ${formatTokens(health.queueDepth)} 条`)
  }
  return details
})

const timeDetails = computed(() => {
  const health = props.health
  if (!health) {
    return []
  }
  const details: string[] = []
  if (health.affectedSince) {
    details.push(`影响起始时间 ${formatDateTime(health.affectedSince)}`)
  }
  if (health.affectedUntil) {
    details.push(`影响结束时间 ${formatDateTime(health.affectedUntil)}`)
  }
  if (health.lastWriteErrorAt) {
    details.push(`最近写入错误 ${formatDateTime(health.lastWriteErrorAt)}`)
  }
  return details
})
</script>

<template>
  <VAlert
    v-if="degraded || error"
    :type="unavailable ? 'error' : 'warning'"
    :title="title"
    role="alert"
  >
    <template #description>
      <div class=":uno: min-w-0 text-sm">
        <div class=":uno: mt-1 text-xs leading-5">
          <template v-if="unavailable">
            模型调用不受影响，但用量数据可能正在丢失。历史数据未被清除，请检查存储状态后再解读统计结果。
          </template>
          <template v-else-if="incomplete">
            部分统计事件未能持久化，当前汇总与历史可能低于实际用量。
          </template>
          <template v-else>
            无法获取统计健康状态，当前页面展示的汇总与历史可能低于实际用量，请稍后刷新重试。
          </template>
        </div>
        <ul v-if="storageErrors.length" class=":uno: mt-1 list-disc pl-4 text-xs leading-5">
          <li v-for="error in storageErrors" :key="error">{{ error }}</li>
        </ul>
        <div v-if="lossDetails.length" class=":uno: mt-1 text-xs leading-5">
          {{ lossDetails.join('；') }}
        </div>
        <div v-if="timeDetails.length" class=":uno: mt-1 text-xs leading-5 opacity-80">
          {{ timeDetails.join('；') }}
        </div>
      </div>
    </template>
  </VAlert>
</template>
