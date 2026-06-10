<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiProvider } from '@/api/generated'
import { QK_PROVIDER, QK_PROVIDERS } from '@/composables/use-providers-fetch'
import type { ProviderFormState } from '@/types/form'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useTemplateRef } from 'vue'
import ProviderForm from './ProviderForm.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = useTemplateRef<InstanceType<typeof VModal>>('modal')
const form = useTemplateRef<InstanceType<typeof ProviderForm>>('form')

const { mutate, isPending } = useMutation({
  mutationFn: async (formState: ProviderFormState) => {
    return await aiConsoleApiClient.provider.updateProvider({
      name: props.provider.metadata.name,
      aiProvider: {
        ...props.provider,
        spec: {
          ...props.provider.spec,
          ...formState,
        },
      },
    })
  },
  onSuccess: async () => {
    Toast.success('供应商编辑成功')
    modal.value?.close()
    queryClient.invalidateQueries({ queryKey: [QK_PROVIDERS] })

    await aiConsoleApiClient.provider.testProviderConnectivity({
      name: props.provider.metadata.name,
    })
    queryClient.invalidateQueries({ queryKey: [QK_PROVIDER, props.provider.metadata.name] })
    queryClient.invalidateQueries({ queryKey: [QK_PROVIDERS] })
  },
})

function onSubmit(data: ProviderFormState) {
  mutate(data)
}
</script>

<template>
  <VModal
    mount-to-body
    title="编辑供应商"
    :centered="false"
    :width="600"
    ref="modal"
    @close="emit('close')"
  >
    <ProviderForm
      ref="form"
      :form-state="{
        providerType: provider.spec.providerType,
        displayName: provider.spec.displayName,
        enabled: provider.spec.enabled,
        baseUrl: provider.spec.baseUrl,
        apiKeySecretName: provider.spec.apiKeySecretName,
        proxyHost: provider.spec.proxyHost,
        proxyPort: provider.spec.proxyPort,
      }"
      @submit="onSubmit"
    />
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isPending" @click="form?.submit()"> 保存 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
