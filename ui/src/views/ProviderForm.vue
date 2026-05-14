<script setup lang="ts">
import type { AiProvider, AiProviderSpec } from '@/api/generated'
import { useCreateProvider, useUpdateProvider } from '@/composables/useProviders'
import { useProviderTypes } from '@/composables/useProviderTypes'
import { VButton } from '@halo-dev/components'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  provider?: AiProvider | null
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'cancel'): void
}>()

const createProvider = useCreateProvider()
const updateProvider = useUpdateProvider()
const { data: providerTypes } = useProviderTypes()

const submitBtn = ref<HTMLButtonElement>()

const formValues = ref<Record<string, unknown>>({
  enabled: true,
})

watch(
  () => props.provider,
  (p) => {
    if (p) {
      formValues.value = {
        providerType: p.spec.providerType,
        displayName: p.spec.displayName,
        baseUrl: p.spec.baseUrl || '',
        apiKeySecretName: p.spec.apiKeySecretName || '',
        enabled: p.spec.enabled,
        proxyHost: p.spec.proxyHost || '',
        proxyPort: p.spec.proxyPort,
      }
    } else {
      formValues.value = { enabled: true }
    }
  },
  { immediate: true },
)

const providerTypeOptions = computed(() => {
  if (!providerTypes.value) return []
  return providerTypes.value.map((t) => ({
    value: t.providerType,
    label: t.displayName,
  }))
})

const selectedProviderType = computed(() => {
  const type = formValues.value.providerType as string
  return providerTypes.value?.find((t) => t.providerType === type)
})

const requiresBaseUrl = computed(() => {
  return selectedProviderType.value?.requiresBaseUrl ?? false
})

const baseUrlPlaceholder = computed(() => {
  return selectedProviderType.value?.defaultBaseUrl || 'https://api.example.com/v1'
})

const isEditing = computed(() => !!props.provider)

watch(
  () => formValues.value.providerType,
  () => {
    if (!isEditing.value && selectedProviderType.value) {
      const current = (formValues.value.displayName as string)?.trim()
      if (!current) {
        formValues.value.displayName = selectedProviderType.value.displayName
      }
    }
  },
)

function submitForm() {
  submitBtn.value?.click()
}

async function handleSubmit(values: Record<string, unknown>) {
  const spec: AiProviderSpec = {
    providerType: values.providerType as string,
    displayName: (values.displayName as string)?.trim() || '',
    enabled: !!values.enabled,
    baseUrl: requiresBaseUrl.value ? (values.baseUrl as string)?.trim() || undefined : undefined,
    apiKeySecretName: (values.apiKeySecretName as string) || undefined,
    proxyHost: (values.proxyHost as string)?.trim() || undefined,
    proxyPort: values.proxyPort ? Number(values.proxyPort) : undefined,
  }

  if (props.provider) {
    const updated: AiProvider = {
      ...props.provider,
      spec,
    }
    await updateProvider.mutateAsync({
      name: props.provider.metadata.name,
      provider: updated,
    })
  } else {
    const newProvider: AiProvider = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiProvider',
      metadata: { generateName: `${values.providerType}-`.toLocaleLowerCase(), name: '' },
      spec,
    }
    await createProvider.mutateAsync(newProvider)
  }

  emit('saved')
}
</script>

<template>
  <div class=":uno: py-2">
    <FormKit
      id="provider-form"
      type="form"
      v-model="formValues"
      :actions="false"
      @submit="handleSubmit"
    >
      <FormKit
        type="select"
        name="providerType"
        label="供应商类型"
        validation="required"
        :options="providerTypeOptions"
        :disabled="isEditing"
      />

      <FormKit
        type="text"
        name="displayName"
        label="显示名称"
        validation="required"
        placeholder="例如: OpenAI Official"
      />

      <FormKit
        v-if="requiresBaseUrl"
        type="text"
        name="baseUrl"
        label="Base URL"
        validation="required"
        :placeholder="baseUrlPlaceholder"
      />

      <FormKit type="secret" name="apiKeySecretName" label="API Key Secret" />

      <FormKit type="text" name="proxyHost" label="代理主机" placeholder="可选" />

      <FormKit type="number" name="proxyPort" label="代理端口" placeholder="可选" />

      <FormKit type="switch" name="enabled" label="启用" />

      <button ref="submitBtn" type="submit" class=":uno: hidden"></button>
    </FormKit>

    <div class=":uno: mt-6 flex justify-end gap-3 border-t border-gray-200 pt-4">
      <VButton type="secondary" @click="emit('cancel')">取消</VButton>
      <VButton
        type="primary"
        :loading="createProvider.isPending.value || updateProvider.isPending.value"
        @click="submitForm"
      >
        保存
      </VButton>
    </div>
  </div>
</template>
