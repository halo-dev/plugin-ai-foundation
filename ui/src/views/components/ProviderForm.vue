<script setup lang="ts">
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ProviderFormState } from '@/types/form'
import { submitForm } from '@formkit/core'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  formState?: ProviderFormState
}>()

const emit = defineEmits<{
  (e: 'submit', data: ProviderFormState): void
}>()

const { data: providerTypes } = useProviderTypesFetch()

const providerTypeOptions = computed(() => {
  if (!providerTypes.value) return []
  return providerTypes.value.map((t) => ({
    value: t.providerType,
    label: t.displayName,
  }))
})

const providerType = ref()
const displayName = ref()

const selectedProviderType = computed(() => {
  return providerTypes.value?.find((t) => t.providerType === providerType.value)
})

watch(
  () => providerType.value,
  (value) => {
    if (value) {
      displayName.value = selectedProviderType.value?.displayName
    }
  },
)

const requiresBaseUrl = computed(() => {
  return selectedProviderType.value?.requiresBaseUrl ?? false
})

const baseUrlPlaceholder = computed(() => {
  return selectedProviderType.value?.defaultBaseUrl || 'https://api.example.com/v1'
})

const isEditing = computed(() => !!props.formState)

function onSubmit(data: ProviderFormState) {
  emit('submit', data)
}

const providerTypeHelp = computed(() => {
  if (selectedProviderType.value?.documentationUrl) {
    return `${selectedProviderType.value.displayName} 文档地址：${selectedProviderType.value.documentationUrl}`
  }
  return ''
})

defineExpose({
  submit: () => submitForm('provider-form'),
})
</script>

<template>
  <FormKit id="provider-form" type="form" @submit="onSubmit">
    <FormKit
      type="select"
      name="providerType"
      label="供应商类型"
      validation="required"
      :options="providerTypeOptions"
      :disabled="isEditing"
      v-model="providerType"
      :value="formState?.providerType"
      :help="providerTypeHelp"
    />

    <FormKit
      type="text"
      name="displayName"
      label="显示名称"
      validation="required"
      v-model="displayName"
      placeholder="例如: OpenAI Official"
      :value="formState?.displayName"
    />

    <FormKit
      v-if="requiresBaseUrl"
      type="text"
      name="baseUrl"
      label="Base URL"
      validation="required"
      :placeholder="baseUrlPlaceholder"
      :value="formState?.baseUrl"
    />

    <FormKit
      type="secret"
      name="apiKeySecretName"
      label="API Key"
      :value="formState?.apiKeySecretName"
      :requiredKeys="[
        {
          key: 'token',
        },
      ]"
      help="新建一个密钥，并将平台的 API key 填入该密钥的 Value 字段"
    />

    <FormKit
      type="text"
      name="proxyHost"
      label="代理主机"
      placeholder="可选"
      :value="formState?.proxyHost"
    />

    <FormKit
      type="number"
      number
      name="proxyPort"
      label="代理端口"
      placeholder="可选"
      :value="formState?.proxyPort"
    />

    <FormKit type="switch" name="enabled" label="启用" :value="formState?.enabled ?? true" />
  </FormKit>
</template>
