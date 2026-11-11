<script setup lang="ts">
import type { AgentRunDiagnostics } from '@/utils/model-test-workbench'
import { computed } from 'vue'
import RiRobot2Line from '~icons/ri/robot-2-line'

const props = defineProps<{
  diagnostics: AgentRunDiagnostics
}>()

const policyItems = computed(() => [
  ['调用配置', props.diagnostics.profile || '—'],
  ['调用准备', `${props.diagnostics.callPreparationCount || 0} 次`],
  [
    '完成步骤',
    `${props.diagnostics.completedSteps || 0} / ${props.diagnostics.maximumSteps || 20}`,
  ],
  ['分步策略', props.diagnostics.stepPolicy || '—'],
  ['输出模式', props.diagnostics.outputMode || 'TEXT'],
  ['恢复场景', props.diagnostics.recoveryScenario || 'NONE'],
  ['终止状态', props.diagnostics.terminalState || 'streaming'],
])

function json(value: unknown) {
  return JSON.stringify(value, null, 2)
}
</script>

<template>
  <details class=":uno: mt-3 border border-teal-200 rounded-lg bg-teal-50/70" open>
    <summary
      class=":uno: flex cursor-pointer select-none items-center gap-2 px-3 py-2 text-xs text-teal-900 font-semibold"
    >
      <RiRobot2Line class=":uno: size-4" />
      Agent 运行诊断
    </summary>

    <div class=":uno: border-t border-teal-100 px-3 py-3 space-y-3">
      <div class=":uno: grid grid-cols-2 gap-2 sm:grid-cols-3">
        <div
          v-for="([label, value], index) in policyItems"
          :key="`${label}-${index}`"
          class=":uno: rounded-md bg-white px-2 py-1.5"
        >
          <div class=":uno: text-[10px] text-slate-400">{{ label }}</div>
          <div class=":uno: mt-0.5 break-all text-[11px] text-slate-700 font-medium">
            {{ value }}
          </div>
        </div>
      </div>

      <div v-if="diagnostics.effectiveInstructions" class=":uno: rounded-md bg-white px-2.5 py-2">
        <div class=":uno: text-[10px] text-slate-400">有效指令（调用准备后）</div>
        <div class=":uno: mt-1 whitespace-pre-wrap text-[11px] text-slate-700 leading-relaxed">
          {{ diagnostics.effectiveInstructions }}
        </div>
      </div>

      <div class=":uno: rounded-md bg-white px-2.5 py-2">
        <div class=":uno: text-[10px] text-slate-400">定义级有效工具</div>
        <div v-if="diagnostics.activeTools?.length" class=":uno: mt-1 flex flex-wrap gap-1">
          <span
            v-for="tool in diagnostics.activeTools"
            :key="tool"
            class=":uno: rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600 font-mono"
          >
            {{ tool }}
          </span>
        </div>
        <span v-else class=":uno: mt-1 block text-[10px] text-slate-400">无</span>
      </div>

      <div v-if="diagnostics.stepPreparation?.length" class=":uno: space-y-1.5">
        <div class=":uno: text-[10px] text-slate-500 font-semibold">逐步准备</div>
        <div
          v-for="step in diagnostics.stepPreparation"
          :key="`prepare-${step.stepIndex}`"
          class=":uno: rounded-md bg-white px-2.5 py-2 text-[11px] text-slate-600"
        >
          Step {{ step.stepIndex }} · {{ step.policy }} ·
          {{ step.activeTools?.join(', ') || '无工具' }}
        </div>
      </div>

      <div v-if="diagnostics.steps?.length" class=":uno: space-y-1.5">
        <div class=":uno: text-[10px] text-slate-500 font-semibold">模型步骤与用量</div>
        <div
          v-for="step in diagnostics.steps"
          :key="`step-${step.stepIndex}`"
          class=":uno: rounded-md bg-white px-2.5 py-2"
        >
          <div class=":uno: text-[11px] text-slate-700 font-medium">
            Step {{ step.stepIndex }} · {{ step.finishReason || 'unknown' }}
          </div>
          <pre v-if="step.usage" class=":uno: mt-1 overflow-x-auto text-[10px] text-slate-500">{{
            json(step.usage)
          }}</pre>
        </div>
      </div>

      <div v-if="diagnostics.tools?.length" class=":uno: space-y-1.5">
        <div class=":uno: text-[10px] text-slate-500 font-semibold">工具调用与恢复身份</div>
        <div
          v-for="tool in diagnostics.tools"
          :key="tool.toolCallId"
          class=":uno: rounded-md bg-white px-2.5 py-2"
        >
          <div class=":uno: flex flex-wrap items-center gap-1 text-[11px] text-slate-700">
            <code>{{ tool.toolCallId }}</code>
            <span>· {{ tool.originalToolName || 'unknown' }}</span>
            <span v-if="tool.resolvedToolName" class=":uno: text-teal-700 font-medium">
              → {{ tool.resolvedToolName }}
            </span>
            <span class=":uno: text-slate-400">· {{ tool.state }}</span>
          </div>
          <pre v-if="tool.input" class=":uno: mt-1 overflow-x-auto text-[10px] text-slate-500">{{
            json(tool.input)
          }}</pre>
          <pre
            v-if="tool.output !== undefined"
            class=":uno: mt-1 overflow-x-auto text-[10px] text-emerald-700"
            >{{ json(tool.output) }}</pre
          >
          <div v-if="tool.errorText" class=":uno: mt-1 text-[10px] text-rose-600">
            {{ tool.errorText }}
          </div>
        </div>
      </div>

      <div v-if="diagnostics.warnings?.length" class=":uno: space-y-1">
        <div class=":uno: text-[10px] text-amber-700 font-semibold">运行警告</div>
        <div
          v-for="(warning, index) in diagnostics.warnings"
          :key="`${warning.code || 'warning'}-${index}`"
          class=":uno: rounded-md bg-amber-50 px-2 py-1.5 text-[10px] text-amber-800"
        >
          {{ warning.code ? `${warning.code}: ` : '' }}{{ warning.message }}
        </div>
      </div>

      <div
        v-if="diagnostics.finalOutput !== undefined"
        class=":uno: rounded-md bg-white px-2.5 py-2"
      >
        <div class=":uno: text-[10px] text-slate-400">已校验的最终结构化值</div>
        <pre class=":uno: mt-1 overflow-x-auto text-[10px] text-slate-700">{{
          json(diagnostics.finalOutput)
        }}</pre>
      </div>
    </div>
  </details>
</template>
