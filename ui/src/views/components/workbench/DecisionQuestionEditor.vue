<script setup lang="ts">
import { DecisionQuestionTypeEnum } from '@/api/generated'
import { DECISION_QUESTION_TYPES, type DecisionQuestionDraft } from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'

const props = defineProps<{ question: DecisionQuestionDraft; index: number; disabled: boolean }>()
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
  <div class=":uno: min-w-0 border border-slate-100 rounded-lg bg-slate-50/70 space-y-3 !p-3">
    <div class=":uno: flex items-center justify-between gap-3">
      <span class=":uno: text-xs text-slate-500 font-medium">问题 {{ index + 1 }}</span>
      <VButton
        size="sm"
        ghost
        :disabled="disabled"
        :aria-label="`移除问题 ${index + 1}`"
        @click="emit('remove')"
      >
        <template #icon><RiDeleteBinLine /></template>
        移除
      </VButton>
    </div>
    <div class=":uno: grid grid-cols-1 gap-3 sm:grid-cols-2">
      <FormKit
        type="text"
        label="问题标识"
        placeholder="例如 spam"
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
      :rows="3"
      placeholder="描述需要模型判断的问题…"
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
  </div>
</template>
