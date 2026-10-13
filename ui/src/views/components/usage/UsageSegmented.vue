<script setup lang="ts">
import { ToggleGroupItem, ToggleGroupRoot, type AcceptableValue } from 'reka-ui'
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'

export interface UsageSegmentedOption {
  label: string
  value: string
}

const props = defineProps<{
  modelValue?: string
  options: UsageSegmentedOption[]
  ariaLabel?: string
}>()

const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>()

const root = shallowRef<ComponentPublicInstance>()
const thumb = shallowRef({ left: 2, width: 0, ready: false })

const activeValue = computed(() => props.modelValue)

let observer: ResizeObserver | undefined

function rootEl() {
  return root.value?.$el as HTMLElement | undefined
}

async function measure() {
  await nextTick()
  const container = rootEl()
  const button = container?.querySelector<HTMLElement>('[data-state="on"]')
  if (!container || !button) {
    return
  }
  thumb.value = { left: button.offsetLeft, width: button.offsetWidth, ready: true }
}

function update(value: AcceptableValue | AcceptableValue[]) {
  // Single-select segments cannot be deselected: ignore empty values and keep the current option
  if (typeof value !== 'string' || !value) {
    return
  }
  emit('update:modelValue', value)
}

onMounted(() => {
  measure()
  const el = rootEl()
  if (el) {
    observer = new ResizeObserver(measure)
    observer.observe(el)
  }
})

watch([activeValue, () => props.options], measure)

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <ToggleGroupRoot
    ref="root"
    type="single"
    :model-value="modelValue"
    class=":uno: usage-segmented"
    :aria-label="ariaLabel"
    @update:model-value="update"
  >
    <span
      v-show="thumb.ready"
      class=":uno: usage-segmented-thumb"
      :style="{ transform: `translateX(${thumb.left - 2}px)`, width: `${thumb.width}px` }"
      aria-hidden="true"
    ></span>
    <ToggleGroupItem
      v-for="option in options"
      :key="option.value"
      :value="option.value"
      class=":uno: usage-segmented-option"
    >
      {{ option.label }}
    </ToggleGroupItem>
  </ToggleGroupRoot>
</template>

<style scoped>
.usage-segmented {
  position: relative;
  display: inline-flex;
  padding: 2px;
  border-radius: 7px;
  background: #f3f4f6;
}
.usage-segmented-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  height: calc(100% - 4px);
  border-radius: 5px;
  background: #fff;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.12),
    0 0 0 0.5px rgba(0, 0, 0, 0.06);
  transition:
    transform 0.18s cubic-bezier(0.2, 0.8, 0.2, 1),
    width 0.18s cubic-bezier(0.2, 0.8, 0.2, 1);
  pointer-events: none;
}
.usage-segmented-option {
  position: relative;
  height: 26px;
  padding: 0 12px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s;
}
.usage-segmented-option:hover {
  color: #374151;
}
.usage-segmented-option[data-state='on'] {
  color: #111827;
}
.usage-segmented-option:focus-visible {
  outline: 2px solid #9ca3af;
  outline-offset: 1px;
}
</style>
