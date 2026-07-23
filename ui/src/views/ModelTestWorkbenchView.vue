<script lang="ts" setup>
import { useChatWorkbench } from '@/composables/workbench/use-chat-workbench'
import { useEmbeddingTest } from '@/composables/workbench/use-embedding-test'
import { useImageGenerationTest } from '@/composables/workbench/use-image-generation-test'
import { useLanguageGenerationSettings } from '@/composables/workbench/use-language-generation-settings'
import { useRagTest } from '@/composables/workbench/use-rag-test'
import { useRerankTest } from '@/composables/workbench/use-rerank-test'
import { useWorkbenchModels } from '@/composables/workbench/use-workbench-models'
import type { ExamplePrompt } from '@/utils/model-test-workbench'
import { VButton, VEmpty, VLoading } from '@halo-dev/components'
import { computed, nextTick, shallowRef, useTemplateRef, watch } from 'vue'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import ChatInputArea from './components/workbench/ChatInputArea.vue'
import ChatMessageItem from './components/workbench/ChatMessageItem.vue'
import EmbeddingTestPanel from './components/workbench/EmbeddingTestPanel.vue'
import ExamplePrompts from './components/workbench/ExamplePrompts.vue'
import ImageGenerationTestPanel from './components/workbench/ImageGenerationTestPanel.vue'
import ParameterSidebar from './components/workbench/ParameterSidebar.vue'
import RagTestPanel from './components/workbench/RagTestPanel.vue'
import RerankTestPanel from './components/workbench/RerankTestPanel.vue'
import WorkbenchToolbar from './components/workbench/WorkbenchToolbar.vue'

const {
  availableOnly,
  selectedModelName,
  testMode,
  chatModels,
  embeddingModels,
  rerankModels,
  imageModels,
  activeModels,
  activeModelType,
  selectedModel,
  isLoading,
  isFetching,
  refetch,
} = useWorkbenchModels()

const languageSettings = useLanguageGenerationSettings()
const {
  systemPrompt,
  temperature,
  topP,
  topK,
  minP,
  presencePenalty,
  frequencyPenalty,
  repetitionPenalty,
  stopSequencesText,
  logprobs,
  topLogprobs,
  parallelToolCalls,
  maxTokens,
  seed,
  maxRetries,
  reasoningMode,
  reasoningEffort,
  testToolEnabled,
  testToolApprovalEnabled,
  externalTestToolEnabled,
  agentTestToolsEnabled,
  toolCallRepairEnabled,
  toolInputStreamTestEnabled,
  outputMode,
  outputSchemaText,
  outputChoicesText,
  outputError,
  chatHeadersText,
  chatHeadersError,
} = languageSettings

const shouldAutoScroll = shallowRef(true)
const conversationRef = useTemplateRef<HTMLElement>('conversation')
const chatInputRef = useTemplateRef<InstanceType<typeof ChatInputArea>>('chatInput')

const chatWorkbench = useChatWorkbench({
  selectedModel,
  activeModels,
  testMode,
  settings: languageSettings,
  onConversationActivity: () => {
    shouldAutoScroll.value = true
  },
})
const {
  messages,
  input,
  chatFiles,
  isStreaming,
  sendMessage,
  regenerate: handleRegenerate,
  handleToolApproval,
  handleExternalToolResult,
  handleExternalToolError,
  stopGeneration: stopChatGeneration,
  clearMessages,
  applyExamplePrompt,
} = chatWorkbench

const embeddingTest = useEmbeddingTest(selectedModel)
const {
  embeddingInputs,
  embeddingDimensions,
  embeddingMaxBatchSize,
  embeddingMaxParallelCalls,
  embeddingMaxRetries,
  embeddingResult,
  embeddingError,
  isEmbeddingTesting,
  runEmbeddingTest,
  handleEmbeddingKeydown,
} = embeddingTest

const rerankTest = useRerankTest(selectedModel)
const {
  rerankQuery,
  rerankDocuments,
  rerankTopN,
  rerankResult,
  rerankError,
  isRerankTesting,
  runRerankTest,
} = rerankTest

const imageGenerationTest = useImageGenerationTest(selectedModel)
const {
  imagePrompt,
  imageInputUrl,
  imageInputData,
  imageInputMediaType,
  imageMaskUrl,
  imageMaskData,
  imageMaskMediaType,
  imageNegativePrompt,
  imageN,
  imageWidth,
  imageHeight,
  imageAspectRatio,
  imageSeed,
  imageResponseFormat,
  imageMaxRetries,
  imageMaxParallelCalls,
  imageHeadersText,
  imageHeadersError,
  imageResult,
  imageError,
  isImageTesting,
  runImageGenerationTest,
} = imageGenerationTest

const ragTest = useRagTest({ selectedModel, settings: languageSettings })
const {
  ragQuery,
  ragSources,
  ragRerankModelName,
  ragTopN,
  ragMessages,
  ragError,
  isRagTesting,
  runRagTest,
  clearRagMessages,
  stopRagTest,
} = ragTest

const isAnyTesting = computed(
  () =>
    isStreaming.value ||
    isEmbeddingTesting.value ||
    isRerankTesting.value ||
    isImageTesting.value ||
    isRagTesting.value,
)

watch(
  messages,
  async () => {
    await nextTick()
    scrollConversationToBottomIfNeeded()
  },
  { deep: true },
)

function handleExampleSelect(prompt: ExamplePrompt) {
  applyExamplePrompt(prompt)
  chatInputRef.value?.focus()
}

function stopGeneration() {
  stopChatGeneration()
  stopRagTest()
}

function handleConversationScroll() {
  const element = conversationRef.value
  if (!element) return
  shouldAutoScroll.value = distanceToConversationBottom(element) < 48
}

function scrollConversationToBottomIfNeeded() {
  const element = conversationRef.value
  if (!element || !shouldAutoScroll.value) return
  element.scrollTop = element.scrollHeight
}

function distanceToConversationBottom(element: HTMLElement) {
  return element.scrollHeight - element.scrollTop - element.clientHeight
}
</script>

<template>
  <div class=":uno: h-[calc(100vh-7.5rem)] min-h-[34rem] bg-[#eef3f7] p-2">
    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="
        !chatModels.length && !embeddingModels.length && !rerankModels.length && !imageModels.length
      "
      title="暂无可测试的模型"
      message="你可以在配置选项卡中添加或启用支持对话、嵌入、Rerank 或图片生成能力的模型"
    >
      <template #actions>
        <VButton :loading="isFetching" @click="refetch()">刷新</VButton>
      </template>
    </VEmpty>

    <div
      v-else
      class=":uno: grid grid-cols-1 h-full min-h-0 overflow-hidden border border-slate-200/80 rounded-lg bg-white lg:grid-cols-[minmax(0,1fr)_23rem]"
    >
      <section class=":uno: min-h-0 min-w-0 flex flex-col bg-[#f8fafc]">
        <WorkbenchToolbar
          v-model:mode="testMode"
          v-model:selected-model-name="selectedModelName"
          :model-type="activeModelType"
          :available="availableOnly"
          :disabled="isAnyTesting"
          :is-fetching="isFetching"
          @refresh="refetch()"
          @clear="clearMessages"
        />

        <template v-if="testMode === 'chat'">
          <div
            ref="conversation"
            class=":uno: min-h-0 flex-1 overflow-y-auto bg-[#f8fafc] px-4 py-5"
            @scroll.passive="handleConversationScroll"
          >
            <ExamplePrompts v-if="!messages.length" @select="handleExampleSelect" />

            <div v-else class=":uno: mx-auto max-w-5xl space-y-5">
              <ChatMessageItem
                v-for="(message, index) in messages"
                :key="message.id"
                :message="message"
                :index="index"
                @regenerate="handleRegenerate"
                @tool-approval="handleToolApproval"
                @external-tool-result="handleExternalToolResult"
                @external-tool-error="handleExternalToolError"
              />
            </div>
          </div>

          <ChatInputArea
            ref="chatInput"
            v-model="input"
            v-model:files="chatFiles"
            :is-streaming="isStreaming"
            :disabled="!selectedModel"
            @send="sendMessage()"
            @stop="stopGeneration"
          />
        </template>

        <template v-else-if="testMode === 'embedding'">
          <EmbeddingTestPanel
            :inputs="embeddingInputs"
            :result="embeddingResult"
            :error="embeddingError"
            :is-loading="isEmbeddingTesting"
            :disabled="!selectedModel"
            @update:inputs="embeddingInputs = $event"
            @run="runEmbeddingTest"
          />

          <div class=":uno: border-t border-slate-200 bg-white/95 px-4 py-3">
            <div class=":uno: mx-auto max-w-5xl">
              <div
                class=":uno: relative flex-1 border border-slate-200 rounded-lg bg-slate-50 shadow-inner transition-colors focus-within:border-teal-400 focus-within:bg-white focus-within:ring-3 focus-within:ring-teal-500/10"
              >
                <textarea
                  v-model="embeddingInputs"
                  rows="3"
                  placeholder="每行一段需要向量化的文本... (Cmd/Ctrl + Enter 发送)"
                  class=":uno: min-h-20 w-[calc(100%-4.5rem)] resize-none text-sm text-slate-900 leading-relaxed outline-none !border-none !bg-transparent !px-4 !py-3 placeholder:text-slate-400"
                  :disabled="!selectedModel || isEmbeddingTesting"
                  @keydown="handleEmbeddingKeydown"
                />
                <div class=":uno: absolute bottom-2 right-2 flex items-end gap-3">
                  <div class=":uno: pointer-events-none text-[11px] text-slate-400">
                    {{ embeddingInputs.length }}
                  </div>
                  <VButton
                    type="primary"
                    :loading="isEmbeddingTesting"
                    :disabled="!embeddingInputs.trim() || !selectedModel"
                    @click="runEmbeddingTest"
                    size="sm"
                  >
                    <template #icon>
                      <RiSendPlaneLine />
                    </template>
                    发送
                  </VButton>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="testMode === 'rerank'">
          <RerankTestPanel
            :query="rerankQuery"
            :documents="rerankDocuments"
            :top-n="rerankTopN"
            :result="rerankResult"
            :error="rerankError"
            :is-loading="isRerankTesting"
            :disabled="!selectedModel"
            @update:query="rerankQuery = $event"
            @update:documents="rerankDocuments = $event"
            @update:top-n="rerankTopN = $event"
            @run="runRerankTest"
          />
        </template>

        <template v-else-if="testMode === 'image'">
          <ImageGenerationTestPanel
            :prompt="imagePrompt"
            :input-url="imageInputUrl"
            :input-data="imageInputData"
            :input-media-type="imageInputMediaType"
            :mask-url="imageMaskUrl"
            :mask-data="imageMaskData"
            :mask-media-type="imageMaskMediaType"
            :negative-prompt="imageNegativePrompt"
            :result="imageResult"
            :error="imageError"
            :is-loading="isImageTesting"
            :disabled="!selectedModel"
            @update:prompt="imagePrompt = $event"
            @update:input-url="imageInputUrl = $event"
            @update:input-data="imageInputData = $event"
            @update:input-media-type="imageInputMediaType = $event"
            @update:mask-url="imageMaskUrl = $event"
            @update:mask-data="imageMaskData = $event"
            @update:mask-media-type="imageMaskMediaType = $event"
            @update:negative-prompt="imageNegativePrompt = $event"
            @run="runImageGenerationTest"
          />
        </template>

        <template v-else-if="testMode === 'rag'">
          <RagTestPanel
            :query="ragQuery"
            :sources="ragSources"
            :rerank-model-name="ragRerankModelName"
            :rerank-models="rerankModels"
            :top-n="ragTopN"
            :messages="ragMessages"
            :error="ragError"
            :is-loading="isRagTesting"
            :disabled="!selectedModel"
            @update:query="ragQuery = $event"
            @update:sources="ragSources = $event"
            @update:rerank-model-name="ragRerankModelName = $event"
            @update:top-n="ragTopN = $event"
            @run="runRagTest"
            @clear="clearRagMessages"
          />
        </template>
      </section>

      <ParameterSidebar
        :mode="testMode === 'rag' ? 'chat' : testMode"
        :system-prompt="systemPrompt"
        :temperature="temperature"
        :top-p="topP"
        :top-k="topK"
        :min-p="minP"
        :presence-penalty="presencePenalty"
        :frequency-penalty="frequencyPenalty"
        :repetition-penalty="repetitionPenalty"
        :stop-sequences-text="stopSequencesText"
        :logprobs="logprobs"
        :top-logprobs="topLogprobs"
        :parallel-tool-calls="parallelToolCalls"
        :max-tokens="maxTokens"
        :seed="seed"
        :max-retries="maxRetries"
        :reasoning-mode="reasoningMode"
        :reasoning-effort="reasoningEffort"
        :test-tool-enabled="testToolEnabled"
        :test-tool-approval-enabled="testToolApprovalEnabled"
        :external-test-tool-enabled="externalTestToolEnabled"
        :agent-test-tools-enabled="agentTestToolsEnabled"
        :tool-call-repair-enabled="toolCallRepairEnabled"
        :tool-input-stream-test-enabled="toolInputStreamTestEnabled"
        :output-mode="outputMode"
        :output-schema-text="outputSchemaText"
        :output-choices-text="outputChoicesText"
        :chat-headers-text="chatHeadersText"
        :chat-headers-error="chatHeadersError"
        :output-error="outputError"
        :embedding-dimensions="embeddingDimensions"
        :embedding-max-batch-size="embeddingMaxBatchSize"
        :embedding-max-parallel-calls="embeddingMaxParallelCalls"
        :embedding-max-retries="embeddingMaxRetries"
        :image-n="imageN"
        :image-width="imageWidth"
        :image-height="imageHeight"
        :image-aspect-ratio="imageAspectRatio"
        :image-seed="imageSeed"
        :image-response-format="imageResponseFormat"
        :image-max-retries="imageMaxRetries"
        :image-max-parallel-calls="imageMaxParallelCalls"
        :image-headers-text="imageHeadersText"
        :image-headers-error="imageHeadersError"
        @update:system-prompt="systemPrompt = $event"
        @update:temperature="temperature = $event"
        @update:top-p="topP = $event"
        @update:top-k="topK = $event"
        @update:min-p="minP = $event"
        @update:presence-penalty="presencePenalty = $event"
        @update:frequency-penalty="frequencyPenalty = $event"
        @update:repetition-penalty="repetitionPenalty = $event"
        @update:stop-sequences-text="stopSequencesText = $event"
        @update:logprobs="logprobs = $event"
        @update:top-logprobs="topLogprobs = $event"
        @update:parallel-tool-calls="parallelToolCalls = $event"
        @update:max-tokens="maxTokens = $event"
        @update:seed="seed = $event"
        @update:max-retries="maxRetries = $event"
        @update:reasoning-mode="reasoningMode = $event"
        @update:reasoning-effort="reasoningEffort = $event"
        @update:test-tool-enabled="testToolEnabled = $event"
        @update:test-tool-approval-enabled="testToolApprovalEnabled = $event"
        @update:external-test-tool-enabled="externalTestToolEnabled = $event"
        @update:agent-test-tools-enabled="agentTestToolsEnabled = $event"
        @update:tool-call-repair-enabled="toolCallRepairEnabled = $event"
        @update:tool-input-stream-test-enabled="toolInputStreamTestEnabled = $event"
        @update:output-mode="outputMode = $event"
        @update:output-schema-text="outputSchemaText = $event"
        @update:output-choices-text="outputChoicesText = $event"
        @update:chat-headers-text="chatHeadersText = $event"
        @update:embedding-dimensions="embeddingDimensions = $event"
        @update:embedding-max-batch-size="embeddingMaxBatchSize = $event"
        @update:embedding-max-parallel-calls="embeddingMaxParallelCalls = $event"
        @update:embedding-max-retries="embeddingMaxRetries = $event"
        @update:image-n="imageN = $event"
        @update:image-width="imageWidth = $event"
        @update:image-height="imageHeight = $event"
        @update:image-aspect-ratio="imageAspectRatio = $event"
        @update:image-seed="imageSeed = $event"
        @update:image-response-format="imageResponseFormat = $event"
        @update:image-max-retries="imageMaxRetries = $event"
        @update:image-max-parallel-calls="imageMaxParallelCalls = $event"
        @update:image-headers-text="imageHeadersText = $event"
      />
    </div>
  </div>
</template>
