<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import { toolCategoryLabel, toolStatus, type ToolCollection } from '@/utils/tools'
import { VCard } from '@halo-dev/components'
import { computed, useId } from 'vue'
import MingcuteDownLine from '~icons/mingcute/down-line'
import ToolCatalogCard from './ToolCatalogCard.vue'

const props = withDefaults(
  defineProps<{
    collection: ToolCollection
    selectedIds?: string[]
    disabled?: boolean
    management?: boolean
    pendingIds?: string[]
    compact?: boolean
  }>(),
  {
    selectedIds: undefined,
    disabled: false,
    management: false,
    pendingIds: () => [],
    compact: false,
  },
)

const expanded = defineModel<boolean>('expanded', { default: false })
const emit = defineEmits<{
  toggle: [id: string]
  details: [tool: ToolDescriptor]
  configure: [tool: ToolDescriptor]
  enabled: [tool: ToolDescriptor, enabled: boolean]
}>()

const contentId = 'tool-group-' + useId()
const name = computed(
  () => props.collection.group?.displayName || props.collection.group?.id || '工具组',
)
const categories = computed(() => [
  ...new Set(props.collection.tools.map((tool) => toolCategoryLabel(tool.category))),
])
const selected = computed(() => new Set(props.selectedIds))
const pending = computed(() => new Set(props.pendingIds))
const statusSummary = computed(() => {
  const counts = { ENABLED: 0, DISABLED: 0, UNCONFIGURED: 0 }
  for (const tool of props.collection.tools) counts[toolStatus(tool)]++
  return [
    counts.ENABLED && `${counts.ENABLED} 个已启用`,
    counts.DISABLED && `${counts.DISABLED} 个已禁用`,
    counts.UNCONFIGURED && `${counts.UNCONFIGURED} 个未配置`,
  ]
    .filter(Boolean)
    .join(' · ')
})

function toggleExpanded() {
  if (!props.disabled) expanded.value = !expanded.value
}
</script>

<template>
  <VCard
    class=":uno: min-w-0 w-full"
    :body-class="['!p-0']"
    :data-tool-group-id="collection.group?.id"
    :data-expanded="expanded"
  >
    <button
      type="button"
      :disabled="disabled"
      :aria-label="`${expanded ? '收起' : '展开'} ${name} 工具组`"
      :aria-expanded="expanded"
      :aria-controls="contentId"
      class=":uno: focus-visible:ring-primary w-full flex items-start justify-between gap-3 rounded-md p-4 text-left transition-colors disabled:cursor-not-allowed hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset"
      @click="toggleExpanded"
    >
      <span class=":uno: min-w-0 flex-1">
        <span class=":uno: flex flex-wrap items-center gap-x-2 gap-y-1">
          <span
            role="heading"
            aria-level="3"
            class=":uno: min-w-0 break-words text-sm text-gray-950 font-medium"
          >
            {{ name }}
          </span>
          <span class=":uno: text-xs text-gray-500">{{ collection.tools.length }} 个工具</span>
          <span
            v-for="category in categories"
            :key="category"
            class=":uno: text-xs text-gray-500"
            >{{ category }}</span
          >
          <span class=":uno: text-xs text-gray-500">{{ statusSummary }}</span>
        </span>
        <span
          v-if="collection.group?.description"
          class=":uno: mt-1 block break-words text-xs text-gray-500"
        >
          {{ collection.group.description }}
        </span>
      </span>
      <span
        aria-hidden="true"
        class=":uno: inline-flex flex-none items-center gap-1 text-xs text-gray-600"
      >
        {{ expanded ? '收起' : '展开' }}
        <MingcuteDownLine
          class=":uno: size-3"
          :class="{ ':uno: rotate-180': expanded }"
          aria-hidden="true"
        />
      </span>
    </button>
    <div :id="contentId" class=":uno: min-w-0 px-4" :class="{ ':uno: pb-4': expanded }">
      <ul
        v-if="expanded"
        :aria-label="`${name}中的工具`"
        class=":uno: grid grid-cols-1 m-0 list-none gap-3 border-t border-gray-100 p-0 pt-3"
        :class="{ ':uno: sm:grid-cols-2': !compact && collection.tools.length > 1 }"
      >
        <li v-for="tool in collection.tools" :key="tool.id" class=":uno: min-w-0">
          <ToolCatalogCard
            :tool="tool"
            :selected="selectedIds === undefined ? undefined : selected.has(tool.id || '')"
            :disabled="disabled"
            :management="management"
            :pending="pending.has(tool.id || '')"
            :compact="compact"
            @toggle="emit('toggle', $event)"
            @details="emit('details', $event)"
            @configure="emit('configure', $event)"
            @enabled="(tool, enabled) => emit('enabled', tool, enabled)"
          />
        </li>
      </ul>
    </div>
  </VCard>
</template>
