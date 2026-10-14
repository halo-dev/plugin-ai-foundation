<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import { useToolsFetch } from '@/composables/use-tools-fetch'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import { filterTools, toolDisplayName, toolSelectable, toolStatusLabel } from '@/utils/tools'
import { IconRefreshLine, VButton, VLoading } from '@halo-dev/components'
import { PopoverClose, PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { computed, nextTick, shallowRef, useId, useTemplateRef, watch } from 'vue'
import { RouterLink } from 'vue-router'
import MingcuteCloseLine from '~icons/mingcute/close-line'
import MingcuteDownLine from '~icons/mingcute/down-line'
import MingcuteSettings3Line from '~icons/mingcute/settings-3-line'
import AiToolSelectOption from './AiToolSelectOption.vue'

const props = withDefaults(
  defineProps<{
    id?: string
    modelValue?: string[]
    pluginName?: string
    category?: string
    placeholder?: string
    searchPlaceholder?: string
    disabled?: boolean
    fullWidth?: boolean
    label?: string
    labelledby?: string
    showLabel?: boolean
  }>(),
  {
    modelValue: () => [],
    placeholder: '请选择工具',
    searchPlaceholder: '搜索工具名称、标签、来源...',
    disabled: false,
    fullWidth: false,
    label: '选择工具',
    showLabel: true,
  },
)

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const { data, isLoading, isError, isFetching, refetch } = useToolsFetch()
const generatedId = useId()
const triggerId = computed(() => props.id || 'ai-tool-selector-' + generatedId)
const summaryId = generatedId + '-summary'
const warningId = generatedId + '-warning'
const popupHeadingId = generatedId + '-heading'
const isOpen = shallowRef(false)
const keyword = shallowRef('')
const searchContainer = useTemplateRef<HTMLElement>('searchContainer')
const optionList = useTemplateRef<HTMLElement>('optionList')
const tools = computed(() => data.value || [])
const toolById = computed(() => new Map(tools.value.map((tool) => [tool.id, tool])))
const selectedIds = computed(() => [...new Set(props.modelValue)])
const selectedTools = computed(() =>
  selectedIds.value.map((id) => ({ id, tool: toolById.value.get(id) })),
)
const unavailableSelections = computed(() =>
  isLoading.value || isError.value
    ? []
    : selectedTools.value.filter(({ tool }) => !tool || !toolSelectable(tool)),
)
const selectedSummary = computed(() => {
  const names = selectedTools.value
    .slice(0, 2)
    .map(({ id, tool }) => (tool ? toolDisplayName(tool) : id))
  return names.join('、') + (selectedIds.value.length > 2 ? '等工具' : '')
})
const selectedTitle = computed(() =>
  selectedTools.value.map(({ id, tool }) => (tool ? toolDisplayName(tool) : id)).join('、'),
)
const visibleTools = computed(() =>
  filterTools(tools.value, {
    keyword: keyword.value,
    pluginName: props.pluginName,
    category: props.category,
  }),
)
const groups = computed(() => {
  const sources = new Map<string, ToolDescriptor[]>()
  for (const tool of visibleTools.value) {
    const source = tool.pluginName || '未知插件'
    const group = sources.get(source)
    if (group) group.push(tool)
    else sources.set(source, [tool])
  }
  return Array.from(sources, ([source, tools]) => ({
    source,
    label: source === 'ai-foundation' ? 'AI Foundation' : source,
    tools,
  }))
})
const selectableIds = computed(() =>
  visibleTools.value.filter(toolSelectable).map((tool) => tool.id!),
)
const selectionPending = computed(() => isLoading.value || isError.value || isFetching.value)
const allVisibleSelected = computed(
  () =>
    selectableIds.value.length > 0 &&
    selectableIds.value.every((id) => selectedIds.value.includes(id)),
)

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) isOpen.value = false
  },
)
watch(isOpen, (open) => {
  if (!open) keyword.value = ''
})

function toggleTool(id: string) {
  if (props.disabled || selectionPending.value) return
  const tool = toolById.value.get(id)
  if (!tool || !toolSelectable(tool)) return
  emit(
    'update:modelValue',
    selectedIds.value.includes(id)
      ? selectedIds.value.filter((selected) => selected !== id)
      : [...selectedIds.value, id],
  )
}

function selectAll() {
  if (props.disabled || selectionPending.value || !selectableIds.value.length) return
  emit('update:modelValue', [...new Set([...selectedIds.value, ...selectableIds.value])])
}

function clearSelection() {
  if (!props.disabled && selectedIds.value.length) emit('update:modelValue', [])
}

function removeTool(id: string) {
  if (!props.disabled)
    emit(
      'update:modelValue',
      selectedIds.value.filter((selected) => selected !== id),
    )
}

async function focusSearch(event: Event) {
  event.preventDefault()
  await nextTick()
  searchContainer.value?.querySelector('input')?.focus({ preventScroll: true })
}

function focusOption(position: 'first' | 'last' | number, current?: EventTarget | null) {
  const inputs = Array.from(
    optionList.value?.querySelectorAll<HTMLInputElement>('input[type="checkbox"]:not(:disabled)') ||
      [],
  )
  if (!inputs.length) return
  const index =
    position === 'first'
      ? 0
      : position === 'last'
        ? inputs.length - 1
        : (inputs.indexOf(current as HTMLInputElement) + position + inputs.length) % inputs.length
  inputs[index]?.focus({ preventScroll: true })
  inputs[index]?.scrollIntoView?.({ block: 'nearest' })
}

function handleSearchKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    focusOption(event.key === 'ArrowDown' ? 'first' : 'last')
  } else if (event.key === 'Enter') {
    event.preventDefault()
    if (selectableIds.value[0]) toggleTool(selectableIds.value[0])
  }
}

function handleOptionKeydown(event: KeyboardEvent) {
  if (!(event.target instanceof HTMLInputElement) || event.target.type !== 'checkbox') return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    focusOption(event.key === 'ArrowDown' ? 1 : -1, event.target)
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    focusOption(event.key === 'Home' ? 'first' : 'last')
  }
}
</script>

<template>
  <div class=":uno: min-w-0 w-full" :data-disabled="disabled || undefined">
    <label
      v-if="showLabel && !labelledby"
      :for="triggerId"
      class=":uno: mb-1.5 block text-sm text-gray-700 font-medium"
      >{{ label }}</label
    >
    <PopoverRoot v-model:open="isOpen">
      <div class=":uno: relative" :class="fullWidth ? ':uno: w-full' : ':uno: sm:max-w-lg'">
        <PopoverTrigger as-child>
          <button
            type="button"
            :id="triggerId"
            :disabled="disabled"
            :aria-label="labelledby ? undefined : label"
            :aria-labelledby="labelledby"
            :aria-describedby="summaryId + (unavailableSelections.length ? ' ' + warningId : '')"
            :title="selectedTitle || undefined"
            class=":uno: group h-9 w-full flex cursor-pointer items-center gap-2 border border-gray-200 rounded-md bg-white px-3 text-left text-sm transition-colors disabled:cursor-not-allowed focus:border-gray-400 hover:border-gray-400 disabled:bg-gray-50 focus:outline-none"
            :class="selectedIds.length && !isOpen && !disabled ? ':uno: pr-14' : ':uno: pr-3'"
          >
            <span
              class=":uno: min-w-0 flex-1 truncate"
              :class="selectedIds.length ? ':uno: text-gray-900' : ':uno: text-gray-500'"
              >{{ selectedIds.length ? selectedSummary : placeholder }}</span
            >
            <span
              v-if="selectedIds.length > 1"
              class=":uno: flex-none rounded bg-gray-100 px-1.5 text-xs text-gray-600"
              >{{ selectedIds.length }}</span
            >
            <MingcuteDownLine
              class=":uno: size-4 flex-none text-gray-500 transition-transform duration-200 group-data-[state=open]:rotate-180"
              aria-hidden="true"
            />
          </button>
        </PopoverTrigger>
        <button
          v-if="selectedIds.length && !isOpen && !disabled"
          type="button"
          aria-label="全部取消"
          class=":uno: absolute right-7 top-1/2 size-5 flex items-center justify-center rounded text-gray-500 -translate-y-1/2 hover:bg-gray-100 hover:text-gray-700"
          @click="clearSelection"
        >
          <MingcuteCloseLine class=":uno: size-3.5" aria-hidden="true" />
        </button>
      </div>
      <PopoverPortal>
        <PopoverContent
          as-child
          align="start"
          :side-offset="4"
          :collision-padding="8"
          @open-auto-focus="focusSearch"
        >
          <div
            :aria-labelledby="popupHeadingId"
            class=":uno: ai-tool-selector__content min-h-0 flex flex-col"
            :style="{
              width: 'min(var(--reka-popover-trigger-width), calc(100vw - 1rem))',
              maxHeight: 'min(32rem, var(--reka-popover-content-available-height))',
            }"
          >
            <h2 :id="popupHeadingId" class=":uno: sr-only">{{ label }}</h2>
            <div class=":uno: min-h-0 flex flex-1 flex-col">
              <div ref="searchContainer" class=":uno: flex-none border-b border-gray-100 p-2">
                <FormKit
                  :id="triggerId + '-search'"
                  v-model="keyword"
                  type="text"
                  label="搜索工具"
                  label-class="sr-only"
                  aria-label="搜索工具"
                  :placeholder="searchPlaceholder"
                  :ignore="true"
                  :delay="0"
                  :disabled="disabled"
                  outer-class="!p-0 !w-full"
                  @keydown="handleSearchKeydown"
                />
              </div>
              <div
                class=":uno: flex flex-none items-center justify-between gap-2 border-b border-gray-100 px-3 py-2"
              >
                <div class=":uno: flex items-center gap-2">
                  <VButton
                    size="sm"
                    :disabled="
                      disabled || selectionPending || !selectableIds.length || allVisibleSelected
                    "
                    @click="selectAll"
                    >{{ keyword.trim() ? '全选当前结果' : '全选' }}</VButton
                  >
                  <VButton
                    size="sm"
                    :disabled="disabled || !selectedIds.length"
                    @click="clearSelection"
                    >全部取消</VButton
                  >
                </div>
                <button
                  type="button"
                  :disabled="disabled || isFetching"
                  aria-label="刷新可选工具"
                  class=":uno: size-7 flex flex-none items-center justify-center rounded text-gray-500 disabled:cursor-not-allowed hover:bg-gray-100 disabled:opacity-50"
                  @click="refetch()"
                >
                  <IconRefreshLine
                    :class="{ ':uno: animate-spin': isFetching }"
                    aria-hidden="true"
                  />
                </button>
              </div>
              <div
                v-if="unavailableSelections.length"
                class=":uno: max-h-24 flex-none overflow-y-auto border-b border-gray-100 bg-orange-50 px-3 py-2 text-xs"
              >
                <p class=":uno: mb-1 text-orange-700">以下已选工具暂不可用</p>
                <ul class=":uno: m-0 list-none p-0 space-y-1" aria-label="不可用的已选工具">
                  <li
                    v-for="selection in unavailableSelections"
                    :key="selection.id"
                    class=":uno: flex items-start justify-between gap-2"
                  >
                    <span class=":uno: min-w-0 break-all text-gray-700"
                      >{{ selection.tool ? toolDisplayName(selection.tool) : selection.id }}
                      <span class=":uno: text-orange-600"
                        >（{{ selection.tool ? toolStatusLabel(selection.tool) : '不可用' }}）</span
                      ></span
                    >
                    <button
                      type="button"
                      :disabled="disabled"
                      :aria-label="
                        '移除 ' + (selection.tool ? toolDisplayName(selection.tool) : selection.id)
                      "
                      class=":uno: flex-none text-gray-500 hover:text-gray-900"
                      @click="removeTool(selection.id)"
                    >
                      移除
                    </button>
                  </li>
                </ul>
              </div>
              <div
                ref="optionList"
                aria-label="工具列表"
                class=":uno: max-h-80 min-h-0 flex-1 overflow-y-auto py-1"
                @keydown="handleOptionKeydown"
              >
                <VLoading v-if="isLoading" />
                <div
                  v-else-if="isError"
                  role="alert"
                  class=":uno: flex flex-col items-center gap-2 px-3 py-5 text-sm text-gray-500"
                >
                  <span>无法加载工具，已选工具仍会保留</span>
                  <VButton size="sm" :disabled="disabled || isFetching" @click="refetch()"
                    >重试</VButton
                  >
                </div>
                <p
                  v-else-if="!visibleTools.length"
                  class=":uno: px-3 py-5 text-center text-sm text-gray-500"
                >
                  {{ keyword.trim() ? '未找到匹配工具' : '暂无匹配工具' }}
                </p>
                <template v-else>
                  <section
                    v-for="group in groups"
                    :key="group.source"
                    :aria-label="group.label + ' 提供的工具'"
                  >
                    <header
                      class=":uno: flex items-center justify-between gap-2 bg-gray-50 px-3 py-1.5 text-xs text-gray-600"
                    >
                      <span class=":uno: min-w-0 break-all font-medium">{{ group.label }}</span>
                      <span class=":uno: flex-none text-gray-400">{{ group.tools.length }}</span>
                    </header>
                    <div class=":uno: py-1">
                      <AiToolSelectOption
                        v-for="tool in group.tools"
                        :key="tool.id"
                        :tool="tool"
                        :selected="!!tool.id && selectedIds.includes(tool.id)"
                        :disabled="disabled || selectionPending"
                        @toggle="toggleTool"
                      />
                    </div>
                  </section>
                </template>
              </div>
            </div>
            <div
              class=":uno: flex flex-none items-center justify-between gap-2 border-t border-gray-100 p-2"
            >
              <RouterLink
                :to="{ name: AI_FOUNDATION_ROUTE_NAMES.TOOLS }"
                class=":uno: flex items-center gap-1.5 rounded px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                @click="isOpen = false"
              >
                <MingcuteSettings3Line class=":uno: size-4" aria-hidden="true" />管理工具
              </RouterLink>
              <div class=":uno: flex items-center gap-3">
                <span class=":uno: text-xs text-gray-500">已选 {{ selectedIds.length }} 个</span>
                <PopoverClose
                  class=":uno: rounded px-2 py-1 text-xs text-gray-700 hover:bg-gray-100"
                  >完成</PopoverClose
                >
              </div>
            </div>
          </div>
        </PopoverContent>
      </PopoverPortal>
    </PopoverRoot>
    <span :id="summaryId" class=":uno: sr-only">{{
      selectedIds.length
        ? '已选 ' + selectedIds.length + ' 个工具：' + selectedTitle
        : '尚未选择工具'
    }}</span>
    <p
      v-if="unavailableSelections.length"
      :id="warningId"
      class=":uno: mt-2 text-xs text-orange-600"
      aria-live="polite"
    >
      有 {{ unavailableSelections.length }} 个已选工具暂不可用，可展开查看或移除。
    </p>
  </div>
</template>

<style scoped>
:global(.ai-tool-selector__content) {
  z-index: 9999;
  overflow: hidden;
  border: 1px solid rgb(229 231 235);
  border-radius: 0.375rem;
  background-color: rgb(255 255 255);
  box-shadow:
    0 4px 6px -1px rgb(0 0 0 / 10%),
    0 2px 4px -2px rgb(0 0 0 / 10%);
}
</style>
