<script setup lang="ts">
import {
  classifyToolInputStream,
  type ToolInputStreamDiagnostic,
  type ToolInputStreamMode,
} from '@/utils/model-test-workbench-tool-input-stream'
import { computed } from 'vue'

const props = defineProps<{
  diagnostics: ToolInputStreamDiagnostic[]
}>()

interface ModePresentation {
  label: string
  className: string
}

const MODE_PRESENTATIONS: Record<ToolInputStreamMode, ModePresentation> = {
  'provider-native-delta': {
    label: '真实增量流式',
    className: ':uno: bg-emerald-100 text-emerald-700',
  },
  'final-only': {
    label: 'final-only',
    className: ':uno: bg-amber-100 text-amber-700',
  },
  'input-error': {
    label: '入参错误',
    className: ':uno: bg-rose-100 text-rose-700',
  },
  pending: {
    label: '等待入参',
    className: ':uno: bg-slate-100 text-slate-600',
  },
}

const summaries = computed(() => props.diagnostics.map(createDiagnosticSummary))

function createDiagnosticSummary(diagnostic: ToolInputStreamDiagnostic) {
  const mode = classifyToolInputStream(diagnostic)
  return {
    ...diagnostic,
    mode: MODE_PRESENTATIONS[mode],
    eventSequence: compactEventSequence(diagnostic),
  }
}

function compactEventSequence(diagnostic: ToolInputStreamDiagnostic) {
  const labels: string[] = []
  let pendingDeltas = 0
  for (const event of diagnostic.events) {
    if (event.type === 'tool-input-delta') {
      pendingDeltas++
      continue
    }
    if (pendingDeltas) {
      labels.push(`delta × ${pendingDeltas}`)
      pendingDeltas = 0
    }
    labels.push(event.type.replace('tool-input-', ''))
  }
  if (pendingDeltas) labels.push(`delta × ${pendingDeltas}`)
  if (diagnostic.droppedEventCount) labels.push(`另有 ${diagnostic.droppedEventCount} 个事件`)
  return labels
}

function formatInput(value: unknown) {
  if (value === undefined) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
</script>

<template>
  <div class=":uno: mb-2.5 border border-cyan-200 rounded-md bg-cyan-50/70 px-3 py-2">
    <div class=":uno: flex flex-wrap items-center justify-between gap-2">
      <div class=":uno: text-xs text-cyan-900 font-semibold">工具入参流诊断</div>
      <div class=":uno: text-[10px] text-cyan-700">浏览器收到的原始 UI Message 事件</div>
    </div>

    <div class=":uno: mt-2 space-y-2">
      <details
        v-for="diagnostic in summaries"
        :key="diagnostic.toolCallId"
        class=":uno: rounded-md bg-white/80 px-2.5 py-2 text-[11px] text-slate-700"
        open
      >
        <summary class=":uno: cursor-pointer list-none [&::-webkit-details-marker]:hidden">
          <div class=":uno: flex flex-wrap items-center gap-1.5">
            <span class=":uno: font-semibold">{{ diagnostic.toolName || '未命名工具' }}</span>
            <span
              class=":uno: rounded px-1.5 py-0.5 font-medium"
              :class="diagnostic.mode.className"
            >
              {{ diagnostic.mode.label }}
            </span>
            <span class=":uno: text-slate-400">{{ diagnostic.toolCallId }}</span>
          </div>
        </summary>

        <div class=":uno: mt-2 flex flex-wrap items-center gap-1 text-[10px]">
          <template v-for="(event, eventIndex) in diagnostic.eventSequence" :key="eventIndex">
            <span v-if="eventIndex" class=":uno: text-slate-300">→</span>
            <span class=":uno: rounded bg-slate-100 px-1.5 py-0.5 font-mono">{{ event }}</span>
          </template>
        </div>
        <div class=":uno: mt-1.5 text-slate-500">
          start {{ diagnostic.startCount }} · delta {{ diagnostic.deltaCount }} · available
          {{ diagnostic.availableCount }} · error {{ diagnostic.errorCount }}
        </div>

        <div
          v-if="diagnostic.protocolIssues.length"
          class=":uno: mt-2 border border-rose-200 rounded bg-rose-50 px-2 py-1.5 text-rose-700"
        >
          <div v-for="issue in diagnostic.protocolIssues" :key="issue">{{ issue }}</div>
        </div>

        <details v-if="diagnostic.inputText" class=":uno: mt-2">
          <summary class=":uno: cursor-pointer text-cyan-700">查看累计 delta 文本</summary>
          <pre
            class=":uno: mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded bg-slate-950 p-2 text-[10px] text-slate-100"
            >{{ diagnostic.inputText }}</pre
          >
        </details>
        <details v-if="diagnostic.input !== undefined" class=":uno: mt-2">
          <summary class=":uno: cursor-pointer text-cyan-700">查看最终解析入参</summary>
          <pre
            class=":uno: mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded bg-slate-950 p-2 text-[10px] text-slate-100"
            >{{ formatInput(diagnostic.input) }}</pre
          >
        </details>
      </details>
    </div>
  </div>
</template>
