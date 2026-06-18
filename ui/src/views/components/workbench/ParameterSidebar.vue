<script lang="ts" setup>
import type { OutputMode, ReasoningEffort, ReasoningMode } from '@/utils/model-test-workbench'
import { VSwitch } from '@halo-dev/components'
import { computed } from 'vue'
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'
import RiFlaskLine from '~icons/ri/flask-line'
import RiSettings3Line from '~icons/ri/settings-3-line'

const props = defineProps<{
  mode: 'chat' | 'embedding' | 'rerank'
  systemPrompt?: string
  temperature?: number
  topP?: number
  maxTokens?: number
  seed?: number | undefined
  maxRetries?: number | undefined
  reasoningMode?: ReasoningMode
  reasoningEffort?: ReasoningEffort
  testToolEnabled?: boolean
  testToolApprovalEnabled?: boolean
  externalTestToolEnabled?: boolean
  agentTestToolsEnabled?: boolean
  toolCallRepairEnabled?: boolean
  outputMode?: OutputMode
  outputSchemaText?: string
  outputChoicesText?: string
  providerOptionsText?: string
  chatHeadersText?: string
  chatHeadersError?: string
  providerOptionsError?: string
  outputError?: string
  embeddingDimensions?: number | undefined
  embeddingMaxBatchSize?: number | undefined
  embeddingMaxParallelCalls?: number | undefined
  embeddingMaxRetries?: number | undefined
  embeddingProviderOptionsText?: string
  embeddingProviderOptionsError?: string
}>()

const emit = defineEmits<{
  (e: 'update:systemPrompt', value: string): void
  (e: 'update:temperature', value: number): void
  (e: 'update:topP', value: number): void
  (e: 'update:maxTokens', value: number): void
  (e: 'update:seed', value: number | undefined): void
  (e: 'update:maxRetries', value: number | undefined): void
  (e: 'update:reasoningMode', value: ReasoningMode): void
  (e: 'update:reasoningEffort', value: ReasoningEffort): void
  (e: 'update:testToolEnabled', value: boolean): void
  (e: 'update:testToolApprovalEnabled', value: boolean): void
  (e: 'update:externalTestToolEnabled', value: boolean): void
  (e: 'update:agentTestToolsEnabled', value: boolean): void
  (e: 'update:toolCallRepairEnabled', value: boolean): void
  (e: 'update:outputMode', value: OutputMode): void
  (e: 'update:outputSchemaText', value: string): void
  (e: 'update:outputChoicesText', value: string): void
  (e: 'update:providerOptionsText', value: string): void
  (e: 'update:chatHeadersText', value: string): void
  (e: 'update:embeddingDimensions', value: number | undefined): void
  (e: 'update:embeddingMaxBatchSize', value: number | undefined): void
  (e: 'update:embeddingMaxParallelCalls', value: number | undefined): void
  (e: 'update:embeddingMaxRetries', value: number | undefined): void
  (e: 'update:embeddingProviderOptionsText', value: string): void
}>()

const outputSchemaHelp = computed(() => {
  return (
    props.outputError ||
    (props.outputMode === 'ARRAY' ? '用于校验每个数组元素' : '用于约束最终 JSON 对象')
  )
})

const outputChoicesHelp = computed(() => {
  return props.outputError || '每行一个可选值'
})

const providerOptionsHelp = computed(() => {
  return (
    props.providerOptionsError || '请输入按服务商分组的 JSON 对象，例如 {"openai": {"seed": 42}}'
  )
})

type NumberFieldKey =
  | 'temperature'
  | 'topP'
  | 'maxTokens'
  | 'seed'
  | 'maxRetries'
  | 'embeddingDimensions'
  | 'embeddingMaxBatchSize'
  | 'embeddingMaxParallelCalls'
  | 'embeddingMaxRetries'

function updateNumberField(key: NumberFieldKey, value: string) {
  const num = value === '' ? undefined : Number(value)
  switch (key) {
    case 'temperature':
      emit('update:temperature', num as number)
      break
    case 'topP':
      emit('update:topP', num as number)
      break
    case 'maxTokens':
      emit('update:maxTokens', num as number)
      break
    case 'seed':
      emit('update:seed', num)
      break
    case 'maxRetries':
      emit('update:maxRetries', num)
      break
    case 'embeddingDimensions':
      emit('update:embeddingDimensions', num)
      break
    case 'embeddingMaxBatchSize':
      emit('update:embeddingMaxBatchSize', num)
      break
    case 'embeddingMaxParallelCalls':
      emit('update:embeddingMaxParallelCalls', num)
      break
    case 'embeddingMaxRetries':
      emit('update:embeddingMaxRetries', num)
      break
  }
}
</script>

<template>
  <div
    class=":uno: h-full flex flex-col overflow-hidden border-t border-slate-200 bg-white lg:border-l lg:border-t-0"
  >
    <div class=":uno: border-b border-slate-200 bg-slate-50/70 px-4 py-3">
      <div class=":uno: flex items-center gap-2">
        <span
          class=":uno: h-7 w-7 flex items-center justify-center rounded-lg bg-white text-slate-600 shadow-sm ring-1 ring-slate-200"
        >
          <RiSettings3Line class=":uno: size-4" />
        </span>
        <span class=":uno: text-sm text-slate-950 font-semibold">参数设置</span>
      </div>
      <div class=":uno: mt-1 text-xs text-slate-500">调整模型行为、输出格式和请求扩展</div>
    </div>

    <div class=":uno: flex-1 overflow-y-auto px-4 py-3">
      <template v-if="mode === 'chat'">
        <details class=":uno: group border-b border-slate-200 last:border-b-0" open>
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            系统提示词
          </summary>
          <div class=":uno: pb-3 pl-5">
            <textarea
              :value="systemPrompt"
              rows="4"
              placeholder="可选：设置 AI 的角色和行为规则..."
              class=":uno: w-full text-slate-700 leading-relaxed outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="emit('update:systemPrompt', ($event.target as HTMLTextAreaElement).value)"
            />
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0" open>
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            <RiFlaskLine class=":uno: size-3.5" />
            采样参数
          </summary>
          <div class=":uno: pb-3 pl-5 space-y-3">
            <div class=":uno: space-y-1">
              <div class=":uno: flex items-center justify-between">
                <label class=":uno: text-xs text-slate-600 font-medium">Temperature</label>
                <span
                  class=":uno: border border-slate-200 rounded-md bg-white text-xs text-slate-700 font-mono !px-1.5 !py-0.5"
                >
                  {{ temperature }}
                </span>
              </div>
              <input
                type="range"
                :value="temperature"
                min="0"
                max="2"
                step="0.1"
                class=":uno: h-1.5 w-full cursor-pointer appearance-none rounded-full bg-slate-200 accent-teal-600 [&::-moz-range-thumb]:h-3 [&::-moz-range-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:appearance-none [&::-moz-range-thumb]:border-2 [&::-webkit-slider-thumb]:border-2 [&::-moz-range-thumb]:border-white [&::-webkit-slider-thumb]:border-white [&::-moz-range-thumb]:rounded-full [&::-webkit-slider-thumb]:rounded-full [&::-moz-range-thumb]:bg-teal-700 [&::-webkit-slider-thumb]:bg-teal-700 [&::-moz-range-thumb]:shadow-[0_1px_4px_rgba(15,23,42,0.24)] [&::-webkit-slider-thumb]:shadow-[0_1px_4px_rgba(15,23,42,0.24)]"
                @input="updateNumberField('temperature', ($event.target as HTMLInputElement).value)"
              />
              <div class=":uno: flex justify-between text-[10px] text-slate-400">
                <span>精确</span>
                <span>平衡</span>
                <span>创意</span>
              </div>
            </div>

            <div class=":uno: space-y-1">
              <div class=":uno: flex items-center justify-between">
                <label class=":uno: text-xs text-slate-600 font-medium">Top P</label>
                <span
                  class=":uno: border border-slate-200 rounded-md bg-white text-xs text-slate-700 font-mono !px-1.5 !py-0.5"
                >
                  {{ topP }}
                </span>
              </div>
              <input
                type="range"
                :value="topP"
                min="0"
                max="1"
                step="0.05"
                class=":uno: h-1.5 w-full cursor-pointer appearance-none rounded-full bg-slate-200 accent-teal-600 [&::-moz-range-thumb]:h-3 [&::-moz-range-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:appearance-none [&::-moz-range-thumb]:border-2 [&::-webkit-slider-thumb]:border-2 [&::-moz-range-thumb]:border-white [&::-webkit-slider-thumb]:border-white [&::-moz-range-thumb]:rounded-full [&::-webkit-slider-thumb]:rounded-full [&::-moz-range-thumb]:bg-teal-700 [&::-webkit-slider-thumb]:bg-teal-700 [&::-moz-range-thumb]:shadow-[0_1px_4px_rgba(15,23,42,0.24)] [&::-webkit-slider-thumb]:shadow-[0_1px_4px_rgba(15,23,42,0.24)]"
                @input="updateNumberField('topP', ($event.target as HTMLInputElement).value)"
              />
            </div>

            <div class=":uno: space-y-1">
              <div class=":uno: flex items-center justify-between">
                <label class=":uno: text-xs text-slate-600 font-medium">Max Tokens</label>
                <input
                  type="number"
                  :value="maxTokens"
                  min="1"
                  step="1"
                  class=":uno: w-20 text-right text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="updateNumberField('maxTokens', ($event.target as HTMLInputElement).value)"
                />
              </div>
            </div>

            <div class=":uno: grid grid-cols-2 gap-2">
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Seed</label>
                <input
                  type="number"
                  :value="seed"
                  step="1"
                  placeholder="随机"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="updateNumberField('seed', ($event.target as HTMLInputElement).value)"
                />
              </div>
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Max Retries</label>
                <input
                  type="number"
                  :value="maxRetries"
                  min="0"
                  step="1"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    updateNumberField('maxRetries', ($event.target as HTMLInputElement).value)
                  "
                />
              </div>
            </div>
            <div
              v-if="maxRetries !== undefined"
              class=":uno: rounded-md bg-slate-50 text-[10px] text-slate-500 !px-2 !py-1.5"
            >
              仅作用于可重试的非流式 provider 调用，0 表示不重试
            </div>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            推理控制
          </summary>
          <div class=":uno: pb-3 pl-5 space-y-3">
            <div class=":uno: space-y-1">
              <label class=":uno: text-xs text-slate-600 font-medium">模式</label>
              <select
                :value="reasoningMode"
                class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
                @change="
                  emit(
                    'update:reasoningMode',
                    ($event.target as HTMLSelectElement).value as ReasoningMode,
                  )
                "
              >
                <option value="DEFAULT">默认（不传递推理参数）</option>
                <option value="ENABLED">启用推理</option>
                <option value="DISABLED">禁用推理</option>
                <option value="EFFORT">按 Effort 控制</option>
              </select>
              <div class=":uno: text-[10px] text-slate-400">
                默认表示不传 provider 原生推理参数；启用、禁用和 effort 需要当前 provider 支持
              </div>
            </div>

            <div v-if="reasoningMode === 'EFFORT'" class=":uno: space-y-1">
              <label class=":uno: text-xs text-slate-600 font-medium">Effort</label>
              <select
                :value="reasoningEffort"
                class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
                @change="
                  emit(
                    'update:reasoningEffort',
                    ($event.target as HTMLSelectElement).value as ReasoningEffort,
                  )
                "
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </select>
            </div>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            结构化输出
          </summary>
          <div class=":uno: pb-3 pl-5 space-y-3">
            <div class=":uno: space-y-1">
              <label class=":uno: text-xs text-slate-600 font-medium">输出格式</label>
              <select
                :value="outputMode"
                class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
                @change="
                  emit(
                    'update:outputMode',
                    ($event.target as HTMLSelectElement).value as OutputMode,
                  )
                "
              >
                <option value="TEXT">文本</option>
                <option value="OBJECT">JSON 对象</option>
                <option value="ARRAY">JSON 数组</option>
                <option value="CHOICE">枚举选择</option>
                <option value="JSON">任意 JSON</option>
              </select>
            </div>

            <template v-if="outputMode === 'OBJECT' || outputMode === 'ARRAY'">
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">{{
                  outputMode === 'ARRAY' ? '元素 JSON Schema' : 'JSON Schema'
                }}</label>
                <textarea
                  :value="outputSchemaText"
                  rows="6"
                  :class="{ ':uno: !border-rose-300': outputError }"
                  class=":uno: w-full text-slate-700 leading-relaxed font-mono outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    emit('update:outputSchemaText', ($event.target as HTMLTextAreaElement).value)
                  "
                />
                <div
                  class=":uno: text-[10px]"
                  :class="outputError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
                >
                  {{ outputSchemaHelp }}
                </div>
              </div>
            </template>

            <template v-if="outputMode === 'CHOICE'">
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">枚举选项</label>
                <textarea
                  :value="outputChoicesText"
                  rows="4"
                  :class="{ ':uno: !border-rose-300': outputError }"
                  class=":uno: w-full text-slate-700 leading-relaxed outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    emit('update:outputChoicesText', ($event.target as HTMLTextAreaElement).value)
                  "
                />
                <div
                  class=":uno: text-[10px]"
                  :class="outputError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
                >
                  {{ outputChoicesHelp }}
                </div>
              </div>
            </template>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            工具测试
          </summary>
          <div class=":uno: pb-3 pl-5">
            <div class=":uno: flex items-center gap-2">
              <VSwitch
                :model-value="testToolEnabled"
                @update:model-value="emit('update:testToolEnabled', $event)"
              />
              <span class=":uno: text-xs text-slate-700">启用测试工具</span>
            </div>
            <div class=":uno: mt-2 flex items-center gap-2">
              <VSwitch
                :model-value="testToolApprovalEnabled"
                :disabled="!testToolEnabled"
                @update:model-value="emit('update:testToolApprovalEnabled', $event)"
              />
              <span
                class=":uno: text-xs"
                :class="testToolEnabled ? ':uno: text-slate-700' : ':uno: text-slate-400'"
              >
                工具执行需要审批
              </span>
            </div>
            <div class=":uno: mt-2 flex items-center gap-2">
              <VSwitch
                :model-value="externalTestToolEnabled"
                @update:model-value="emit('update:externalTestToolEnabled', $event)"
              />
              <span class=":uno: text-xs text-slate-700">启用外部测试工具</span>
            </div>
            <div class=":uno: mt-2 flex items-center gap-2">
              <VSwitch
                :model-value="agentTestToolsEnabled"
                @update:model-value="emit('update:agentTestToolsEnabled', $event)"
              />
              <span class=":uno: text-xs text-slate-700">启用 Agent 工具测试</span>
            </div>
            <div class=":uno: mt-2 flex items-center gap-2">
              <VSwitch
                :model-value="toolCallRepairEnabled"
                @update:model-value="emit('update:toolCallRepairEnabled', $event)"
              />
              <span class=":uno: text-xs text-slate-700">启用工具调用修复</span>
            </div>
            <div class=":uno: mt-1 text-[10px] text-slate-400">
              halo_test_info 可用于服务端工具测试；外部工具会注入
              halo_external_test_info；修复测试会注入 halo_repair_test_info；前端自动工具会注入
              get_current_page_context 和 halo_agent_test_action，并由工作台前端自动回填工具结果。
            </div>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            Provider Options
          </summary>
          <div class=":uno: pb-3 pl-5">
            <textarea
              :value="providerOptionsText"
              rows="6"
              :class="{ ':uno: !border-rose-300': providerOptionsError }"
              class=":uno: w-full text-slate-700 leading-relaxed font-mono outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                emit('update:providerOptionsText', ($event.target as HTMLTextAreaElement).value)
              "
            />
            <div
              class=":uno: mt-1 text-[10px]"
              :class="providerOptionsError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
            >
              {{ providerOptionsHelp }}
            </div>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            Headers
          </summary>
          <div class=":uno: pb-3 pl-5">
            <textarea
              :value="chatHeadersText"
              rows="4"
              :class="{ ':uno: !border-rose-300': chatHeadersError }"
              class=":uno: w-full text-slate-700 leading-relaxed font-mono outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="emit('update:chatHeadersText', ($event.target as HTMLTextAreaElement).value)"
            />
            <div
              class=":uno: mt-1 text-[10px]"
              :class="chatHeadersError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
            >
              {{
                chatHeadersError || '请求级 headers，当前 provider 不支持时会返回 warning 或错误'
              }}
            </div>
          </div>
        </details>
      </template>

      <template v-else-if="mode === 'embedding'">
        <details class=":uno: group border-b border-slate-200 last:border-b-0" open>
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            嵌入参数
          </summary>
          <div class=":uno: pb-3 pl-5 space-y-3">
            <div class=":uno: grid grid-cols-2 gap-2">
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Dimensions</label>
                <input
                  type="number"
                  :value="embeddingDimensions"
                  min="1"
                  step="1"
                  placeholder="默认"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    updateNumberField(
                      'embeddingDimensions',
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
              </div>
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Max Batch Size</label>
                <input
                  type="number"
                  :value="embeddingMaxBatchSize"
                  min="1"
                  step="1"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    updateNumberField(
                      'embeddingMaxBatchSize',
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
              </div>
            </div>

            <div class=":uno: grid grid-cols-2 gap-2">
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Max Parallel Calls</label>
                <input
                  type="number"
                  :value="embeddingMaxParallelCalls"
                  min="1"
                  step="1"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    updateNumberField(
                      'embeddingMaxParallelCalls',
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
              </div>
              <div class=":uno: space-y-1">
                <label class=":uno: text-xs text-slate-600 font-medium">Max Retries</label>
                <input
                  type="number"
                  :value="embeddingMaxRetries"
                  min="0"
                  step="1"
                  class=":uno: w-full text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
                  @input="
                    updateNumberField(
                      'embeddingMaxRetries',
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
              </div>
            </div>
          </div>
        </details>

        <details class=":uno: group border-b border-slate-200 last:border-b-0">
          <summary
            class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
          >
            <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
            Provider Options
          </summary>
          <div class=":uno: pb-3 pl-5">
            <textarea
              :value="embeddingProviderOptionsText"
              rows="6"
              :class="{ ':uno: !border-rose-300': embeddingProviderOptionsError }"
              class=":uno: w-full text-slate-700 leading-relaxed font-mono outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                emit(
                  'update:embeddingProviderOptionsText',
                  ($event.target as HTMLTextAreaElement).value,
                )
              "
            />
            <div
              class=":uno: mt-1 text-[10px]"
              :class="
                embeddingProviderOptionsError ? ':uno: text-rose-500' : ':uno: text-slate-400'
              "
            >
              {{ embeddingProviderOptionsError || '例如 openai.dimensions = 512' }}
            </div>
          </div>
        </details>
      </template>
    </div>
  </div>
</template>
