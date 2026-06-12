<script setup lang="ts">
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ModelFormState } from '@/types/form'
import {
  defaultModelTypeForProviderType,
  modelFeatureOptionsForProviderType,
  modelTypeOptionsForProviderType,
} from '@/utils/model'
import type { FormKitTypeDefinition } from '@formkit/core'
import { submitForm } from '@formkit/core'
import { computed } from 'vue'

const props = defineProps<{
  formState?: ModelFormState
  providerType: string
  modelName?: string
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
  return modelTypeOptionsForProviderType(selectedProviderType.value).map((item) => ({
    value: item.value,
    label: item.label,
  }))
})

const featureOptions = computed(() => {
  return modelFeatureOptionsForProviderType(selectedProviderType.value).map((item) => ({
    value: item.value,
    label: item.label,
  }))
})

const defaultModelType = computed(() => {
  return defaultModelTypeForProviderType(selectedProviderType.value, props.formState?.modelType)
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
      v-if="isEditing"
      type="text"
      name="modelName"
      label="内部模型 ID"
      help="Halo 内用于识别此模型的 ID，调用 AI Foundation SDK 时传递此值。"
      disabled
      :value="modelName"
    />

    <FormKit
      type="text"
      name="modelId"
      label="供应商模型 ID"
      help="调用第三方供应商时传递的模型 ID，例如 OpenAI 的 gpt-4o。"
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

    <FormKit
      :type="'switch' as unknown as FormKitTypeDefinition<boolean>"
      name="enabled"
      label="启用"
      :value="formState?.enabled ?? true"
    />
  </FormKit>
</template>
