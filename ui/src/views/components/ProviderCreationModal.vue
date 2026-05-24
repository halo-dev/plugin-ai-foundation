<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import { useProviderQueryState } from '@/composables/use-provider-state'
import { QK_PROVIDERS } from '@/composables/use-providers-fetch'
import type { ProviderFormState } from '@/types/form'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useTemplateRef } from 'vue'
import ProviderForm from './ProviderForm.vue'

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = useTemplateRef<InstanceType<typeof VModal>>('modal')
const form = useTemplateRef<InstanceType<typeof ProviderForm>>('form')

const { selectedProvider } = useProviderQueryState()

const { mutate, isPending } = useMutation({
  mutationFn: async (formState: ProviderFormState) => {
    return await aiConsoleApiClient.provider.createProvider({
      aiProvider: {
        kind: 'AiProvider',
        apiVersion: 'aifoundation.halo.run/v1alpha1',
        metadata: {
          generateName: `${formState.providerType}-`.toLocaleLowerCase(),
          name: '',
        },
        spec: {
          providerType: formState.providerType,
          displayName: formState.displayName,
          enabled: formState.enabled,
          apiKeySecretName: formState.apiKeySecretName,
          baseUrl: formState.baseUrl,
          proxyHost: formState.proxyHost,
          proxyPort: formState.proxyPort,
        },
      },
    })
  },
  onSuccess: (data) => {
    Toast.success('供应商创建成功')
    modal.value?.close()
    queryClient.invalidateQueries({ queryKey: [QK_PROVIDERS] })
    selectedProvider.value = data.data.metadata.name
  },
})

function onSubmit(data: ProviderFormState) {
  mutate(data)
}
</script>

<template>
  <VModal
    mount-to-body
    title="接入供应商"
    :centered="false"
    :width="600"
    ref="modal"
    @close="emit('close')"
  >
    <ProviderForm ref="form" @submit="onSubmit" />
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isPending" @click="form?.submit()"> 保存 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
