<script setup lang="ts">
import type { FormKitFrameworkContext } from '@formkit/core'
import { computed } from 'vue'
import AiToolSelector from './AiToolSelector.vue'

const props = defineProps<{ context: FormKitFrameworkContext<string[]> }>()
const value = computed({
  get: () =>
    Array.isArray(props.context._value)
      ? props.context._value.filter((id): id is string => typeof id === 'string')
      : [],
  set: (ids: string[]) => props.context.node.input(ids),
})
const disabled = computed(() => !!props.context.disabled)
const pluginName = computed(() => stringProp('pluginName'))
const category = computed(() => stringProp('category'))
const placeholder = computed(() => stringProp('placeholder'))
const searchPlaceholder = computed(() => stringProp('searchPlaceholder'))
const fullWidth = computed(() => props.context.fullWidth === true)

function stringProp(name: string) {
  const value = props.context[name]
  return typeof value === 'string' ? value : undefined
}
</script>

<template>
  <AiToolSelector
    :id="stringProp('id')"
    v-model="value"
    :plugin-name="pluginName"
    :category="category"
    :placeholder="placeholder"
    :search-placeholder="searchPlaceholder"
    :disabled="disabled"
    :full-width="fullWidth"
    :label="stringProp('label') || '选择工具'"
    :show-label="false"
  />
</template>
