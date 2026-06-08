<script lang="ts" setup>
import { renderMarkdown } from '@/utils/markdown'
import type { WorkbenchMessage } from '@/utils/model-test-workbench'
import { copyToClipboard } from '@/utils/model-test-workbench'
import { VTag } from '@halo-dev/components'
import { ref } from 'vue'
import RiCheckLine from '~icons/ri/check-line'
import RiCpuLine from '~icons/ri/cpu-line'
import RiFileCopyLine from '~icons/ri/file-copy-line'
import RiRestartLine from '~icons/ri/restart-line'
import RiUserLine from '~icons/ri/user-line'

const props = defineProps<{
  message: WorkbenchMessage
  index: number
}>()

const emit = defineEmits<{
  (e: 'regenerate', messageIndex: number): void
  (e: 'toolApproval', options: { messageId: string; eventId: string; approved: boolean }): void
  (
    e: 'externalToolResult',
    options: {
      messageId: string
      eventId: string
      resultText: string
    },
  ): void
  (
    e: 'externalToolError',
    options: {
      messageId: string
      eventId: string
      errorText: string
    },
  ): void
}>()

const copied = ref(false)
const externalResultInputs = ref<Record<string, string>>({})
const externalErrorInputs = ref<Record<string, string>>({})

async function handleCopy() {
  const ok = await copyToClipboard(props.message.content)
  if (ok) {
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  }
}

function handleRegenerate() {
  emit('regenerate', props.index)
}

function handleToolApproval(eventId: string, approved: boolean) {
  emit('toolApproval', {
    messageId: props.message.id,
    eventId,
    approved,
  })
}

function externalResultInput(eventId: string) {
  if (!(eventId in externalResultInputs.value)) {
    externalResultInputs.value[eventId] = '{"ok":true}'
  }
  return externalResultInputs.value[eventId]
}

function handleExternalResultInput(eventId: string, value: string) {
  externalResultInputs.value[eventId] = value
}

function externalErrorInput(eventId: string) {
  if (!(eventId in externalErrorInputs.value)) {
    externalErrorInputs.value[eventId] = 'External tool failed'
  }
  return externalErrorInputs.value[eventId]
}

function handleExternalErrorInput(eventId: string, value: string) {
  externalErrorInputs.value[eventId] = value
}

function handleExternalToolResult(eventId: string) {
  emit('externalToolResult', {
    messageId: props.message.id,
    eventId,
    resultText: externalResultInput(eventId),
  })
}

function handleExternalToolError(eventId: string) {
  emit('externalToolError', {
    messageId: props.message.id,
    eventId,
    errorText: externalErrorInput(eventId),
  })
}
</script>

<template>
  <div
    class=":uno: group flex gap-3"
    :class="{
      ':uno: flex-row-reverse': message.role === 'user',
    }"
  >
    <div
      class=":uno: h-8 w-8 flex flex-none items-center justify-center border rounded-lg shadow-sm"
      :class="{
        ':uno: border-slate-300 bg-slate-900 text-white': message.role === 'user',
        ':uno: border-teal-100 bg-white text-teal-600': message.role === 'assistant',
      }"
    >
      <RiUserLine v-if="message.role === 'user'" class=":uno: size-3.5" />
      <RiCpuLine v-else class=":uno: size-3.5" />
    </div>

    <div
      class=":uno: min-w-0 flex flex-col"
      :class="{
        ':uno: max-w-[85%] items-end': message.role === 'user',
        ':uno: max-w-[88%] flex-1': message.role === 'assistant',
      }"
    >
      <div v-if="message.role === 'assistant'" class=":uno: mb-1 flex items-center gap-2">
        <span class=":uno: text-xs text-slate-600 font-medium">
          {{ message.modelDisplayName || 'Assistant' }}
        </span>
        <VTag v-if="message.state === 'streaming'" size="sm">
          <span class=":uno: inline-flex items-center gap-1.5">
            <span class=":uno: h-1 w-1 animate-pulse rounded-full bg-teal-500" />
            生成中
          </span>
        </VTag>
        <VTag v-else-if="message.state === 'stopped'" size="sm" theme="default"> 已停止 </VTag>
        <VTag v-else-if="message.state === 'error'" size="sm" theme="danger">错误</VTag>
      </div>

      <div
        class=":uno: relative overflow-hidden text-sm leading-relaxed shadow-sm"
        :class="{
          ':uno: rounded-lg rounded-tr-sm border border-slate-800 bg-slate-950 px-3.5 py-2 text-white':
            message.role === 'user',
          ':uno: rounded-lg rounded-tl-sm border border-slate-200 bg-white px-4 py-3 text-slate-800':
            message.role === 'assistant' && message.state !== 'error',
          ':uno: rounded-lg rounded-tl-sm border border-rose-200 bg-rose-50 px-4 py-3 text-rose-700':
            message.role === 'assistant' && message.state === 'error',
        }"
      >
        <details
          v-if="message.role === 'assistant' && message.reasoningContent"
          class=":uno: group/reasoning mb-2.5 border-b border-slate-100 pb-2"
          :open="message.reasoningState === 'streaming'"
        >
          <summary
            class=":uno: flex cursor-pointer select-none list-none items-center gap-1.5 text-xs text-slate-500 font-medium [&::-webkit-details-marker]:hidden"
          >
            <span
              class=":uno: h-3 w-3 transition-transform duration-200 group-open/reasoning:rotate-90"
              :class="{
                ':uno: animate-pulse text-teal-600': message.reasoningState === 'streaming',
              }"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 5l7 7-7 7" />
              </svg>
            </span>
            <span
              class=":uno: h-1.5 w-1.5 rounded-full transition-colors"
              :class="
                message.reasoningState === 'streaming'
                  ? ':uno: animate-pulse bg-teal-500'
                  : ':uno: bg-slate-300'
              "
            />
            推理过程
          </summary>
          <div
            class=":uno: ai-markdown mt-1.5 break-words text-xs text-slate-500"
            v-html="renderMarkdown(message.reasoningContent)"
          />
        </details>

        <div
          v-if="message.role === 'assistant' && message.toolEvents?.length"
          class=":uno: mb-2.5 border-b border-slate-100 pb-2 space-y-1.5"
        >
          <div
            v-for="event in message.toolEvents"
            :key="event.id"
            class=":uno: border rounded-md px-3 py-2 text-xs"
            :class="{
              ':uno: border-slate-200 bg-slate-50 text-slate-700': event.type === 'tool-call',
              ':uno: border-emerald-200 bg-emerald-50 text-emerald-700':
                event.type === 'tool-result',
              ':uno: border-rose-200 bg-rose-50 text-rose-700': event.type === 'tool-error',
              ':uno: border-amber-200 bg-amber-50 text-amber-800':
                event.type === 'tool-approval-request',
            }"
          >
            <div class=":uno: flex items-center gap-1.5 font-medium">
              <span
                class=":uno: h-1.5 w-1.5 rounded-full"
                :class="{
                  ':uno: bg-slate-400': event.type === 'tool-call',
                  ':uno: bg-emerald-500': event.type === 'tool-result',
                  ':uno: bg-rose-500': event.type === 'tool-error',
                  ':uno: bg-amber-500': event.type === 'tool-approval-request',
                }"
              />
              {{
                event.type === 'tool-call'
                  ? '工具调用'
                  : event.type === 'tool-result'
                    ? '工具结果'
                    : event.type === 'tool-error'
                      ? '工具错误'
                      : event.approvalStatus === 'approved'
                        ? '已批准'
                        : event.approvalStatus === 'denied'
                          ? '已拒绝'
                          : '等待审批'
              }}
              <span v-if="event.toolName" class=":uno: font-mono opacity-70">
                {{ event.toolName }}
              </span>
            </div>
            <div v-if="event.summary" class=":uno: mt-1 break-all opacity-80">
              {{ event.summary }}
            </div>
            <div
              v-if="
                event.type === 'tool-approval-request' &&
                event.approvalStatus === 'pending' &&
                event.continuationSupported !== false
              "
              class=":uno: mt-2 flex items-center gap-1.5"
            >
              <button
                type="button"
                class=":uno: rounded-md bg-emerald-600 text-[11px] text-white font-medium shadow-sm hover:bg-emerald-700 !px-2 !py-1"
                @click="handleToolApproval(event.id, true)"
              >
                批准执行
              </button>
              <button
                type="button"
                class=":uno: border border-rose-200 rounded-md bg-white text-[11px] text-rose-600 font-medium shadow-sm hover:bg-rose-50 !px-2 !py-1"
                @click="handleToolApproval(event.id, false)"
              >
                拒绝
              </button>
            </div>
            <div
              v-if="
                event.type === 'tool-call' &&
                event.externalStatus === 'pending' &&
                event.continuationSupported !== false
              "
              class=":uno: mt-2 space-y-2"
            >
              <textarea
                :value="externalResultInput(event.id)"
                rows="2"
                class=":uno: w-full resize-y border border-slate-200 rounded-md bg-white text-[11px] text-slate-700 font-mono outline-none focus:border-teal-400 !px-2 !py-1.5 focus:ring-2 focus:ring-teal-500/10"
                @input="
                  handleExternalResultInput(event.id, ($event.target as HTMLTextAreaElement).value)
                "
              />
              <div class=":uno: flex flex-wrap items-center gap-1.5">
                <button
                  type="button"
                  class=":uno: rounded-md bg-emerald-600 text-[11px] text-white font-medium shadow-sm hover:bg-emerald-700 !px-2 !py-1"
                  @click="handleExternalToolResult(event.id)"
                >
                  提交结果并继续
                </button>
                <input
                  :value="externalErrorInput(event.id)"
                  class=":uno: h-7 w-44 flex-none text-[11px] text-rose-700 outline-none sm:w-56 !border !border-rose-200 !rounded-md !border-solid !bg-white !px-2 !py-1 focus:!border-rose-300 focus:!ring-2 focus:!ring-rose-500/10"
                  @input="
                    handleExternalErrorInput(event.id, ($event.target as HTMLInputElement).value)
                  "
                />
                <button
                  type="button"
                  class=":uno: border border-rose-200 rounded-md bg-white text-[11px] text-rose-600 font-medium shadow-sm hover:bg-rose-50 !px-2 !py-1"
                  @click="handleExternalToolError(event.id)"
                >
                  提交错误
                </button>
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="message.role === 'assistant' && message.warnings?.length"
          class=":uno: mb-2.5 border border-amber-200 rounded-md bg-amber-50 px-3 py-2"
        >
          <div class=":uno: flex items-center gap-1.5 text-xs text-amber-800 font-medium">
            <svg
              class=":uno: size-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
              />
            </svg>
            Warnings
          </div>
          <ul class=":uno: mt-1 text-xs text-amber-700 space-y-0.5">
            <li v-for="warning in message.warnings" :key="`${warning.code}-${warning.message}`">
              <span class=":uno: font-mono">{{ warning.code }}</span>
              <span v-if="warning.message">: {{ warning.message }}</span>
            </li>
          </ul>
        </div>

        <div v-if="message.role === 'user'" class=":uno: whitespace-pre-wrap">
          {{ message.content }}
        </div>
        <div
          v-else
          class=":uno: ai-markdown break-words"
          :class="{ ':uno: text-slate-400': !message.content && message.state === 'streaming' }"
          v-html="
            renderMarkdown(message.content || (message.state === 'streaming' ? '思考中...' : ''))
          "
        />

        <span
          v-if="message.role === 'assistant' && message.state === 'streaming'"
          class=":uno: ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-teal-500 align-middle"
        />
      </div>

      <div
        v-if="message.role === 'assistant' && message.state !== 'streaming'"
        class=":uno: mt-1 flex items-center gap-0.5 opacity-0 transition-opacity duration-150 group-hover:opacity-100"
      >
        <button
          type="button"
          class=":uno: inline-flex items-center gap-1 rounded-md text-xs text-slate-500 hover:bg-white !px-2 !py-1 hover:text-slate-800"
          @click="handleCopy"
        >
          <RiCheckLine v-if="copied" class=":uno: h-3 w-3 text-green-600" />
          <RiFileCopyLine v-else class=":uno: h-3 w-3" />
          {{ copied ? '已复制' : '复制' }}
        </button>
        <button
          v-if="index > 0"
          type="button"
          class=":uno: inline-flex items-center gap-1 rounded-md text-xs text-slate-500 hover:bg-white !px-2 !py-1 hover:text-slate-800"
          @click="handleRegenerate"
        >
          <RiRestartLine class=":uno: h-3 w-3" />
          重新生成
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-markdown :deep(*) {
  max-width: 100%;
}

.ai-markdown :deep(p) {
  margin: 0.375rem 0;
}

.ai-markdown :deep(p:first-child) {
  margin-top: 0;
}

.ai-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-markdown :deep(ul),
.ai-markdown :deep(ol) {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
}

.ai-markdown :deep(li) {
  margin: 0.125rem 0;
}

.ai-markdown :deep(li > p) {
  margin: 0;
}

.ai-markdown :deep(pre) {
  margin: 0.5rem 0;
  overflow-x: auto;
  border-radius: 0.5rem;
  background: #1a1a1a;
  padding: 0.875rem;
  color: #e5e7eb;
}

.ai-markdown :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: 0.8125rem;
  color: #e5e5e5;
}

.ai-markdown :deep(code) {
  border-radius: 0.25rem;
  background: #f3f4f6;
  padding: 0.125rem 0.375rem;
  font-size: 0.875em;
  color: #374151;
}

.ai-markdown :deep(blockquote) {
  margin: 0.5rem 0;
  border-left: 2px solid #d1d5db;
  padding-left: 0.875rem;
  color: #525252;
}

.ai-markdown :deep(a) {
  color: #525252;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ai-markdown :deep(a:hover) {
  color: #171717;
}

.ai-markdown :deep(table) {
  margin: 0.5rem 0;
  border-collapse: collapse;
  width: 100%;
  font-size: 0.8125rem;
}

.ai-markdown :deep(th),
.ai-markdown :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 0.5rem 0.75rem;
  text-align: left;
}

.ai-markdown :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}

.ai-markdown :deep(hr) {
  margin: 0.75rem 0;
  border: none;
  border-top: 1px solid #e5e7eb;
}

.ai-markdown :deep(h1),
.ai-markdown :deep(h2),
.ai-markdown :deep(h3),
.ai-markdown :deep(h4) {
  margin: 0.75rem 0 0.375rem;
  font-weight: 600;
  color: #171717;
}

.ai-markdown :deep(h1) {
  font-size: 1.125rem;
}

.ai-markdown :deep(h2) {
  font-size: 1rem;
}

.ai-markdown :deep(h3) {
  font-size: 0.9375rem;
}

.ai-markdown :deep(h4) {
  font-size: 0.875rem;
}

.border-slate-800.bg-slate-950 .ai-markdown :deep(a) {
  color: #99f6e4;
}

.border-slate-800.bg-slate-950 .ai-markdown :deep(code) {
  background: rgba(255, 255, 255, 0.12);
  color: #f8fafc;
}

.border-slate-800.bg-slate-950 .ai-markdown :deep(pre) {
  background: #020617;
}

.border-slate-800.bg-slate-950 .ai-markdown :deep(blockquote) {
  border-left-color: rgba(255, 255, 255, 0.28);
  color: #cbd5e1;
}
</style>
