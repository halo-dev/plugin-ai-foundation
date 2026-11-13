<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import ToolDetailsContent from '@/components/tools/ToolDetailsContent.vue'
import {
  toolCategoryLabel,
  toolDisplayDescription,
  toolDisplayName,
  toolSelectable,
  toolStatusLabel,
  toolStatusState,
} from '@/utils/tools'
import { VButton, VStatusDot } from '@halo-dev/components'
import { CollapsibleContent, CollapsibleRoot, CollapsibleTrigger } from 'reka-ui'
import { computed, shallowRef, useId } from 'vue'
import MingcuteDownLine from '~icons/mingcute/down-line'

const props = withDefaults(
  defineProps<{
    tool: ToolDescriptor
    selected: boolean
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{
  toggle: [id: string]
}>()

const expanded = shallowRef(false)
const detailsId = 'ai-tool-select-details-' + useId()
const name = computed(() => toolDisplayName(props.tool))
const description = computed(() => toolDisplayDescription(props.tool))
const unavailable = computed(() => !toolSelectable(props.tool))
const selectionDisabled = computed(() => props.disabled || unavailable.value)
const metadata = computed(() =>
  [...new Set([toolCategoryLabel(props.tool.category), ...(props.tool.tags || [])])].join(' · '),
)

function toggleTool() {
  if (selectionDisabled.value || !props.tool.id) return
  emit('toggle', props.tool.id)
}
</script>

<template>
  <CollapsibleRoot
    v-model:open="expanded"
    :disabled="disabled"
    :data-tool-id="tool.id"
    class=":uno: mx-1.5 min-w-0 text-[13px] leading-5"
  >
    <div
      class=":uno: min-w-0 flex items-center rounded-md transition-colors"
      :class="selected ? ':uno: bg-blue-50 text-blue-700' : ':uno: text-gray-800 hover:bg-gray-100'"
    >
      <label
        class=":uno: min-w-0 flex flex-1 items-start gap-2 px-3 py-2"
        :class="selectionDisabled ? ':uno: cursor-not-allowed' : ':uno: cursor-pointer'"
      >
        <input
          type="checkbox"
          :value="tool.id"
          :checked="selected"
          :disabled="selectionDisabled"
          :aria-label="`选择 ${name}`"
          :title="`选择 ${name}`"
          class=":uno: mt-1 flex-none"
          @change="toggleTool"
        />
        <span class=":uno: min-w-0 flex-1">
          <span class=":uno: flex items-center gap-1.5">
            <span class=":uno: min-w-0 truncate" :title="name">{{ name }}</span>
            <VStatusDot
              v-if="unavailable"
              class=":uno: flex-none"
              :state="toolStatusState(tool)"
              :text="toolStatusLabel(tool)"
            />
          </span>
          <span
            v-if="description"
            class=":uno: mt-0.5 block truncate text-xs text-gray-500"
            :title="description"
          >
            {{ description }}
          </span>
          <span
            class=":uno: mt-0.5 block truncate text-[11px] text-gray-500 leading-4"
            :title="metadata"
          >
            {{ metadata }}
          </span>
        </span>
      </label>
      <CollapsibleTrigger as-child>
        <VButton
          type="default"
          size="sm"
          class=":uno: mr-2 flex-none"
          :disabled="disabled"
          :aria-label="`${expanded ? '收起' : '查看'} ${name} 详情`"
          :aria-controls="detailsId"
        >
          <span class=":uno: inline-flex items-center gap-1">
            {{ expanded ? '收起' : '详情' }}
            <MingcuteDownLine
              class=":uno: ai-tool-select-option__chevron size-3"
              :data-expanded="expanded"
              aria-hidden="true"
            />
          </span>
        </VButton>
      </CollapsibleTrigger>
    </div>
    <div
      :id="detailsId"
      role="region"
      :aria-label="`${name} 详情`"
      :aria-hidden="expanded ? undefined : true"
      class=":uno: min-w-0"
    >
      <CollapsibleContent class=":uno: ai-tool-select-option__details min-w-0">
        <div class=":uno: min-w-0 border-t border-gray-100 px-3 py-3 text-gray-700">
          <ToolDetailsContent :tool="tool" />
        </div>
      </CollapsibleContent>
    </div>
  </CollapsibleRoot>
</template>

<style scoped>
.ai-tool-select-option__details {
  overflow: hidden;
}

.ai-tool-select-option__details[data-state='open'] {
  animation: ai-tool-details-expand 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.ai-tool-select-option__details[data-state='closed'] {
  animation: ai-tool-details-collapse 180ms cubic-bezier(0.4, 0, 1, 1);
}

.ai-tool-select-option__chevron {
  transition: transform 180ms ease;
}

.ai-tool-select-option__chevron[data-expanded='true'] {
  transform: rotate(180deg);
}

@keyframes ai-tool-details-expand {
  from {
    height: 0;
  }
  to {
    height: var(--reka-collapsible-content-height);
  }
}

@keyframes ai-tool-details-collapse {
  from {
    height: var(--reka-collapsible-content-height);
  }
  to {
    height: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ai-tool-select-option__details[data-state] {
    animation: none;
  }

  .ai-tool-select-option__chevron {
    transition: none;
  }
}
</style>
