<script setup lang="ts">
import { DecisionQuestionTypeEnum } from '@/api/generated'
import {
  DECISION_QUESTION_TYPES,
  type DecisionChoiceDraft,
  type DecisionQuestionDraft,
} from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'
import RiAddLine from '~icons/ri/add-line'
import RiCloseLine from '~icons/ri/close-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'

const props = defineProps<{ question: DecisionQuestionDraft; index: number; disabled: boolean }>()
const emit = defineEmits<{ update: [question: DecisionQuestionDraft]; remove: [] }>()

function update<K extends keyof DecisionQuestionDraft>(key: K, value: DecisionQuestionDraft[K]) {
  if (props.disabled) return
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

function updateOption(index: number, field: keyof DecisionChoiceDraft, value: unknown) {
  update(
    'options',
    props.question.options.map((option, position) =>
      position === index ? { ...option, [field]: String(value ?? '') } : option,
    ),
  )
}

function updateLevel(index: number, value: unknown) {
  update(
    'levels',
    props.question.levels.map((level, position) =>
      position === index ? String(value ?? '') : level,
    ),
  )
}
</script>

<template>
  <div class=":uno: decision-question-editor min-w-0 space-y-4">
    <div class=":uno: grid grid-cols-1 gap-4 sm:grid-cols-2">
      <FormKit
        type="text"
        label="问题标识"
        placeholder="例如 sentiment"
        :delay="0"
        :model-value="question.id"
        :disabled="disabled"
        @update:model-value="update('id', String($event ?? ''))"
      />
      <FormKit
        type="select"
        label="问题类型"
        :delay="0"
        :options="DECISION_QUESTION_TYPES"
        :model-value="question.type"
        :disabled="disabled"
        @update:model-value="updateType"
      />
    </div>
    <FormKit
      type="textarea"
      label="判断说明"
      :rows="2"
      :delay="0"
      placeholder="描述需要模型判断的问题…"
      :model-value="question.instructions"
      :disabled="disabled"
      @update:model-value="update('instructions', String($event ?? ''))"
    />

    <fieldset
      v-if="question.type === DecisionQuestionTypeEnum.Choice"
      class=":uno: min-w-0 space-y-2"
    >
      <legend class=":uno: mb-2 text-sm text-slate-700 font-medium">候选选项</legend>
      <div
        v-for="(option, optionIndex) in question.options"
        :key="optionIndex"
        class=":uno: grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)_2.25rem] items-start gap-2"
      >
        <FormKit
          type="text"
          :aria-label="`选项 ${optionIndex + 1} 标识`"
          placeholder="例如 positive"
          :delay="0"
          :model-value="option.key"
          :disabled="disabled"
          @update:model-value="updateOption(optionIndex, 'key', $event)"
        />
        <FormKit
          type="textarea"
          :aria-label="`选项 ${optionIndex + 1} 说明`"
          placeholder="例如 正面"
          :rows="1"
          :classes="{ input: 'decision-description-input' }"
          :delay="0"
          :model-value="option.label"
          :disabled="disabled"
          @update:model-value="updateOption(optionIndex, 'label', $event)"
        />
        <VButton
          ghost
          size="sm"
          class=":uno: decision-row-remove !size-9 !p-0"
          :disabled="disabled"
          :aria-label="`删除选项 ${optionIndex + 1}`"
          title="删除选项"
          @click="
            update(
              'options',
              question.options.filter((_, position) => position !== optionIndex),
            )
          "
        >
          <RiCloseLine class=":uno: size-4" aria-hidden="true" />
        </VButton>
      </div>
      <VButton
        ghost
        size="sm"
        :disabled="disabled"
        @click="update('options', [...question.options, { key: '', label: '' }])"
      >
        <template #icon><RiAddLine /></template>
        添加选项
      </VButton>
    </fieldset>

    <fieldset
      v-else-if="question.type === DecisionQuestionTypeEnum.Score"
      class=":uno: min-w-0 space-y-2"
    >
      <legend class=":uno: mb-2 text-sm text-slate-700 font-medium">评分等级</legend>
      <div
        v-for="(level, levelIndex) in question.levels"
        :key="levelIndex"
        class=":uno: grid grid-cols-[1.5rem_minmax(0,1fr)_2.25rem] items-start gap-2"
      >
        <span class=":uno: pt-2 text-center text-sm text-slate-500 tabular-nums">
          {{ levelIndex }}
        </span>
        <FormKit
          type="textarea"
          :aria-label="`等级 ${levelIndex} 说明`"
          placeholder="填写该等级的含义"
          :rows="1"
          :classes="{ input: 'decision-description-input' }"
          :delay="0"
          :model-value="level"
          :disabled="disabled"
          @update:model-value="updateLevel(levelIndex, $event)"
        />
        <VButton
          ghost
          size="sm"
          class=":uno: decision-row-remove !size-9 !p-0"
          :disabled="disabled"
          :aria-label="`删除等级 ${levelIndex}`"
          title="删除等级"
          @click="
            update(
              'levels',
              question.levels.filter((_, position) => position !== levelIndex),
            )
          "
        >
          <RiCloseLine class=":uno: size-4" aria-hidden="true" />
        </VButton>
      </div>
      <VButton
        ghost
        size="sm"
        :disabled="disabled"
        @click="update('levels', [...question.levels, ''])"
      >
        <template #icon><RiAddLine /></template>
        添加等级
      </VButton>
      <p class=":uno: text-xs text-slate-500 leading-relaxed">按从低到高排列，结果可以是小数。</p>
    </fieldset>

    <div class=":uno: border-t border-slate-100 pt-4">
      <VButton
        ghost
        size="sm"
        :disabled="disabled"
        :aria-label="`删除问题 ${index + 1}`"
        @click="!disabled && emit('remove')"
      >
        <template #icon><RiDeleteBinLine /></template>
        删除问题
      </VButton>
    </div>
  </div>
</template>

<style scoped>
.decision-question-editor :deep(textarea.decision-description-input) {
  field-sizing: content;
  min-height: 34px;
  max-height: 144px;
  padding-block: 7px;
  resize: vertical;
}

@supports (field-sizing: content) {
  .decision-question-editor :deep(textarea.decision-description-input) {
    resize: none;
  }
}

.decision-row-remove {
  color: #64748b;
  transition: color 150ms, background-color 150ms;
}

.decision-row-remove:not(:disabled):hover {
  color: #dc2626;
  background-color: #fef2f2 !important;
}

.decision-row-remove:focus-visible {
  outline: 2px solid #64748b;
  outline-offset: 2px;
}
</style>