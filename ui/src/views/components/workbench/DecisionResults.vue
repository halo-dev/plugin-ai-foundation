<script setup lang="ts">
import { DecisionAnswerTypeEnum, type DecisionResponse } from '@/api/generated'

defineProps<{ result: DecisionResponse }>()
const percent = (value: number) => `${(value * 100).toFixed(1)}%`
</script>

<template>
  <div class=":uno: border border-slate-200 rounded-lg bg-white p-4">
    <div class=":uno: text-sm font-semibold">决策结果</div>
    <div class=":uno: mt-1 break-all text-xs text-slate-500">
      {{ result.model }} · 输入 {{ result.usage?.inputTokens ?? '未知' }} tokens · 输出
      {{ result.usage?.outputTokens ?? '未知' }} tokens
    </div>
    <div
      v-for="(answer, id) in result.answers"
      :key="id"
      class=":uno: mt-4 border-t border-slate-100 pt-3"
    >
      <div class=":uno: break-all text-sm font-medium">{{ id }}</div>
      <div v-if="answer.type === DecisionAnswerTypeEnum.YesNo" class=":uno: mt-2 text-sm">
        “是”的概率：{{ answer.probability == null ? '未知' : percent(answer.probability) }}
      </div>
      <template v-else>
        <div class=":uno: mt-2 break-all text-sm">
          {{
            answer.type === DecisionAnswerTypeEnum.Choice
              ? `选择：${answer.choice}`
              : `评分：${answer.score}`
          }}
        </div>
        <div v-if="answer.confidence != null" class=":uno: mt-1 text-xs text-slate-500">
          置信度：{{ percent(answer.confidence) }}（反映分布的确定程度，不等于正确率）
        </div>
        <dl class=":uno: mt-2 space-y-1">
          <div
            v-for="(probability, option) in answer.probabilities"
            :key="option"
            class=":uno: flex justify-between gap-4 text-sm"
          >
            <dt class=":uno: min-w-0 break-all text-slate-600">
              {{ answer.legend?.[option] || option }}
            </dt>
            <dd class=":uno: shrink-0 tabular-nums">{{ percent(probability) }}</dd>
          </div>
        </dl>
      </template>
    </div>
  </div>
</template>
