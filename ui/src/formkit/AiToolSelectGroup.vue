<script setup lang="ts">
import type { CatalogTool, ToolCollection } from '@/utils/tools'
import { VButton } from '@halo-dev/components'
import { CollapsibleContent, CollapsibleRoot, CollapsibleTrigger } from 'reka-ui'
import { computed, shallowRef, useId } from 'vue'
import MingcuteDownLine from '~icons/mingcute/down-line'
import AiToolSelectOption from './AiToolSelectOption.vue'
import { toolGroupSelection, toolPresetSelection } from './ai-tool-selection'

const props = withDefaults(
  defineProps<{
    collection: ToolCollection
    catalog: CatalogTool[]
    selectedIds: string[]
    disabled?: boolean
    searching?: boolean
  }>(),
  { disabled: false, searching: false },
)
const emit = defineEmits<{
  toggle: [id: string]
  select: [ids: string[]]
  remove: [ids: string[]]
  preset: [ids: string[]]
}>()
const expanded = shallowRef(false)
const contentId = 'ai-tool-select-group-' + useId()
const name = computed(() => props.collection.group?.displayName || props.collection.group?.id || '')
const selection = computed(() => toolGroupSelection(props.collection.tools, props.selectedIds))
const open = computed({
  get: () => props.searching || expanded.value,
  set: (value) => {
    if (!props.searching) expanded.value = value
  },
})
const presets = computed(() =>
  (props.collection.group?.presets || []).map((preset) => {
    const selection = toolPresetSelection(preset.toolIds || [], props.catalog, props.selectedIds)
    return {
      ...preset,
      name: preset.displayName || preset.id,
      ...selection,
    }
  }),
)

function toggleGroup(event: Event) {
  // Mixed -> empty keeps checked=false, so restore the controlled DOM state before emitting.
  if (event.currentTarget instanceof HTMLInputElement) {
    event.currentTarget.checked = selection.value.checked
    event.currentTarget.indeterminate = selection.value.indeterminate
  }
  if (props.disabled || !selection.value.canToggle) return
  if (selection.value.removing) emit('remove', selection.value.ids)
  else emit('select', selection.value.selectableIds)
}

function togglePreset(ids: string[]) {
  if (props.disabled) return
  if (!toolPresetSelection(ids, props.catalog, props.selectedIds).canToggle) return
  emit('preset', ids)
}
</script>

<template>
  <CollapsibleRoot
    v-model:open="open"
    :disabled="disabled"
    :data-tool-group="collection.group?.id"
    class=":uno: mx-1.5 my-1 min-w-0 border border-gray-100 rounded-md"
  >
    <div class=":uno: min-w-0 flex items-center gap-2 px-3 py-2">
      <label class=":uno: flex flex-none items-center">
        <input
          type="checkbox"
          :aria-label="`选择工具组 ${name}`"
          :checked="selection.checked"
          :indeterminate="selection.indeterminate"
          :aria-checked="selection.indeterminate ? 'mixed' : selection.checked"
          :disabled="disabled || !selection.canToggle"
          @change="toggleGroup"
        />
      </label>
      <CollapsibleTrigger as-child>
        <button
          type="button"
          :disabled="disabled || searching"
          :aria-label="`${open ? '收起' : '展开'}工具组 ${name}`"
          :aria-controls="contentId"
          class=":uno: min-w-0 flex flex-1 items-center gap-1.5 text-left text-sm text-gray-800 font-medium disabled:cursor-default"
        >
          <span class=":uno: min-w-0 flex-1 truncate" :title="name">{{ name }}</span>
          <MingcuteDownLine
            class=":uno: size-3.5 flex-none transition-transform duration-200"
            :class="open ? ':uno: rotate-180' : ''"
            aria-hidden="true"
          />
        </button>
      </CollapsibleTrigger>
    </div>
    <div class=":uno: min-w-0 px-3 pb-2 text-xs text-gray-500 space-y-1.5">
      <p v-if="open && collection.group?.description" class=":uno: break-words">
        {{ collection.group.description }}
      </p>
      <div class=":uno: flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <span>
          可选 {{ selection.selectableIds.length }}
          <template v-if="selection.unavailableCount">
            · 不可选 {{ selection.unavailableCount }}</template
          >
          · 已选 {{ selection.selectedCount }}
        </span>
      </div>
      <p v-if="searching">组选择仅作用于当前搜索结果；预设切换作用于完整预设。</p>
      <div v-if="presets.length" class=":uno: flex flex-wrap items-center gap-1.5">
        <VButton
          v-for="preset in presets"
          :key="preset.id"
          :type="preset.checked ? 'secondary' : 'default'"
          size="sm"
          :aria-label="`切换预设 ${preset.name}`"
          :aria-pressed="preset.checked"
          :title="preset.description"
          :disabled="disabled || !preset.canToggle"
          @click="togglePreset(preset.toolIds || [])"
        >
          <span class=":uno: inline-flex flex-wrap items-center gap-1 whitespace-normal text-left">
            <span>{{ preset.name }}</span>
            <span v-if="preset.addingCount">+{{ preset.addingCount }}</span>
            <span v-else-if="preset.checked">已选</span>
            <span v-else-if="preset.removing">取消已选</span>
            <span v-if="preset.unavailableCount">{{ preset.unavailableCount }}不可用</span>
          </span>
        </VButton>
      </div>
    </div>
    <CollapsibleContent :id="contentId" class=":uno: ai-tool-select-group__content min-w-0">
      <AiToolSelectOption
        v-for="tool in collection.tools"
        :key="tool.id"
        :tool="tool"
        :selected="!!tool.id && selectedIds.includes(tool.id)"
        :disabled="disabled"
        @toggle="emit('toggle', $event)"
      />
    </CollapsibleContent>
  </CollapsibleRoot>
</template>

<style scoped>
.ai-tool-select-group__content {
  overflow: hidden;
}

.ai-tool-select-group__content[data-state='open'] {
  animation: ai-tool-group-expand 180ms ease-out;
}

.ai-tool-select-group__content[data-state='closed'] {
  animation: ai-tool-group-collapse 180ms ease-in;
}

@keyframes ai-tool-group-expand {
  from {
    height: 0;
  }
  to {
    height: var(--reka-collapsible-content-height);
  }
}

@keyframes ai-tool-group-collapse {
  from {
    height: var(--reka-collapsible-content-height);
  }
  to {
    height: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-tool-select-group__content[data-state] {
    animation: none;
  }
}
</style>
