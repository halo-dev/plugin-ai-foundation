<script lang="ts" setup>
import { computed } from 'vue'

export interface Tab {
  label: string
  value: string
}

const props = defineProps<{
  tabs: Tab[]
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const activeValue = computed({
  get: () => props.modelValue ?? props.tabs[0]?.value ?? '',
  set: (val) => emit('update:modelValue', val),
})
</script>

<template>
  <div
    class=":uno: h-10 w-full inline-flex items-baseline justify-start rounded-lg bg-gray-200/50 p-1 sm:w-auto"
  >
    <button
      v-for="item in tabs"
      :key="item.value"
      type="button"
      class=":uno: group h-8 min-w-[32px] w-full inline-flex items-center justify-center gap-1.5 whitespace-nowrap px-4 py-2 align-middle text-xs font-semibold transition-all duration-300 ease-in-out sm:w-auto disabled:cursor-not-allowed disabled:text-slate-400"
      :class="{
        ':uno: bg-transparent text-slate-600 hover:text-blue-950': activeValue !== item.value,
        ':uno: rounded-md bg-white text-slate-950 drop-shadow': activeValue === item.value,
      }"
      @click="activeValue = item.value"
    >
      <div>{{ item.label }}</div>
    </button>
  </div>
</template>
