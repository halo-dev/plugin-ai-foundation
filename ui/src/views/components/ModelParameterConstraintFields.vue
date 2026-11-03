<script setup lang="ts">
import type { AiModelSpecModelTypeEnum, ModelParameterConstraints } from '@/api/generated'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ inherited?: ModelParameterConstraints; modelType?: AiModelSpecModelTypeEnum }>(),
  { modelType: 'language' },
)
const constraints = defineModel<ModelParameterConstraints>()
const allTokenFields = [
  { key: 'contextTokens', label: '上下文 Token 上限' },
  { key: 'inputTokens', label: '输入 Token 上限' },
  { key: 'outputTokens', label: '输出 Token 上限' },
] as const
const tokenFields = computed(() =>
  allTokenFields.filter((field) => props.modelType === 'language' || field.key !== 'outputTokens'),
)
const reasoningNumbers = [
  { key: 'budgetMinimum', label: '推理预算下限（Token）' },
  { key: 'budgetMaximum', label: '推理预算上限（Token）' },
] as const
type ReasoningBudgetField = (typeof reasoningNumbers)[number]['key']

function update<Key extends keyof ModelParameterConstraints>(
  key: Key,
  value: ModelParameterConstraints[Key],
) {
  const next = { ...constraints.value, [key]: value }
  if (value === undefined) delete next[key]
  constraints.value = Object.keys(next).length ? next : undefined
}
function updateReasoning(key: ReasoningBudgetField, value: number | undefined) {
  const next = { ...constraints.value?.reasoning, [key]: value }
  if (value === undefined) delete next[key]
  update('reasoning', Object.keys(next).length ? next : undefined)
}
function numberValue(value: unknown) {
  return value === '' || value == null ? undefined : Number(value)
}
function inheritedNumber(value?: number) {
  return value === undefined ? '自动（未声明）' : `models.dev：${value}`
}
</script>

<template>
  <div class=":uno: grid gap-4 sm:grid-cols-2">
    <div v-for="field in tokenFields" :key="field.key">
      <FormKit
        type="number"
        :name="`constraint_${field.key}`"
        :label="field.label"
        min="1"
        step="1"
        validation="min:1|number"
        :placeholder="inheritedNumber(inherited?.[field.key])"
        :model-value="constraints?.[field.key]"
        @input="update(field.key, numberValue($event))"
      />
    </div>
    <template v-if="modelType === 'language'">
      <div v-for="field in reasoningNumbers" :key="field.key">
        <FormKit
          type="number"
          :name="`constraint_reasoning_${field.key}`"
          :label="field.label"
          min="0"
          step="1"
          validation="min:0|number"
          :placeholder="inheritedNumber(inherited?.reasoning?.[field.key])"
          :model-value="constraints?.reasoning?.[field.key]"
          @input="updateReasoning(field.key, numberValue($event))"
        />
      </div>
    </template>
  </div>
</template>