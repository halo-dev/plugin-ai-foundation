<script setup lang="ts">
import type { ModelOption } from '@/api/generated'
import { capabilityUnavailableDetailsLabel } from '@/utils/capabilities'
import { SelectItem, SelectItemIndicator, SelectItemText } from 'reka-ui'
import MingcuteCheckLine from '~icons/mingcute/check-line'
import {
  isModelOptionSelectable,
  modelDetailLabels,
  modelOptionDisplayName,
  modelOptionUnavailableReasonLabel,
  shouldShowModelDetails,
  shouldShowModelId,
} from './ai-model-selector'

const props = defineProps<{
  model: ModelOption
  selected: boolean
}>()

const emit = defineEmits<{
  select: [model: ModelOption]
}>()
</script>

<template>
  <SelectItem
    :value="props.model.name!"
    :text-value="modelOptionDisplayName(props.model)"
    :disabled="!props.model.name || !isModelOptionSelectable(props.model)"
    :data-ai-model-selectable="
      props.model.name && isModelOptionSelectable(props.model) ? '' : undefined
    "
    class=":uno: relative mx-1.5 flex cursor-pointer select-none items-center gap-1.5 rounded-md py-2 pl-3 pr-2 text-[13px] text-gray-800 leading-5 outline-none transition-colors focus:bg-gray-100 hover:bg-gray-100"
    :class="[
      props.selected ? ':uno: bg-blue-50 font-medium text-blue-700' : '',
      !props.model.name || !isModelOptionSelectable(props.model)
        ? ':uno: cursor-not-allowed opacity-50'
        : '',
    ]"
    @select="emit('select', props.model)"
  >
    <SelectItemText as-child>
      <span class=":uno: min-w-0 flex-1">
        <span class=":uno: flex items-center gap-1.5">
          <span class=":uno: min-w-0 truncate leading-5">
            {{ modelOptionDisplayName(props.model) }}
          </span>
          <span
            v-if="shouldShowModelId(props.model)"
            class=":uno: flex-none text-[11px] leading-4 opacity-50"
          >
            {{ props.model.modelId }}
          </span>
        </span>
        <span
          v-if="shouldShowModelDetails(props.model)"
          class=":uno: mt-1 flex flex-wrap items-center text-[11px] text-gray-500 leading-4"
        >
          <template v-for="(detail, index) in modelDetailLabels(props.model)" :key="detail">
            <span v-if="index" class=":uno: mx-1 text-gray-300" aria-hidden="true">·</span>
            <span>{{ detail }}</span>
          </template>
          <span v-if="!isModelOptionSelectable(props.model)" class=":uno: ml-2 text-red-600">
            {{
              capabilityUnavailableDetailsLabel(props.model) ||
              modelOptionUnavailableReasonLabel(props.model.unavailableReason)
            }}
          </span>
        </span>
      </span>
    </SelectItemText>

    <SelectItemIndicator class=":uno: flex-none text-blue-600">
      <MingcuteCheckLine class=":uno: size-3.5" aria-hidden="true" />
    </SelectItemIndicator>
  </SelectItem>
</template>
