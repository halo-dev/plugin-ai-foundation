<script lang="ts" setup>
import type { FormKitFrameworkContext } from '@formkit/core'
import { computed } from 'vue'
import type { RequiredModelCapabilitiesValue } from '@/utils/capabilities'
import AiModelSelector from './AiModelSelector.vue'

const props = defineProps<{
  context: FormKitFrameworkContext<string | undefined>
}>()

const inputValue = computed({
  get: () => {
    return typeof props.context._value === 'string' ? props.context._value : ''
  },
  set: (value) => {
    props.context.node.input(value || undefined)
  },
})

const modelType = computed(() => stringProp('modelType'))
const providerName = computed(() => stringProp('providerName'))
const providerType = computed(() => stringProp('providerType'))
const placeholder = computed(() => stringProp('placeholder'))
const searchPlaceholder = computed(() => stringProp('searchPlaceholder'))
const requiredFeatures = computed(
  () => props.context.requiredFeatures as string | string[] | undefined,
)
const requiredCapabilities = computed(
  () => props.context.requiredCapabilities as RequiredModelCapabilitiesValue,
)
const enabled = computed(() => booleanProp('enabled'))
const available = computed(() => nullableBooleanProp('available', true))
const clearable = computed(() => booleanProp('clearable', true))
const fullWidth = computed(() => booleanProp('fullWidth'))
const disabled = computed(() => booleanProp('disabled') ?? false)

function stringProp(name: string) {
  const value = props.context[name]
  return typeof value === 'string' ? value : undefined
}

function booleanProp(name: string, defaultValue?: boolean) {
  const value = props.context[name]
  return typeof value === 'boolean' ? value : defaultValue
}

function nullableBooleanProp(name: string, defaultValue?: boolean | null) {
  const value = props.context[name]
  return typeof value === 'boolean' || value === null ? value : defaultValue
}
</script>

<template>
  <AiModelSelector
    v-model="inputValue"
    :model-type="modelType"
    :provider-name="providerName"
    :provider-type="providerType"
    :enabled="enabled"
    :available="available"
    :required-features="requiredFeatures"
    :required-capabilities="requiredCapabilities"
    :placeholder="placeholder"
    :search-placeholder="searchPlaceholder"
    :clearable="clearable"
    :disabled="disabled"
    :full-width="fullWidth"
  />
</template>
