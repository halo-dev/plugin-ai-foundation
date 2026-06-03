<script lang="ts" setup>
import type { TestEmbeddingResponse } from '@/api/generated'
import { VTag } from '@halo-dev/components'

defineProps<{
  inputs: string
  dimensions?: number
  maxBatchSize?: number
  maxParallelCalls?: number
  maxRetries?: number
  providerOptionsText?: string
  headersText?: string
  providerOptionsError?: string
  headersError?: string
  result?: TestEmbeddingResponse
  error?: string
  isLoading: boolean
  disabled: boolean
}>()
</script>

<template>
  <div
    class=":uno: min-h-0 flex-1 overflow-y-auto bg-[radial-gradient(circle_at_top_left,rgba(245,158,11,0.10),transparent_28%),linear-gradient(180deg,#f8fafc_0%,#f1f5f9_100%)] px-4 py-5"
  >
    <div class=":uno: mx-auto max-w-4xl space-y-3">
      <div
        v-if="error"
        class=":uno: border border-rose-200 rounded-lg bg-rose-50 text-sm text-rose-700 shadow-sm !px-4 !py-3"
      >
        <div class=":uno: flex items-center gap-2 font-medium">
          <svg
            class=":uno: size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          {{ error }}
        </div>
      </div>

      <div
        v-if="!result && !error"
        class=":uno: border border-slate-200 rounded-lg border-dashed bg-white text-center shadow-sm !px-6 !py-12"
      >
        <div
          class=":uno: mx-auto mb-3 size-12 flex items-center justify-center border border-slate-200 rounded-lg bg-amber-50"
        >
          <svg
            class=":uno: size-5 text-amber-600"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path
              d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z"
            />
          </svg>
        </div>
        <div class=":uno: text-sm text-slate-900 font-semibold">暂无嵌入测试结果</div>
        <div class=":uno: mt-1 text-xs text-slate-500">输入多行文本后点击发送</div>
      </div>

      <div v-if="result" class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <div class=":uno: flex flex-wrap items-center gap-2">
          <span class=":uno: text-sm text-slate-950 font-semibold">结果</span>
          <VTag size="sm">{{ result.embeddingsCount }} 个向量</VTag>
          <VTag v-if="result.usage?.tokens !== undefined" size="sm">
            {{ result.usage.tokens }} tokens
          </VTag>
          <VTag v-if="result.response?.model" size="sm">
            {{ result.response.model }}
          </VTag>
        </div>

        <div
          v-if="result.firstPairSimilarity !== undefined"
          class=":uno: mt-3 border border-slate-100 rounded-lg bg-slate-50 text-sm !px-4 !py-3"
        >
          <div class=":uno: flex items-center justify-between">
            <span class=":uno: text-slate-600">前两个输入的 cosine similarity</span>
            <span
              class=":uno: text-base font-bold font-mono"
              :class="
                result.firstPairSimilarity > 0.8
                  ? ':uno: text-green-600'
                  : result.firstPairSimilarity > 0.5
                    ? ':uno: text-amber-600'
                    : ':uno: text-rose-600'
              "
            >
              {{ result.firstPairSimilarity.toFixed(4) }}
            </span>
          </div>
          <div class=":uno: mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
            <div
              class=":uno: h-full rounded-full transition-all"
              :class="
                result.firstPairSimilarity > 0.8
                  ? ':uno: bg-green-500'
                  : result.firstPairSimilarity > 0.5
                    ? ':uno: bg-amber-500'
                    : ':uno: bg-rose-500'
              "
              :style="{ width: `${Math.min(result.firstPairSimilarity * 100, 100)}%` }"
            />
          </div>
        </div>

        <div class=":uno: mt-3 space-y-2">
          <div
            v-for="item in result.embeddings"
            :key="item.index"
            class=":uno: border border-slate-100 rounded-lg bg-slate-50/70 transition-colors hover:bg-slate-50 !px-3 !py-2.5"
          >
            <div class=":uno: flex items-center justify-between">
              <span class=":uno: text-xs text-slate-500 font-medium font-mono"
                >#{{ (item.index ?? 0) + 1 }}</span
              >
              <span
                class=":uno: border border-slate-200 rounded-md bg-white text-xs text-slate-600 font-mono !px-1.5 !py-0.5"
                >{{ item.dimensions }} 维</span
              >
            </div>
            <div
              class=":uno: mt-1.5 break-all border border-slate-800 rounded-md bg-slate-950 text-xs text-slate-300 font-mono !px-3 !py-2"
            >
              [{{ (item.preview || []).map((value) => Number(value).toFixed(4)).join(', ') }}]
            </div>
          </div>
        </div>

        <div
          v-if="result.warnings?.length"
          class=":uno: mt-3 border border-amber-200 rounded-lg bg-amber-50 !px-3 !py-2"
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
            <li v-for="warning in result.warnings" :key="`${warning.code}-${warning.message}`">
              <span class=":uno: font-mono">{{ warning.code }}</span>
              <span v-if="warning.message">: {{ warning.message }}</span>
            </li>
          </ul>
        </div>

        <details class=":uno: mt-3 text-xs text-slate-600">
          <summary class=":uno: cursor-pointer select-none font-medium">诊断信息</summary>
          <pre
            class=":uno: mt-2 overflow-auto rounded-md bg-slate-950 text-xs text-slate-100 !p-3"
            >{{
              JSON.stringify(
                {
                  response: result.response,
                  providerMetadata: result.providerMetadata,
                  usage: result.usage,
                },
                null,
                2,
              )
            }}</pre
          >
        </details>
      </div>
    </div>
  </div>
</template>
