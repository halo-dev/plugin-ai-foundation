<script setup lang="ts">
import { DecisionAnswerTypeEnum, type DecisionResponse } from '@/api/generated'
import { VTag } from '@halo-dev/components'

defineProps<{ result: DecisionResponse }>()
const percent = (value: number) => `${(value * 100).toFixed(1)}%`
</script>

<template>
  <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
    <div class=":uno: flex flex-wrap items-center gap-2">
      <span class=":uno: text-sm text-slate-950 font-semibold">决策结果</span>
      <VTag size="sm">{{ Object.keys(result.answers || {}).length }} 个结果</VTag>
      <VTag size="sm" class=":uno: min-w-0 break-all">{{ result.model }}</VTag>
      <span class=":uno: text-xs text-slate-500">
        输入 {{ result.usage?.inputTokens ?? '未知' }} tokens · 输出
        {{ result.usage?.outputTokens ?? '未知' }} tokens
      </span>
    </div>
    <div class=":uno: grid mt-3 gap-3 sm:grid-cols-2">
      <div
        v-for="(answer, id) in result.answers"
        :key="id"
        class=":uno: min-w-0 border border-slate-100 rounded-lg bg-slate-50/70 !p-3"
      >
        <div class=":uno: break-all text-xs text-slate-500 font-medium">{{ id }}</div>
        <div
          v-if="answer.type === DecisionAnswerTypeEnum.YesNo"
          class=":uno: mt-2 text-sm text-slate-900 font-semibold tabular-nums"
        >
          “是”的概率：{{ answer.probability == null ? '未知' : percent(answer.probability) }}
        </div>
        <template v-else>
          <div class=":uno: mt-2 break-all text-sm text-slate-900 font-semibold tabular-nums">
            {{
              answer.type === DecisionAnswerTypeEnum.Choice
                ? `选择：${answer.choice}`
                : `评分：${answer.score}`
            }}
          </div>
          <div
            v-if="answer.confidence != null"
            class=":uno: mt-1 text-xs text-slate-500 leading-relaxed"
          >
            置信度：{{ percent(answer.confidence) }}（含义由提供商定义，不等于正确率）
          </div>
          <dl
            v-if="answer.probabilities != null"
            class=":uno: mt-3 border-t border-slate-200 pt-2 space-y-1"
          >
            <div
              v-for="(probability, option) in answer.probabilities"
              :key="option"
              class=":uno: flex justify-between gap-4 text-sm"
            >
              <dt class=":uno: min-w-0 break-all text-slate-600">
                {{ answer.legend?.[option] || option }}
              </dt>
              <dd class=":uno: shrink-0 text-slate-700 tabular-nums">{{ percent(probability) }}</dd>
            </div>
          </dl>
          <dl
            v-else-if="answer.legend != null"
            class=":uno: mt-3 border-t border-slate-200 pt-2 space-y-1"
          >
            <div
              v-for="(label, level) in answer.legend"
              :key="level"
              class=":uno: flex gap-2 text-sm"
            >
              <dt class=":uno: shrink-0 text-slate-700 tabular-nums">{{ level }}</dt>
              <dd class=":uno: min-w-0 break-all text-slate-600">{{ label }}</dd>
            </div>
          </dl>
        </template>
      </div>
    </div>
  </div>
</template>
