<script setup lang="ts">
import { DecisionAnswerTypeEnum, type DecisionAnswer } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { computed } from 'vue'
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'

const props = defineProps<{ answer: DecisionAnswer; question?: DecisionQuestionDraft }>()

const percent = (value: number) => `${(value * 100).toFixed(1)}%`

const scoreLevels = computed(() => {
  if (props.answer.type !== DecisionAnswerTypeEnum.Score) return undefined
  if (props.question?.type === DecisionAnswerTypeEnum.Score && props.question.levels.length >= 2) {
    return props.question.levels.map((level) => level.trim())
  }

  const legend = props.answer.legend
  if (!legend) return undefined
  const keys = Object.keys(legend).sort((left, right) => Number(left) - Number(right))
  if (keys.length < 2 || keys.some((key, index) => key !== String(index))) return undefined
  return keys.map((key) => legend[key])
})

function submittedOption(key: string) {
  if (props.question?.type !== DecisionAnswerTypeEnum.Choice) return undefined
  return props.question.options.find((option) => option.key.trim() === key)
}

const choiceLabel = computed(() => {
  const choice = props.answer.choice
  if (choice == null) return '未返回结果'
  return submittedOption(choice)?.label.trim() || choice
})

function distributionLabel(key: string): string {
  const option = submittedOption(key)
  if (option) return option.label.trim() || key
  return scoreLevels.value?.[Number(key)] || props.answer.legend?.[key] || key
}

const distribution = computed(() =>
  Object.entries(props.answer.probabilities ?? {})
    .map(([key, probability]) => ({ key, probability, label: distributionLabel(key) }))
    .sort((left, right) => right.probability - left.probability),
)
const legendEntries = computed(() => Object.entries(props.answer.legend ?? {}))
const hasDetails = computed(() => props.answer.confidence != null || legendEntries.value.length > 0)
</script>

<template>
  <div class=":uno: decision-result-detail min-w-0 space-y-6">
    <div v-if="answer.type === DecisionAnswerTypeEnum.YesNo" class=":uno: space-y-3">
      <p class=":uno: text-sm text-slate-500">“是”的概率</p>
      <p class=":uno: text-2xl text-slate-950 font-semibold tabular-nums">
        {{ answer.probability == null ? '未知' : percent(answer.probability) }}
      </p>
      <meter
        v-if="answer.probability != null"
        class=":uno: decision-probability"
        min="0"
        max="1"
        :value="answer.probability"
        aria-label="是的概率"
      />
    </div>
    <p
      v-else-if="answer.type === DecisionAnswerTypeEnum.Choice"
      aria-label="选择结果"
      class=":uno: break-words text-2xl text-slate-950 font-semibold"
    >
      {{ choiceLabel }}
    </p>
    <div v-else-if="answer.type === DecisionAnswerTypeEnum.Score" class=":uno: space-y-3">
      <p class=":uno: text-sm text-slate-500">评分</p>
      <p aria-label="评分结果" class=":uno: text-2xl text-slate-950 font-semibold tabular-nums">
        {{ answer.score ?? '未知' }}
        <span v-if="scoreLevels" class=":uno: text-base text-slate-500 font-normal">
          / {{ scoreLevels.length - 1 }}
        </span>
      </p>
      <template v-if="scoreLevels && answer.score != null">
        <meter
          class=":uno: decision-probability"
          min="0"
          :max="scoreLevels.length - 1"
          :value="answer.score"
          aria-label="评分刻度"
        />
        <div class=":uno: flex justify-between gap-4 text-sm text-slate-500">
          <span class=":uno: min-w-0 break-words">0 · {{ scoreLevels[0] }}</span>
          <span class=":uno: min-w-0 break-words text-right">
            {{ scoreLevels.length - 1 }} · {{ scoreLevels[scoreLevels.length - 1] }}
          </span>
        </div>
      </template>
    </div>

    <dl v-if="distribution.length" aria-label="概率分布" class=":uno: space-y-4">
      <div v-for="item in distribution" :key="item.key" class=":uno: space-y-2">
        <div class=":uno: flex justify-between gap-4 text-sm">
          <dt class=":uno: min-w-0 break-words text-slate-700">{{ item.label }}</dt>
          <dd class=":uno: shrink-0 text-slate-900 tabular-nums">
            {{ percent(item.probability) }}
          </dd>
        </div>
        <meter
          class=":uno: decision-probability"
          min="0"
          max="1"
          :value="item.probability"
          :aria-label="`${item.label}的概率`"
        />
      </div>
    </dl>

    <details
      v-if="hasDetails"
      class=":uno: decision-result-diagnostics border-t border-slate-200 pt-3 text-sm"
    >
      <summary
        class=":uno: flex cursor-pointer items-center gap-2 text-slate-700 hover:text-slate-950"
      >
        <RiArrowRightSLine class=":uno: size-4 shrink-0" aria-hidden="true" />
        展开详情
      </summary>
      <div class=":uno: mt-4 space-y-4">
        <div v-if="answer.confidence != null" class=":uno: space-y-1">
          <dl class=":uno: flex justify-between gap-4">
            <dt class=":uno: text-slate-600">置信度</dt>
            <dd class=":uno: text-slate-900 tabular-nums">{{ percent(answer.confidence) }}</dd>
          </dl>
          <p class=":uno: text-slate-500">含义由提供商定义，不等于正确率。</p>
        </div>
        <dl v-if="legendEntries.length" aria-label="返回图例" class=":uno: space-y-2">
          <div v-for="[key, label] in legendEntries" :key="key" class=":uno: flex gap-3">
            <dt class=":uno: min-w-0 break-words text-slate-700 tabular-nums">{{ key }}</dt>
            <dd class=":uno: min-w-0 break-words text-slate-500">{{ label }}</dd>
          </div>
        </dl>
      </div>
    </details>
  </div>
</template>

<style scoped>
.decision-probability {
  display: block;
  width: 100%;
  height: 0.375rem;
  overflow: hidden;
  appearance: none;
  border: 0;
  border-radius: 999px;
  background: #f1f5f9;
}

.decision-probability::-webkit-meter-bar {
  height: 0.375rem;
  border: 0;
  border-radius: 999px;
  background: #f1f5f9;
}

.decision-probability::-webkit-meter-optimum-value {
  border-radius: 999px;
  background: #4b9f96;
}

.decision-probability::-moz-meter-bar {
  border-radius: 999px;
  background: #4b9f96;
}

.decision-result-diagnostics > summary {
  list-style: none;
}

.decision-result-diagnostics > summary::-webkit-details-marker {
  display: none;
}

.decision-result-diagnostics[open] > summary > svg {
  transform: rotate(90deg);
}
</style>