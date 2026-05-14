<script setup lang="ts">
import { CAPABILITY_OPTIONS } from '@/types'
import type { ModelFormState } from '@/types/form'
import { submitForm } from '@formkit/core'
import { computed } from 'vue'

const props = defineProps<{
  formState?: ModelFormState
}>()

const emit = defineEmits<{
  (e: 'submit', data: ModelFormState): void
}>()

const isEditing = computed(() => !!props.formState)

const capabilityOptions = CAPABILITY_OPTIONS.map((c) => ({
  value: c.value,
  label: c.label,
}))

const endpointTypeOptions = [
  { value: 'openai-chat', label: 'OpenAI Chat' },
  { value: 'openai-embedding', label: 'OpenAI Embedding' },
  { value: 'ollama-chat', label: 'Ollama Chat' },
]

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
      type="text"
      name="group"
      label="分组"
      placeholder="例如: chat, embedding"
      :value="formState?.group"
    />

    <FormKit
      type="checkbox"
      name="capabilities"
      label="能力标签"
      :options="capabilityOptions"
      :value="formState?.capabilities"
    />

    <FormKit
      type="select"
      name="endpointType"
      label="Endpoint 类型"
      :options="endpointTypeOptions"
      :value="formState?.endpointType"
    />

    <FormKit
      type="switch"
      name="supportedTextDelta"
      label="支持流式输出"
      :value="formState?.supportedTextDelta ?? true"
    />

    <FormKit type="switch" name="enabled" label="启用" :value="formState?.enabled ?? true" />
  </FormKit>
</template>
