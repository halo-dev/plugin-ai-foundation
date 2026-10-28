<script setup lang="ts">
import { DecisionQuestionTypeEnum } from '@/api/generated'
import { DECISION_QUESTION_TYPES, type DecisionQuestionDraft } from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'

const props = defineProps<{ question: DecisionQuestionDraft; disabled: boolean }>()
const emit = defineEmits<{ update: [question: DecisionQuestionDraft]; remove: [] }>()

function update<K extends keyof DecisionQuestionDraft>(key: K, value: DecisionQuestionDraft[K]) {
  emit('update', { ...props.question, [key]: value })
}
function updateType(value: unknown) {
  if (
    value === DecisionQuestionTypeEnum.YesNo ||
    value === DecisionQuestionTypeEnum.Choice ||
    value === DecisionQuestionTypeEnum.Score
  ) {
    update('type', value)
  }
}
</script>

<template>
  <div class=":uno: border border-slate-200 rounded-lg bg-white p-4">
    <div class=":uno: grid grid-cols-1 gap-3 sm:grid-cols-2">
      <FormKit
        type="text"
        label="问题标识"
        :model-value="question.id"
        :disabled="disabled"
        @update:model-value="update('id', String($event ?? ''))"
      />
      <FormKit
        type="select"
        label="问题类型"
        :options="DECISION_QUESTION_TYPES"
        :model-value="question.type"
        :disabled="disabled"
        @update:model-value="updateType($event)"
      />
    </div>
    <FormKit
      type="textarea"
      label="判断说明"
      :rows="2"
      :model-value="question.instructions"
      :disabled="disabled"
      @update:model-value="update('instructions', String($event ?? ''))"
    />
    <FormKit
      v-if="question.type === DecisionQuestionTypeEnum.Choice"
      type="textarea"
      label="候选选项"
      help="每行一个选项，格式为 标识=说明；也可以直接填写选项文本。"
      :rows="3"
      :model-value="question.criteria"
      :disabled="disabled"
      @update:model-value="update('criteria', String($event ?? ''))"
    />
    <FormKit
      v-else-if="question.type === DecisionQuestionTypeEnum.Score"
      type="textarea"
      label="评分等级"
      help="每行一个等级，按从低到高排列。第一个等级为 0，结果可以是小数。"
      :rows="3"
      :model-value="question.criteria"
      :disabled="disabled"
      @update:model-value="update('criteria', String($event ?? ''))"
    />
    <div class=":uno: flex justify-end">
      <VButton size="sm" :disabled="disabled" @click="emit('remove')">移除问题</VButton>
    </div>
  </div>
</template>
