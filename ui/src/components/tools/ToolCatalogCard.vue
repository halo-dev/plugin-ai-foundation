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
} from '@/utils/tools'
import { VButton, VCard, VStatusDot, VSwitch, VTag } from '@halo-dev/components'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    tool: CatalogTool
    selected?: boolean
    disabled?: boolean
    management?: boolean
    pending?: boolean
    compact?: boolean
  }>(),
  { selected: undefined, disabled: false, management: false, pending: false, compact: false },
)

const emit = defineEmits<{
  toggle: [id: string]
  details: [tool: ToolDescriptor]
  configure: [tool: ToolDescriptor]
  enabled: [tool: ToolDescriptor, enabled: boolean]
}>()

const name = computed(() => toolDisplayName(props.tool))
const description = computed(() => toolDisplayDescription(props.tool))
const disabled = computed(() => props.disabled || props.pending)
const tags = computed(() => [
  ...new Set([toolCategoryLabel(props.tool.category), ...(props.tool.tags || [])]),
])

function toggleTool() {
  if (!disabled.value && toolSelectable(props.tool) && props.tool.id) emit('toggle', props.tool.id)
}

function changeEnabled(enabled: boolean) {
  if (!disabled.value && props.tool.id) emit('enabled', props.tool, enabled)
}

function showDetails() {
  if (!disabled.value) emit('details', props.tool)
}

function configureTool() {
  if (!disabled.value && props.tool.configurationSchema?.length) emit('configure', props.tool)
}
</script>

<template>
  <VCard class=":uno: h-full" :class="{ ':uno: ring-primary': selected }" :data-tool-id="tool.id">
    <div class=":uno: flex items-start gap-3">
      <input
        v-if="selected !== undefined && tool.id"
        type="checkbox"
        :checked="selected"
        :disabled="disabled || !toolSelectable(tool)"
        :aria-label="`选择 ${name}`"
        class=":uno: mt-1 flex-none"
        @change="toggleTool"
      />
      <div class=":uno: min-w-0 flex flex-1 items-center gap-2">
        <h3 class=":uno: m-0 min-w-0 break-words text-sm text-gray-950 font-medium">
          {{ name }}
        </h3>
        <VStatusDot
          class=":uno: flex-none"
          :state="toolStatusState(tool)"
          :text="toolStatusLabel(tool)"
        />
      </div>
      <VSwitch
        v-if="management"
        :model-value="tool.enabled !== false"
        :disabled="disabled"
        :loading="pending"
        :aria-label="`启用 ${name}`"
        @update:model-value="changeEnabled"
      />
    </div>
    <div class=":uno: mt-2 break-all text-xs text-gray-500">来源：{{ tool.pluginName }}</div>
    <div v-if="tags.length" class=":uno: mt-2 flex flex-wrap gap-1.5">
      <VTag v-for="tag in tags" :key="tag">{{ tag }}</VTag>
    </div>
    <p
      v-if="description"
      class=":uno: my-3 break-words text-sm text-gray-600"
      :class="compact ? ':uno: line-clamp-2' : ':uno: line-clamp-3'"
    >
      {{ description }}
    </p>
    <div class=":uno: mt-1 break-all text-xs text-gray-400">{{ tool.id }}</div>
    <template #footer>
      <div class=":uno: flex items-center justify-end gap-2">
        <VButton
          v-if="management && tool.configurationSchema?.length"
          size="sm"
          :disabled="disabled"
          :aria-label="`配置 ${name}`"
          @click="configureTool"
        >
          配置
        </VButton>
        <VButton
          size="sm"
          :disabled="disabled"
          :aria-label="`查看 ${name} 详情`"
          @click="showDetails"
        >
          详情
        </VButton>
      </div>
    </template>
  </VCard>
</template>

<style scoped>
:deep(.card-body) {
  flex-grow: 1;
}
</style>
