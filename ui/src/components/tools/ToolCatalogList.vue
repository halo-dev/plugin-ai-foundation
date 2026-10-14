<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import {
  toolCategoryLabel,
  toolDisplayDescription,
  toolDisplayName,
  toolSelectable,
  toolStatusLabel,
  toolStatusState,
  type CatalogTool,
  type ToolGroup,
} from '@/utils/tools'
import { VButton, VCard, VStatusDot, VSwitch, VTag } from '@halo-dev/components'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    groups: ToolGroup[]
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

const emit = defineEmits<{
  toggle: [id: string]
  details: [tool: ToolDescriptor]
  configure: [tool: ToolDescriptor]
  enabled: [tool: ToolDescriptor, enabled: boolean]
}>()

const selected = computed(() => new Set(props.selectedIds))
const pending = computed(() => new Set(props.pendingIds))
const cards = computed(() =>
  props.groups.flatMap((group) =>
    group.tools.map((tool) => ({
      tool,
      name: toolDisplayName(tool),
      description: toolDisplayDescription(tool),
      statusLabel: toolStatusLabel(tool),
      statusState: toolStatusState(tool),
      tags: [...new Set([toolCategoryLabel(tool.category), ...(tool.tags || [])])],
      selected: !!tool.id && selected.value.has(tool.id),
      pending: !!tool.id && pending.value.has(tool.id),
      selectable: toolSelectable(tool),
      configurable: !!tool.configurationSchema?.length,
    })),
  ),
)

function cardDisabled(tool: CatalogTool) {
  return props.disabled || (!!tool.id && pending.value.has(tool.id))
}

function toggleTool(tool: CatalogTool) {
  if (cardDisabled(tool) || !toolSelectable(tool) || !tool.id) return
  emit('toggle', tool.id)
}

function changeEnabled(tool: CatalogTool, enabled: boolean) {
  if (cardDisabled(tool) || !tool.id) return
  emit('enabled', tool, enabled)
}

function showDetails(tool: CatalogTool) {
  if (!cardDisabled(tool)) emit('details', tool)
}

function configureTool(tool: CatalogTool) {
  if (!cardDisabled(tool) && tool.configurationSchema?.length) emit('configure', tool)
}
</script>

<template>
  <ul
    class=":uno: grid grid-cols-1 m-0 list-none gap-3 p-3"
    :class="{ ':uno: sm:grid-cols-2 xl:grid-cols-3': !compact }"
    aria-label="工具列表"
  >
    <li v-for="card in cards" :key="card.tool.id" class=":uno: min-w-0">
      <VCard
        class=":uno: h-full"
        :class="{ ':uno: ring-primary': card.selected }"
        :data-tool-id="card.tool.id"
      >
        <div class=":uno: flex items-start gap-3">
          <input
            v-if="selectedIds !== undefined && card.tool.id"
            type="checkbox"
            :checked="card.selected"
            :disabled="disabled || card.pending || !card.selectable"
            :aria-label="`选择 ${card.name}`"
            class=":uno: mt-1 flex-none"
            @change="toggleTool(card.tool)"
          />
          <div class=":uno: min-w-0 flex flex-1 items-center gap-2">
            <h3 class=":uno: m-0 min-w-0 break-words text-sm text-gray-950 font-medium">
              {{ card.name }}
            </h3>
            <VStatusDot
              class=":uno: flex-none"
              :state="card.statusState"
              :text="card.statusLabel"
            />
          </div>
          <VSwitch
            v-if="management"
            :model-value="card.tool.enabled !== false"
            :disabled="disabled || card.pending"
            :loading="card.pending"
            :aria-label="`启用 ${card.name}`"
            @update:model-value="changeEnabled(card.tool, $event)"
          />
        </div>
        <div class=":uno: mt-2 break-all text-xs text-gray-500">
          来源：{{ card.tool.pluginName }}
        </div>
        <div v-if="card.tags.length" class=":uno: mt-2 flex flex-wrap gap-1.5">
          <VTag v-for="tag in card.tags" :key="tag">{{ tag }}</VTag>
        </div>
        <p
          v-if="card.description"
          class=":uno: my-3 break-words text-sm text-gray-600"
          :class="{ ':uno: line-clamp-2': compact, ':uno: line-clamp-3': !compact }"
        >
          {{ card.description }}
        </p>
        <div class=":uno: mt-1 break-all text-xs text-gray-400">{{ card.tool.id }}</div>
        <template #footer>
          <div class=":uno: flex items-center justify-end gap-2">
            <VButton
              v-if="management && card.configurable"
              size="sm"
              :disabled="disabled || card.pending"
              :aria-label="`配置 ${card.name}`"
              @click="configureTool(card.tool)"
            >
              配置
            </VButton>
            <VButton
              size="sm"
              :disabled="disabled || card.pending"
              :aria-label="`查看 ${card.name} 详情`"
              @click="showDetails(card.tool)"
            >
              详情
            </VButton>
          </div>
        </template>
      </VCard>
    </li>
  </ul>
</template>

<style scoped>
:deep(.card-body) {
  flex-grow: 1;
}
</style>
