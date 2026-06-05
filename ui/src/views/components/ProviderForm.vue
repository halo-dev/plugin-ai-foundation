<script setup lang="ts">
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ProviderFormState } from '@/types/form'
import { submitForm } from '@formkit/core'
import { computed, shallowRef, watch } from 'vue'

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

const providerType = shallowRef(props.formState?.providerType)
const displayName = shallowRef(props.formState?.displayName)
const baseUrl = shallowRef(props.formState?.baseUrl)

const selectedProviderType = computed(() => {
  return providerTypes.value?.find((t) => t.providerType === providerType.value)
})

watch(
  () => providerType.value,
  (value) => {
    if (value && !isEditing.value) {
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

const resolvedBaseUrl = computed(() => {
  return (baseUrl.value || selectedProviderType.value?.defaultBaseUrl || '').trim()
})

const completionsUrlPreview = computed(() => {
  const completionsPath = selectedProviderType.value?.completionsPath
  if (!resolvedBaseUrl.value || !completionsPath) {
    return ''
  }
  return joinUrl(resolvedBaseUrl.value, completionsPath)
})

const baseUrlHelp = computed(() => {
  return `留空使用默认地址，自定义时可以填平台文档里的基础地址，当前接口预览：${completionsUrlPreview.value}`
})

function onSubmit(data: ProviderFormState) {
  emit('submit', data)
}

function joinUrl(base: string, path: string) {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

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
    >
      <template
        v-if="selectedProviderType?.description || selectedProviderType?.documentationUrl"
        #help
      >
        <div class=":uno: mt-2 flex flex-col text-xs text-gray-500 space-y-1">
          <div>{{ selectedProviderType?.description }}</div>
          <div v-if="selectedProviderType?.documentationUrl">
            文档地址：<a
              :href="selectedProviderType?.documentationUrl"
              target="_blank"
              rel="noopener noreferrer"
              class=":uno: text-gray-900 hover:text-gray-600 hover:underline"
              >{{ selectedProviderType?.documentationUrl }}</a
            >
          </div>
        </div>
      </template>
    </FormKit>

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
      v-if="selectedProviderType"
      type="text"
      name="baseUrl"
      label="Base URL"
      v-model="baseUrl"
      :validation="requiresBaseUrl ? 'required' : undefined"
      :placeholder="baseUrlPlaceholder"
      :value="formState?.baseUrl"
      :help="baseUrlHelp"
    />

    <FormKit
      type="secret"
      name="apiKeySecretName"
      label="API Key"
      :value="formState?.apiKeySecretName"
      :requiredKeys="[
        {
          key: 'api-key',
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
