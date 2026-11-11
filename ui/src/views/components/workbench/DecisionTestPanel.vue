<script setup lang="ts">
import { DecisionAnswerTypeEnum, type DecisionResponse } from '@/api/generated'
import SegmentedTabs from '@/components/SegmentedTabs.vue'
import {
  DECISION_QUESTION_TYPES,
  type DecisionQuestionDraft,
  type DecisionTestSnapshot,
} from '@/utils/decision-workbench'
import { VButton } from '@halo-dev/components'
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import { computed, nextTick, shallowRef, useTemplateRef, watch } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiContractUpDownLine from '~icons/ri/contract-up-down-line'
import RiExpandUpDownLine from '~icons/ri/expand-up-down-line'
import RiPlayLine from '~icons/ri/play-line'
import DecisionQuestionEditor from './DecisionQuestionEditor.vue'
import DecisionResults from './DecisionResults.vue'

const props = defineProps<{
  stateText: string
  stateFormat: 'text' | 'json'
  questions: DecisionQuestionDraft[]
  result?: DecisionResponse
  resultSnapshot?: DecisionTestSnapshot
  isResultStale?: boolean
  error: string
  isLoading: boolean
  disabled: boolean
}>()
const emit = defineEmits<{
  'update:stateText': [value: string]
  'update:stateFormat': [value: 'text' | 'json']
  updateQuestion: [question: DecisionQuestionDraft]
  removeQuestion: [key: number]
  addQuestion: []
  run: []
  cancel: []
}>()

const panel = useTemplateRef<HTMLElement>('panel')
const selectedKey = shallowRef('')
const inputExpanded = shallowRef(false)
const formatTabs = [
  { label: '文本', value: 'text' },
  { label: 'JSON', value: 'json' },
]

watch(
  () => props.questions.map((question) => String(question.key)),
  async (keys, previous = []) => {
    const selectedWasRemoved =
      previous.includes(selectedKey.value) && !keys.includes(selectedKey.value)
    const added = keys.find((key) => !previous.includes(key))
    if (added && previous.length) {
      selectedKey.value = added
    } else if (!keys.includes(selectedKey.value)) {
      const previousIndex = Math.max(previous.indexOf(selectedKey.value), 0)
      selectedKey.value = keys[Math.min(previousIndex, keys.length - 1)] ?? ''
    }
    if (selectedWasRemoved) {
      await nextTick()
      const target = selectedKey.value
        ? '[role="tab"][aria-selected="true"].decision-question-tab'
        : '[aria-label="添加问题"]'
      panel.value?.querySelector<HTMLElement>(target)?.focus()
    }
  },
  { immediate: true },
)

const selectedIndex = computed(() =>
  props.questions.findIndex((question) => String(question.key) === selectedKey.value),
)
const selectedQuestion = computed(() => props.questions[selectedIndex.value])
const submittedQuestion = computed(() =>
  props.resultSnapshot?.questions.find((question) => String(question.key) === selectedKey.value),
)
const selectedAnswer = computed(() =>
  submittedQuestion.value ? props.result?.answers?.[submittedQuestion.value.id.trim()] : undefined,
)

function typeLabel(question: DecisionQuestionDraft) {
  return DECISION_QUESTION_TYPES.find((type) => type.value === question.type)?.label
}

function resultSummary(key: number) {
  if (props.isResultStale) return ''
  const question = props.resultSnapshot?.questions.find((item) => item.key === key)
  const answer = question && props.result?.answers?.[question.id.trim()]
  if (!answer) return ''
  if (answer.type === DecisionAnswerTypeEnum.YesNo && answer.probability != null) {
    return `${(answer.probability * 100).toFixed(1)}%`
  }
  if (answer.type === DecisionAnswerTypeEnum.Score && answer.score != null) {
    return String(answer.score)
  }
  return ''
}
</script>

<template>
  <div ref="panel" class=":uno: decision-test-panel min-h-0 flex-1 overflow-y-auto bg-white">
    <section class=":uno: border-b border-slate-200 px-4 py-4 sm:px-6">
      <div class=":uno: mb-3 flex flex-wrap items-center justify-between gap-3">
        <div class=":uno: flex flex-wrap items-center gap-4">
          <h2 class=":uno: text-sm text-slate-900 font-semibold">待判断内容</h2>
          <SegmentedTabs
            :tabs="formatTabs"
            :model-value="stateFormat"
            :disabled="isLoading"
            compact
            aria-label="内容格式"
            @update:model-value="emit('update:stateFormat', $event === 'json' ? 'json' : 'text')"
          />
        </div>
        <VButton v-if="isLoading" size="sm" @click="emit('cancel')">取消运行</VButton>
        <VButton
          v-else
          type="secondary"
          size="sm"
          :disabled="disabled || !stateText.trim() || !questions.length"
          @click="emit('run')"
        >
          <template #icon><RiPlayLine /></template>
          运行全部问题
        </VButton>
      </div>
      <div class=":uno: relative">
        <FormKit
          type="textarea"
          aria-label="待判断的内容"
          :delay="0"
          :rows="inputExpanded ? 10 : 3"
          :placeholder="
            stateFormat === 'json' ? '输入 JSON 对象、数组或字符串…' : '输入需要判断的文本…'
          "
          :model-value="stateText"
          :disabled="isLoading"
          :input-class="stateFormat === 'json' ? ':uno: font-mono !pr-10' : ':uno: !pr-10'"
          @update:model-value="emit('update:stateText', String($event ?? ''))"
        />
        <button
          type="button"
          :aria-label="inputExpanded ? '收起内容编辑区' : '展开内容编辑区'"
          :aria-expanded="inputExpanded"
          class=":uno: absolute right-2 top-2 h-7 w-7 flex items-center justify-center rounded text-slate-500 hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-2 focus-visible:outline-slate-500"
          @click="inputExpanded = !inputExpanded"
        >
          <RiContractUpDownLine v-if="inputExpanded" class=":uno: size-4" />
          <RiExpandUpDownLine v-else class=":uno: size-4" />
        </button>
      </div>
    </section>

    <div
      v-if="error"
      role="alert"
      class=":uno: mx-4 mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 sm:mx-6"
    >
      {{ error }}
    </div>
    <div
      v-else-if="isResultStale"
      role="status"
      class=":uno: px-4 pt-3 text-xs text-amber-700 sm:px-6"
    >
      输入或问题已修改，当前显示上次运行的结果。
    </div>

    <TabsRoot
      v-model="selectedKey"
      orientation="vertical"
      activation-mode="automatic"
      class=":uno: decision-workspace"
    >
      <aside class=":uno: decision-question-list">
        <div class=":uno: mb-3 flex items-center justify-between gap-2">
          <h2 class=":uno: text-sm text-slate-900 font-semibold">问题</h2>
          <VButton
            size="sm"
            ghost
            :disabled="isLoading"
            aria-label="添加问题"
            @click="emit('addQuestion')"
          >
            <template #icon><RiAddLine /></template>
            添加
          </VButton>
        </div>
        <TabsList aria-label="判断问题" class=":uno: flex flex-col gap-1">
          <TabsTrigger
            v-for="(question, index) in questions"
            :key="question.key"
            :value="String(question.key)"
            :aria-label="`问题 ${index + 1}：${question.id || '未命名'}`"
            class=":uno: decision-question-tab"
          >
            <span class=":uno: min-w-0 flex-1 text-left">
              <span class=":uno: block truncate text-sm font-medium">{{
                question.id || '未命名问题'
              }}</span>
              <span class=":uno: mt-1 block text-xs text-slate-500">{{ typeLabel(question) }}</span>
            </span>
            <span v-if="resultSummary(question.key)" class=":uno: shrink-0 text-xs tabular-nums">
              {{ resultSummary(question.key) }}
            </span>
          </TabsTrigger>
        </TabsList>
        <p v-if="!questions.length" class=":uno: py-4 text-sm text-slate-500">还没有问题</p>
      </aside>

      <TabsContent
        v-if="selectedQuestion"
        :key="selectedQuestion.key"
        :value="selectedKey"
        class=":uno: decision-question-detail min-w-0 outline-none"
      >
        <section class=":uno: decision-editor min-w-0" aria-label="问题配置">
          <h2 class=":uno: mb-4 text-sm text-slate-900 font-semibold">问题配置</h2>
          <DecisionQuestionEditor
            :key="selectedQuestion.key"
            :question="selectedQuestion"
            :index="selectedIndex"
            :disabled="isLoading"
            @update="emit('updateQuestion', $event)"
            @remove="emit('removeQuestion', selectedQuestion.key)"
          />
        </section>
        <section class=":uno: decision-result min-w-0" aria-label="判断结果" :aria-busy="isLoading">
          <h2 class=":uno: mb-4 text-sm text-slate-900 font-semibold">
            {{ isResultStale ? '上次结果' : '判断结果' }}
          </h2>
          <p v-if="isLoading" role="status" class=":uno: text-sm text-slate-500">
            正在判断全部问题…
          </p>
          <template v-else-if="selectedAnswer">
            <p v-if="isResultStale" class=":uno: mb-3 break-all text-xs text-slate-500">
              {{ submittedQuestion?.id }}
            </p>
            <DecisionResults :answer="selectedAnswer" :question="submittedQuestion" />
          </template>
          <p v-else class=":uno: text-sm text-slate-500 leading-6">
            {{
              error
                ? '本次运行未完成。'
                : result
                  ? '该问题尚未运行。'
                  : '运行后在这里查看此问题的结果。'
            }}
          </p>
        </section>
      </TabsContent>
      <div v-else class=":uno: px-6 py-12 text-sm text-slate-500">
        添加一个问题，开始配置判断条件。
      </div>
    </TabsRoot>

    <footer
      v-if="result && (result.model || result.usage?.inputTokens != null || result.usage?.outputTokens != null)"
      class=":uno: flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-slate-200 px-4 py-3 text-xs text-slate-500 sm:px-6"
    >
      <span v-if="result.model" class=":uno: min-w-0 break-all">{{ result.model }}</span>
      <span v-if="result.usage?.inputTokens != null"
        >输入 {{ result.usage.inputTokens }} tokens</span
      >
      <span v-if="result.usage?.outputTokens != null"
        >输出 {{ result.usage.outputTokens }} tokens</span
      >
    </footer>
  </div>
</template>

<style scoped>
.decision-test-panel {
  container: decision-panel / inline-size;
}

.decision-workspace {
  display: grid;
  grid-template-columns: 12rem minmax(0, 1fr);
  min-height: 25rem;
}

.decision-question-list {
  padding: 1.25rem 1rem;
  border-right: 1px solid #e2e8f0;
}

.decision-question-tab {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  min-width: 0;
  padding: 0.75rem;
  border-left: 3px solid transparent;
  border-radius: 0.375rem;
  color: #475569;
  background: transparent;
}

.decision-question-tab:hover {
  background: #f8fafc;
}

.decision-question-tab[data-state='active'] {
  border-left-color: #0f172a;
  background: #f1f5f9;
  color: #0f172a;
}

.decision-question-tab:focus-visible {
  outline: 2px solid #64748b;
  outline-offset: 2px;
}

.decision-question-detail {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
}

.decision-editor,
.decision-result {
  padding: 1.5rem;
}

.decision-result {
  border-left: 1px solid #e2e8f0;
}

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
  font-size: 0.875rem;
  line-height: 1.25rem;
  color: #334155;
}

@container decision-panel (max-width: 960px) {
  .decision-question-detail {
    grid-template-columns: minmax(0, 1fr);
  }

  .decision-result {
    border-top: 1px solid #e2e8f0;
    border-left: 0;
  }
}

@container decision-panel (max-width: 600px) {
  .decision-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .decision-question-list {
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
  }

  .decision-editor,
  .decision-result {
    padding: 1rem;
  }
}
</style>