<script lang="ts" setup>
import type { ModelOption, TestRagSource } from '@/api/generated'
import type { WorkbenchMessage } from '@/utils/model-test-workbench'
import ChatMessageItem from '@/views/components/workbench/ChatMessageItem.vue'
import { VButton, VTag } from '@halo-dev/components'
import RiAddLine from '~icons/ri/add-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'

defineProps<{
  query: string
  sources: TestRagSource[]
  rerankModelName?: string
  rerankModels: ModelOption[]
  topN?: number
  providerOptionsText: string
  providerOptionsError?: string
  rerankProviderOptionsText: string
  rerankProviderOptionsError?: string
  messages: WorkbenchMessage[]
  error?: string
  isLoading: boolean
  disabled: boolean
}>()

defineEmits<{
  (e: 'update:query', value: string): void
  (e: 'update:sources', value: TestRagSource[]): void
  (e: 'update:rerankModelName', value: string | undefined): void
  (e: 'update:topN', value: number | undefined): void
  (e: 'update:providerOptionsText', value: string): void
  (e: 'update:rerankProviderOptionsText', value: string): void
  (e: 'run'): void
  (e: 'clear'): void
}>()

function patchSource(sources: TestRagSource[], index: number, patch: Partial<TestRagSource>) {
  return sources.map((source, i) => (i === index ? { ...source, ...patch } : source))
}

function removeSource(sources: TestRagSource[], index: number) {
  return sources.filter((_, i) => i !== index)
}

function addSource(sources: TestRagSource[]) {
  const index = sources.length + 1
  return [
    ...sources,
    {
      id: `source-${index}`,
      title: `Source ${index}`,
      content: '',
      visible: true,
      usedForContext: true,
    },
  ]
}
</script>

<template>
  <div class=":uno: min-h-0 flex-1 overflow-y-auto bg-[#f8fafc] px-4 py-5">
    <div class=":uno: mx-auto max-w-5xl space-y-4">
      <div class=":uno: grid gap-3 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <label class=":uno: text-xs text-slate-600 font-medium">Query</label>
          <textarea
            :value="query"
            rows="4"
            placeholder="输入本次 RAG 测试的问题..."
            class=":uno: mt-2 w-full resize-none text-sm text-slate-900 leading-relaxed outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
            :disabled="disabled || isLoading"
            @input="$emit('update:query', ($event.target as HTMLTextAreaElement).value)"
          />
        </div>

        <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4 space-y-3">
          <label class=":uno: text-xs text-slate-600 font-medium">Rerank</label>
          <select
            :value="rerankModelName || ''"
            class=":uno: h-9 w-full text-sm text-slate-800 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
            :disabled="disabled || isLoading"
            @change="
              $emit(
                'update:rerankModelName',
                ($event.target as HTMLSelectElement).value || undefined,
              )
            "
          >
            <option value="">不使用 Rerank</option>
            <option v-for="model in rerankModels" :key="model.name" :value="model.name">
              {{ model.displayName || model.modelId || model.name }}
            </option>
          </select>
          <label class=":uno: text-xs text-slate-600 font-medium">Top Count</label>
          <input
            :value="topN ?? ''"
            type="number"
            min="1"
            class=":uno: h-9 w-full text-sm outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
            :disabled="disabled || isLoading"
            @input="
              $emit(
                'update:topN',
                ($event.target as HTMLInputElement).value
                  ? Number(($event.target as HTMLInputElement).value)
                  : undefined,
              )
            "
          />
        </div>
      </div>

      <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <div class=":uno: mb-3 flex items-center justify-between gap-3">
          <div>
            <div class=":uno: text-sm text-slate-950 font-semibold">Manual Sources</div>
            <div class=":uno: text-xs text-slate-500">{{ sources.length }} 个候选来源</div>
          </div>
          <VButton size="sm" :disabled="disabled || isLoading" @click="$emit('update:sources', addSource(sources))">
            <template #icon><RiAddLine /></template>
            添加
          </VButton>
        </div>

        <div class=":uno: space-y-3">
          <div
            v-for="(source, index) in sources"
            :key="`${source.id || 'source'}-${index}`"
            class=":uno: border border-slate-100 rounded-lg bg-slate-50/70 !p-3"
          >
            <div class=":uno: grid gap-2 md:grid-cols-[9rem_minmax(0,1fr)_minmax(0,1fr)_auto]">
              <input
                :value="source.id"
                placeholder="id"
                class=":uno: h-8 text-xs outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2"
                :disabled="disabled || isLoading"
                @input="
                  $emit(
                    'update:sources',
                    patchSource(sources, index, {
                      id: ($event.target as HTMLInputElement).value,
                    }),
                  )
                "
              />
              <input
                :value="source.title"
                placeholder="标题"
                class=":uno: h-8 text-xs outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2"
                :disabled="disabled || isLoading"
                @input="
                  $emit(
                    'update:sources',
                    patchSource(sources, index, {
                      title: ($event.target as HTMLInputElement).value,
                    }),
                  )
                "
              />
              <input
                :value="source.url"
                placeholder="URL"
                class=":uno: h-8 text-xs outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2"
                :disabled="disabled || isLoading"
                @input="
                  $emit(
                    'update:sources',
                    patchSource(sources, index, {
                      url: ($event.target as HTMLInputElement).value,
                    }),
                  )
                "
              />
              <button
                type="button"
                class=":uno: size-8 inline-flex items-center justify-center border border-rose-200 rounded-md bg-white text-rose-500 hover:bg-rose-50 disabled:opacity-40"
                :disabled="disabled || isLoading || sources.length <= 1"
                @click="$emit('update:sources', removeSource(sources, index))"
              >
                <RiDeleteBinLine class=":uno: size-3.5" />
              </button>
            </div>
            <textarea
              :value="source.content"
              rows="4"
              placeholder="来源内容..."
              class=":uno: mt-2 w-full resize-y text-sm text-slate-800 leading-relaxed outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2"
              :disabled="disabled || isLoading"
              @input="
                $emit(
                  'update:sources',
                  patchSource(sources, index, {
                    content: ($event.target as HTMLTextAreaElement).value,
                  }),
                )
              "
            />
          </div>
        </div>
      </div>

      <details class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <summary class=":uno: cursor-pointer select-none text-sm text-slate-800 font-semibold">
          Provider Options
        </summary>
        <div class=":uno: mt-3 grid gap-3 lg:grid-cols-2">
          <div>
            <label class=":uno: text-xs text-slate-500">Language Provider Options</label>
            <textarea
              :value="providerOptionsText"
              rows="5"
              class=":uno: mt-1 w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs"
              :class="{ ':uno: !border-rose-300': providerOptionsError }"
              :disabled="disabled || isLoading"
              @input="$emit('update:providerOptionsText', ($event.target as HTMLTextAreaElement).value)"
            />
            <div class=":uno: mt-1 text-[10px] text-rose-500">{{ providerOptionsError }}</div>
          </div>
          <div>
            <label class=":uno: text-xs text-slate-500">Rerank Provider Options</label>
            <textarea
              :value="rerankProviderOptionsText"
              rows="5"
              class=":uno: mt-1 w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs"
              :class="{ ':uno: !border-rose-300': rerankProviderOptionsError }"
              :disabled="disabled || isLoading"
              @input="
                $emit('update:rerankProviderOptionsText', ($event.target as HTMLTextAreaElement).value)
              "
            />
            <div class=":uno: mt-1 text-[10px] text-rose-500">
              {{ rerankProviderOptionsError }}
            </div>
          </div>
        </div>
      </details>

      <div class=":uno: flex items-center justify-end gap-2">
        <VButton :disabled="isLoading" @click="$emit('clear')">清空结果</VButton>
        <VButton
          type="primary"
          :loading="isLoading"
          :disabled="disabled || !query.trim() || !sources.some((source) => source.content?.trim())"
          @click="$emit('run')"
        >
          <template #icon><RiSendPlaneLine /></template>
          运行 RAG
        </VButton>
      </div>

      <div
        v-if="error"
        class=":uno: border border-rose-200 rounded-lg bg-rose-50 text-sm text-rose-700 shadow-sm !px-4 !py-3"
      >
        {{ error }}
      </div>

      <div v-if="messages.length" class=":uno: space-y-5">
        <ChatMessageItem
          v-for="(message, index) in messages"
          :key="message.id"
          :message="message"
          :index="index"
        />

        <div
          v-for="message in messages.filter((item) => item.role === 'assistant' && item.ragDiagnostics)"
          :key="`${message.id}-rag-diagnostics`"
          class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4"
        >
          <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
            <span class=":uno: text-sm text-slate-950 font-semibold">RAG Diagnostics</span>
            <VTag size="sm">{{ message.ragDiagnostics?.sourceCount || 0 }} sources</VTag>
            <VTag v-if="message.ragDiagnostics?.events?.length" size="sm">
              {{ message.ragDiagnostics.events.length }} events
            </VTag>
          </div>

          <div class=":uno: grid gap-3 lg:grid-cols-2">
            <div
              v-for="(source, index) in message.ragDiagnostics?.sources || []"
              :key="`${source.id || index}-${index}`"
              class=":uno: border border-slate-100 rounded-lg bg-slate-50/70 !px-3 !py-2.5"
            >
              <div class=":uno: flex items-center justify-between gap-3">
                <span class=":uno: truncate text-xs text-slate-700 font-medium">
                  {{ source.title || source.id || `Source ${index + 1}` }}
                </span>
                <span v-if="source.score !== undefined" class=":uno: text-xs text-teal-700 font-mono">
                  {{ Number(source.score).toFixed(4) }}
                </span>
              </div>
              <a
                v-if="source.url"
                :href="source.url"
                target="_blank"
                rel="noreferrer"
                class=":uno: mt-1 block truncate text-xs text-slate-500 hover:text-teal-700"
              >
                {{ source.url }}
              </a>
              <div
                v-if="source.metadata"
                class=":uno: mt-2 whitespace-pre-wrap break-all rounded-md bg-white text-[11px] text-slate-500 font-mono !px-2 !py-1.5"
              >
                {{ JSON.stringify(source.metadata, null, 2) }}
              </div>
            </div>
          </div>

          <div v-if="message.ragDiagnostics?.events?.length" class=":uno: mt-3 space-y-1.5">
            <div
              v-for="(event, index) in message.ragDiagnostics.events"
              :key="`${event.type}-${index}`"
              class=":uno: flex flex-wrap items-center gap-2 border border-slate-100 rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-600"
            >
              <span class=":uno: text-slate-900 font-medium">{{ event.type }}</span>
              <span v-if="event.sourceCount !== undefined">{{ event.sourceCount }} sources</span>
              <span v-if="event.contextCharacters !== undefined">
                {{ event.contextCharacters }} chars
              </span>
              <span v-if="event.errorMessage" class=":uno: text-rose-600">
                {{ event.errorMessage }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
