<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import { buildToolCollections, type ToolCollection, type ToolGroup } from '@/utils/tools'
import { useMediaQuery } from '@vueuse/core'
import { computed, shallowRef, watch } from 'vue'
import ToolCatalogCard from './ToolCatalogCard.vue'
import ToolGroupCard from './ToolGroupCard.vue'

const props = withDefaults(
  defineProps<{
    groups: ToolGroup[]
    selectedIds?: string[]
    disabled?: boolean
    management?: boolean
    pendingIds?: string[]
    compact?: boolean
    keyword?: string
  }>(),
  {
    selectedIds: undefined,
    disabled: false,
    management: false,
    pendingIds: () => [],
    compact: false,
    keyword: '',
  },
)

const emit = defineEmits<{
  toggle: [id: string]
  details: [tool: ToolDescriptor]
  configure: [tool: ToolDescriptor]
  enabled: [tool: ToolDescriptor, enabled: boolean]
}>()

const selected = computed(() => new Set(props.selectedIds))
const pending = computed(() => new Set(props.pendingIds))
const collections = computed(() =>
  buildToolCollections(props.groups.flatMap((group) => group.tools)),
)
const groupCollections = computed(() => collections.value.filter((collection) => collection.group))
const wideLayout = useMediaQuery('(min-width: 1280px)')
const groupColumns = computed(() => {
  const columns: ToolCollection[][] = props.compact || !wideLayout.value ? [[]] : [[], []]
  groupCollections.value.forEach((collection, index) => {
    columns[index % columns.length]!.push(collection)
  })
  return columns.filter((column) => column.length)
})
const standaloneCollections = computed(() =>
  collections.value.filter((collection) => !collection.group),
)
const expandedKeys = shallowRef(new Set<string>())

watch(
  [() => props.keyword, collections],
  ([keyword, collections]) => {
    const groupKeys = new Set(
      collections.filter((collection) => collection.group).map((collection) => collection.key),
    )
    const expanded = new Set([...expandedKeys.value].filter((key) => groupKeys.has(key)))
    if (keyword.trim()) for (const key of groupKeys) expanded.add(key)
    expandedKeys.value = expanded
  },
  { immediate: true },
)

function setExpanded(key: string, expanded: boolean) {
  if (props.disabled) return
  const keys = new Set(expandedKeys.value)
  if (expanded) keys.add(key)
  else keys.delete(key)
  expandedKeys.value = keys
}
</script>

<template>
  <div class=":uno: min-w-0 p-3 space-y-3">
    <div
      v-if="groupCollections.length"
      class=":uno: grid grid-cols-1 items-start gap-3"
      :class="{ ':uno: xl:grid-cols-2': !compact }"
    >
      <ul
        v-for="(column, index) in groupColumns"
        :key="index"
        :aria-label="
          groupColumns.length > 1 ? `工具组${index === 0 ? '左' : '右'}列` : '工具组列表'
        "
        class=":uno: m-0 min-w-0 flex flex-col list-none gap-3 p-0"
      >
        <li v-for="collection in column" :key="collection.key" class=":uno: min-w-0">
          <ToolGroupCard
            :collection="collection"
            :expanded="expandedKeys.has(collection.key)"
            :selected-ids="selectedIds"
            :disabled="disabled"
            :management="management"
            :pending-ids="pendingIds"
            :compact="compact"
            @update:expanded="setExpanded(collection.key, $event)"
            @toggle="emit('toggle', $event)"
            @details="emit('details', $event)"
            @configure="emit('configure', $event)"
            @enabled="(tool, enabled) => emit('enabled', tool, enabled)"
          />
        </li>
      </ul>
    </div>
    <ul
      v-if="standaloneCollections.length"
      aria-label="工具列表"
      class=":uno: grid grid-cols-1 m-0 list-none gap-3 p-0"
      :class="{ ':uno: sm:grid-cols-2 xl:grid-cols-4': !compact }"
    >
      <li v-for="collection in standaloneCollections" :key="collection.key" class=":uno: min-w-0">
        <ToolCatalogCard
          v-if="collection.tools[0]"
          :tool="collection.tools[0]"
          :selected="
            selectedIds === undefined ? undefined : selected.has(collection.tools[0].id || '')
          "
          :disabled="disabled"
          :management="management"
          :pending="pending.has(collection.tools[0].id || '')"
          :compact="compact"
          @toggle="emit('toggle', $event)"
          @details="emit('details', $event)"
          @configure="emit('configure', $event)"
          @enabled="(tool, enabled) => emit('enabled', tool, enabled)"
        />
      </li>
    </ul>
  </div>
</template>
