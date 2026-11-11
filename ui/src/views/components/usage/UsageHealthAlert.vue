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

const title = computed(() => {
  if (unavailable.value) {
    return '统计存储不可用'
  }
  return '统计状态检查失败'
})

const storageErrors = computed(() => {
  return [props.health?.initializationError, props.health?.integrityError].filter(
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
    v-if="unavailable || error"
    :type="unavailable ? 'error' : 'warning'"
    :title="title"
    role="alert"
  >
    <template #description>
      <div class=":uno: min-w-0 text-sm">
        <div class=":uno: mt-1 text-xs leading-5">
          <template v-if="unavailable">
            模型调用不受影响，用量统计已停止记录。插件不会自动恢复或清空数据库，请查看 Halo
            日志排查原因。
          </template>
          <template v-else>
            无法获取统计健康状态，当前页面展示的汇总与历史可能低于实际用量，请稍后刷新重试。
          </template>
        </div>
        <details v-if="unavailable" class=":uno: mt-2 text-xs leading-5">
          <summary class=":uno: cursor-pointer">如何处理统计存储故障</summary>
          <p class=":uno: mt-1">
            数据库位于 Halo 工作目录的 plugins/ai-foundation/ai-foundation.sqlite。 操作文件前请停止
            Halo，并保留数据库及同名 -wal、-shm 文件的副本。
            可自行恢复有效备份；若接受丢弃全部统计数据，可移走这三个文件，再启动 Halo 创建新库。
            页面中的「重置统计」仅适用于可用的数据库，不能修复存储故障。
          </p>
        </details>
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
