<script setup lang="ts">
import type { DecisionResponse } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'
import RiAddLine from '~icons/ri/add-line'
import RiLoader4Line from '~icons/ri/loader-4-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStackLine from '~icons/ri/stack-line'
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
  <div class=":uno: decision-test-panel min-h-0 flex-1 overflow-y-auto bg-[#f8fafc] px-4 py-5">
    <div class=":uno: mx-auto max-w-5xl space-y-3">
      <div class=":uno: grid items-start gap-3 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <section class=":uno: min-w-0 border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <div class=":uno: mb-3 flex flex-wrap items-center justify-between gap-3">
            <span class=":uno: text-sm text-slate-900 font-semibold">待判断内容</span>
            <div role="group" aria-label="内容格式" class=":uno: w-28">
              <FormKit
                type="select"
                aria-label="内容格式"
                :model-value="stateFormat"
                :disabled="isLoading"
                :options="[
                  { label: '文本', value: 'text' },
                  { label: 'JSON', value: 'json' },
                ]"
                @update:model-value="
                  $emit('update:stateFormat', $event === 'json' ? 'json' : 'text')
                "
              />
            </div>
          </div>
          <FormKit
            type="textarea"
            aria-label="待判断的内容"
            :rows="8"
            :placeholder="
              stateFormat === 'json' ? '输入 JSON 对象、数组或字符串…' : '输入需要判断的文本…'
            "
            :model-value="stateText"
            :disabled="isLoading"
            @update:model-value="$emit('update:stateText', String($event ?? ''))"
          />
          <p class=":uno: mt-2 text-xs text-slate-500 leading-relaxed">
            所有问题共享这段内容，每个问题分别返回判断结果。
          </p>
        </section>

        <section class=":uno: min-w-0 border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <div class=":uno: mb-3 flex items-center justify-between gap-3">
            <span class=":uno: text-sm text-slate-900 font-semibold">判断问题</span>
            <VButton size="sm" :disabled="isLoading" @click="$emit('addQuestion')">
              <template #icon><RiAddLine /></template>
              添加问题
            </VButton>
          </div>
          <div class=":uno: space-y-3">
            <DecisionQuestionEditor
              v-for="(question, index) in questions"
              :key="question.key"
              :question="question"
              :index="index"
              :disabled="isLoading"
              @update="$emit('updateQuestion', $event)"
              @remove="$emit('removeQuestion', question.key)"
            />
          </div>
          <div v-if="!questions.length" class=":uno: py-8 text-center text-xs text-slate-500">
            添加一个问题，选择是非概率、单选或评分。
          </div>
        </section>
      </div>

      <div class=":uno: flex items-center justify-end gap-3">
        <span
          v-if="isLoading"
          role="status"
          class=":uno: flex items-center gap-2 text-xs text-slate-500"
        >
          <RiLoader4Line class=":uno: size-4 animate-spin" />
          正在判断…
        </span>
        <VButton v-if="isLoading" @click="$emit('cancel')">停止等待</VButton>
        <VButton
          v-else
          type="primary"
          :disabled="disabled || !stateText.trim() || !questions.length"
          @click="$emit('run')"
        >
          <template #icon><RiSendPlaneLine /></template>
          开始判断
        </VButton>
      </div>
      <div
        v-if="error"
        role="alert"
        class=":uno: border border-rose-200 rounded-lg bg-rose-50 text-sm text-rose-700 shadow-sm !px-4 !py-3"
      >
        {{ error }}
      </div>
      <DecisionResults v-if="result" :result="result" />
      <div
        v-else-if="!error && !isLoading"
        class=":uno: border border-slate-200 rounded-lg border-dashed bg-white text-center shadow-sm !px-6 !py-10"
      >
        <div
          class=":uno: mx-auto mb-3 size-12 flex items-center justify-center border border-slate-200 rounded-lg bg-slate-50"
        >
          <RiStackLine class=":uno: size-5 text-slate-500" />
        </div>
        <div class=":uno: text-sm text-slate-900 font-semibold">暂无决策测试结果</div>
        <div class=":uno: mt-1 text-xs text-slate-500">填写内容和问题后点击开始判断</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Keep Halo's FormKit controls compact and aligned inside the workbench grid. */
.decision-test-panel :deep(.formkit-outer) {
  min-width: 0;
  padding: 0 !important;
}

.decision-test-panel :deep(.formkit-wrapper) {
  gap: 0.5rem;
}

.decision-test-panel :deep(.formkit-inner) {
  max-width: none;
}

.decision-test-panel :deep(.formkit-label) {
  color: #475569;
  font-size: 0.75rem;
  line-height: 1rem;
}
</style>
