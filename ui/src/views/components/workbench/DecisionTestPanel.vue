<script setup lang="ts">
import type { DecisionResponse } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'
import DecisionQuestionEditor from './DecisionQuestionEditor.vue'
import DecisionResults from './DecisionResults.vue'

defineProps<{
  stateText: string
  stateFormat: 'text' | 'json'
  questions: DecisionQuestionDraft[]
  result?: DecisionResponse
  error: string
  isLoading: boolean
  disabled: boolean
}>()
defineEmits<{
  'update:stateText': [value: string]
  'update:stateFormat': [value: 'text' | 'json']
  updateQuestion: [question: DecisionQuestionDraft]
  removeQuestion: [key: number]
  addQuestion: []
  run: []
  cancel: []
}>()
</script>

<template>
  <div class=":uno: min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
    <div class=":uno: mx-auto max-w-4xl space-y-4">
      <div class=":uno: text-sm text-slate-600">所有问题共享同一段内容，分别返回判断结果。</div>
      <FormKit
        type="select"
        label="内容格式"
        :model-value="stateFormat"
        :disabled="isLoading"
        :options="[
          { label: '文本', value: 'text' },
          { label: 'JSON', value: 'json' },
        ]"
        @update:model-value="$emit('update:stateFormat', $event === 'json' ? 'json' : 'text')"
      />
      <FormKit
        type="textarea"
        label="待判断的内容"
        :rows="5"
        :model-value="stateText"
        :disabled="isLoading"
        @update:model-value="$emit('update:stateText', String($event ?? ''))"
      />
      <DecisionQuestionEditor
        v-for="question in questions"
        :key="question.key"
        :question="question"
        :disabled="isLoading"
        @update="$emit('updateQuestion', $event)"
        @remove="$emit('removeQuestion', question.key)"
      />
      <div class=":uno: flex flex-wrap justify-between gap-3">
        <VButton :disabled="isLoading" @click="$emit('addQuestion')">添加问题</VButton>
        <VButton v-if="isLoading" @click="$emit('cancel')">停止等待</VButton>
        <VButton
          v-else
          type="primary"
          :disabled="disabled || !stateText.trim() || !questions.length"
          @click="$emit('run')"
          >开始判断</VButton
        >
      </div>
      <div v-if="error" role="alert" class=":uno: rounded bg-rose-50 p-3 text-sm text-rose-700">
        {{ error }}
      </div>
      <DecisionResults v-if="result" :result="result" />
      <div v-else-if="!error" class=":uno: py-6 text-center text-sm text-slate-500">
        {{ isLoading ? '正在判断…' : '提交问题后，在这里查看概率、选项和评分。' }}
      </div>
    </div>
  </div>
</template>
