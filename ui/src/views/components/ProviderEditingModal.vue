<script lang="ts" setup>
import { aiCoreApiClient } from '@/api'
import type { AiProvider } from '@/api/generated'
import { QK_PROVIDERS } from '@/composables/use-providers-fetch'
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

const { mutate, isPending } = useMutation({
  mutationFn: async (formState: ProviderFormState) => {
    return await aiCoreApiClient.provider.updateAiProvider({
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
  onSuccess: () => {
    Toast.success('供应商编辑成功')
    modal.value?.close()
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
        <!-- @vue-ignore -->
        <VButton type="secondary" :loading="isPending" @click="$formkit.submit('provider-form')">
          保存
        </VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
