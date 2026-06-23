<script lang="ts" setup>
import type { TestRerankResponse } from '@/api/generated'
import { VButton, VTag } from '@halo-dev/components'
import RiSendPlaneLine from '~icons/ri/send-plane-line'

defineProps<{
  query: string
  documents: string
  providerOptionsText: string
  providerOptionsError?: string
  result?: TestRerankResponse
  error?: string
  isLoading: boolean
  disabled: boolean
}>()

defineEmits<{
  (e: 'update:query', value: string): void
  (e: 'update:documents', value: string): void
  (e: 'update:providerOptionsText', value: string): void
  (e: 'run'): void
}>()

const providerOptionsHint = '例如 {"cohere":{"truncate":"END"}}'
</script>

<template>
  <div
    class=":uno: min-h-0 flex-1 overflow-y-auto bg-[radial-gradient(circle_at_top_left,rgba(99,102,241,0.10),transparent_28%),linear-gradient(180deg,#f8fafc_0%,#f1f5f9_100%)] px-4 py-5"
  >
    <div class=":uno: mx-auto max-w-4xl space-y-3">
      <div class=":uno: grid gap-3 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <label class=":uno: text-xs text-slate-600 font-medium">Query</label>
          <textarea
            :value="query"
            rows="5"
            placeholder="输入用于排序候选文档的问题..."
            class=":uno: mt-2 w-full resize-none text-sm text-slate-900 leading-relaxed outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 placeholder:text-slate-400 focus:!border-indigo-400 focus:!ring-3 focus:!ring-indigo-500/10"
            :disabled="disabled || isLoading"
            @input="$emit('update:query', ($event.target as HTMLTextAreaElement).value)"
          />
        </div>

        <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <label class=":uno: text-xs text-slate-600 font-medium">Documents</label>
          <textarea
            :value="documents"
            rows="5"
            placeholder="每行一个候选文档..."
            class=":uno: mt-2 w-full resize-none text-sm text-slate-900 leading-relaxed outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 placeholder:text-slate-400 focus:!border-indigo-400 focus:!ring-3 focus:!ring-indigo-500/10"
            :disabled="disabled || isLoading"
            @input="$emit('update:documents', ($event.target as HTMLTextAreaElement).value)"
          />
        </div>
      </div>

      <details class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <summary class=":uno: cursor-pointer select-none text-sm text-slate-800 font-semibold">
          Provider Options
        </summary>
        <textarea
          :value="providerOptionsText"
          rows="5"
          class=":uno: mt-3 w-full text-slate-700 leading-relaxed font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs focus:!border-indigo-400 focus:!ring-3 focus:!ring-indigo-500/10"
          :class="{ ':uno: !border-rose-300': providerOptionsError }"
          :disabled="disabled || isLoading"
          @input="$emit('update:providerOptionsText', ($event.target as HTMLTextAreaElement).value)"
        />
        <div
          class=":uno: mt-1 text-[10px]"
          :class="providerOptionsError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
        >
          {{ providerOptionsError || providerOptionsHint }}
        </div>
      </details>

      <div class=":uno: flex justify-end">
        <VButton
          type="primary"
          :loading="isLoading"
          :disabled="disabled || !query.trim() || !documents.trim()"
          @click="$emit('run')"
        >
          <template #icon><RiSendPlaneLine /></template>
          Rerank
        </VButton>
      </div>

      <div
        v-if="error"
        class=":uno: border border-rose-200 rounded-lg bg-rose-50 text-sm text-rose-700 shadow-sm !px-4 !py-3"
      >
        {{ error }}
      </div>

      <div v-if="result" class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <div class=":uno: flex flex-wrap items-center gap-2">
          <span class=":uno: text-sm text-slate-950 font-semibold">排序结果</span>
          <VTag size="sm">{{ result.resultsCount }} 个结果</VTag>
          <VTag v-if="result.usage?.totalTokens !== undefined" size="sm">
            {{ result.usage.totalTokens }} tokens
          </VTag>
          <VTag v-if="result.response?.model" size="sm">{{ result.response.model }}</VTag>
        </div>

        <div class=":uno: mt-3 space-y-2">
          <div
            v-for="item in result.results || []"
            :key="`${item.index}-${item.documentId || ''}`"
            class=":uno: border border-slate-100 rounded-lg bg-slate-50/70 !px-3 !py-2.5"
          >
            <div class=":uno: flex items-center justify-between gap-3">
              <span class=":uno: text-xs text-slate-500 font-mono">#{{ (item.index ?? 0) + 1 }}</span>
              <span class=":uno: text-xs text-indigo-700 font-mono">
                {{ item.score !== undefined ? Number(item.score).toFixed(4) : 'no score' }}
              </span>
            </div>
            <div class=":uno: mt-1.5 whitespace-pre-wrap text-sm text-slate-800 leading-relaxed">
              {{ item.text }}
            </div>
          </div>
        </div>

        <div
          v-if="result.warnings?.length"
          class=":uno: mt-3 border border-amber-200 rounded-lg bg-amber-50 !px-3 !py-2"
        >
          <div class=":uno: text-xs text-amber-800 font-medium">Warnings</div>
          <ul class=":uno: mt-1 text-xs text-amber-700 space-y-0.5">
            <li v-for="warning in result.warnings" :key="`${warning.code}-${warning.message}`">
              <span class=":uno: font-mono">{{ warning.code }}</span>
              <span v-if="warning.message">: {{ warning.message }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>
