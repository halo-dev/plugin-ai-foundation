<script setup lang="ts">
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ProviderFormState } from '@/types/form'
import type { FormKitTypeDefinition } from '@formkit/core'
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
  return providerTypes.value.flatMap((t) => {
    if (!t.providerType || !t.displayName) {
      return []
    }
    return [
      {
        value: t.providerType,
        label: t.displayName,
        icon: t.iconUrl,
        description: t.description || ' ',
      },
    ]
  })
})

const providerType = shallowRef(props.formState?.providerType)
const displayName = shallowRef(props.formState?.displayName)
const baseUrl = shallowRef(props.formState?.baseUrl)
const chatEndpointPath = shallowRef(props.formState?.chatEndpointPath)
const embeddingEndpointPath = shallowRef(props.formState?.embeddingEndpointPath)
const rerankEndpointPath = shallowRef(props.formState?.rerankEndpointPath)
const imageEndpointPath = shallowRef(props.formState?.imageEndpointPath)

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

const isOpenAiLike = computed(() => providerType.value === 'openailike')

const baseUrlPlaceholder = computed(() => {
  return selectedProviderType.value?.defaultBaseUrl || 'https://api.example.com/v1'
})

const isEditing = computed(() => !!props.formState)

const resolvedBaseUrl = computed(() => {
  return (baseUrl.value || selectedProviderType.value?.defaultBaseUrl || '').trim()
})

const previewBaseUrl = computed(() => {
  return (resolvedBaseUrl.value || baseUrlPlaceholder.value || '').trim()
})

const completionsUrlPreview = computed(() => {
  const completionsPath =
    selectedProviderType.value?.chatEndpointPath || selectedProviderType.value?.completionsPath
  if (!previewBaseUrl.value || !completionsPath) {
    return ''
  }
  return joinUrl(previewBaseUrl.value, completionsPath)
})

const baseUrlHelp = computed(() => {
  return `留空使用默认地址，自定义时可以填平台文档里的基础地址，当前接口预览：${completionsUrlPreview.value}`
})

const chatEndpointPlaceholder = computed(() => {
  return selectedProviderType.value?.chatEndpointPath || '/chat/completions'
})

const embeddingEndpointPlaceholder = computed(() => {
  return selectedProviderType.value?.embeddingEndpointPath || '/embeddings'
})

const rerankEndpointPlaceholder = computed(() => {
  return selectedProviderType.value?.rerankEndpointPath || '/rerank'
})

const imageEndpointPlaceholder = computed(() => {
  return selectedProviderType.value?.imageEndpointPath || '/images/generations'
})

const chatEndpointHelp = computed(() => endpointHelp(chatEndpointPath.value, chatEndpointPlaceholder.value))

const embeddingEndpointHelp = computed(() =>
  endpointHelp(embeddingEndpointPath.value, embeddingEndpointPlaceholder.value),
)

const rerankEndpointHelp = computed(() =>
  endpointHelp(rerankEndpointPath.value, rerankEndpointPlaceholder.value),
)

const imageEndpointHelp = computed(() =>
  endpointHelp(imageEndpointPath.value, imageEndpointPlaceholder.value),
)

function onSubmit(data: ProviderFormState) {
  emit('submit', data)
}

function joinUrl(base: string, path: string) {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

function endpointHelp(value: string | undefined, fallbackPath: string) {
  const path = (value || fallbackPath || '').trim()
  const preview = previewBaseUrl.value && path ? joinUrl(previewBaseUrl.value, path) : ''
  return `留空使用默认路径，当前接口预览：${preview}`
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
      :type="'secret' as unknown as FormKitTypeDefinition<string>"
      name="apiKeySecretName"
      label="API Key"
      :value="formState?.apiKeySecretName"
      :requiredKeys="[
        {
          key: 'api-key',
        },
      ]"
      :descriptionPreset="`${selectedProviderType?.displayName} API Key`"
      help="新建一个密钥，并将平台的 API key 填入该密钥的 Value 字段"
    />

    <template v-if="isOpenAiLike">
      <FormKit
        type="text"
        name="chatEndpointPath"
        label="聊天 Endpoint"
        v-model="chatEndpointPath"
        :placeholder="chatEndpointPlaceholder"
        :value="formState?.chatEndpointPath"
        :help="chatEndpointHelp"
      />

      <FormKit
        type="text"
        name="embeddingEndpointPath"
        label="嵌入 Endpoint"
        v-model="embeddingEndpointPath"
        :placeholder="embeddingEndpointPlaceholder"
        :value="formState?.embeddingEndpointPath"
        :help="embeddingEndpointHelp"
      />

      <FormKit
        type="text"
        name="rerankEndpointPath"
        label="重排 Endpoint"
        v-model="rerankEndpointPath"
        :placeholder="rerankEndpointPlaceholder"
        :value="formState?.rerankEndpointPath"
        :help="rerankEndpointHelp"
      />

      <FormKit
        type="text"
        name="imageEndpointPath"
        label="图像 Endpoint"
        v-model="imageEndpointPath"
        :placeholder="imageEndpointPlaceholder"
        :value="formState?.imageEndpointPath"
        :help="imageEndpointHelp"
      />
    </template>

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

    <FormKit
      :type="'switch' as unknown as FormKitTypeDefinition<boolean>"
      name="enabled"
      label="启用"
      :value="formState?.enabled ?? true"
    />
  </FormKit>
</template>
