<script setup lang="ts">
import { AiModelSpecModelTypeEnum } from '@/api/generated'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import { MODEL_FEATURE_OPTIONS, MODEL_TYPE_OPTIONS } from '@/types'
import type { ModelFormState } from '@/types/form'
import { submitForm } from '@formkit/core'
import { computed } from 'vue'

const props = defineProps<{
  formState?: ModelFormState
  providerType: string
}>()

const emit = defineEmits<{
  (e: 'submit', data: ModelFormState): void
}>()

const isEditing = computed(() => !!props.formState)

const { data: providerTypes } = useProviderTypesFetch()

const selectedProviderType = computed(() => {
  return providerTypes.value?.find((type) => type.providerType === props.providerType)
})

const modelTypeOptions = computed(() => {
  const supportedTypes = selectedProviderType.value?.supportedModelTypes || []
  const options = supportedTypes.length
    ? MODEL_TYPE_OPTIONS.filter((item) => supportedTypes.includes(item.value))
    : MODEL_TYPE_OPTIONS
  return options.map((item) => ({ value: item.value, label: item.label }))
})

const featureOptions = computed(() => {
  const supportedFeatures = selectedProviderType.value?.supportedFeatures || []
  const options = supportedFeatures.length
    ? MODEL_FEATURE_OPTIONS.filter((item) => supportedFeatures.includes(item.value))
    : MODEL_FEATURE_OPTIONS
  return options.map((item) => ({ value: item.value, label: item.label }))
})

const defaultModelType = computed(() => {
  return (
    props.formState?.modelType ||
    modelTypeOptions.value[0]?.value ||
    AiModelSpecModelTypeEnum.Language
  )
})

function onSubmit(data: ModelFormState) {
  emit('submit', data)
}

defineExpose({
  submit: () => submitForm('model-form'),
})
</script>

<template>
  <FormKit id="model-form" type="form" @submit="onSubmit">
    <FormKit
      type="text"
      name="modelId"
      label="模型 ID"
      validation="required"
      placeholder="例如: gpt-4o"
      :disabled="isEditing"
      :value="formState?.modelId"
    />

    <FormKit
      type="text"
      name="displayName"
      label="显示名称"
      validation="required"
      placeholder="例如: GPT-4o"
      :value="formState?.displayName"
    />

    <FormKit
      type="select"
      name="modelType"
      label="模型类型"
      validation="required"
      :options="modelTypeOptions"
      :value="defaultModelType"
    />

    <FormKit
      type="checkbox"
      name="features"
      label="高级特性"
      :options="featureOptions"
      :value="formState?.features"
    />

    <FormKit type="switch" name="enabled" label="启用" :value="formState?.enabled ?? true" />
  </FormKit>
</template>
