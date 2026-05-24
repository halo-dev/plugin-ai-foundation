<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel, AiProvider } from '@/api/generated'
import { AiModelSpecDiscoveryConfidenceEnum, AiModelSpecDiscoverySourceEnum } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import type { ModelFormState } from '@/types/form'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useTemplateRef } from 'vue'
import ModelForm from './ModelForm.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = useTemplateRef<InstanceType<typeof VModal>>('modal')
const form = useTemplateRef<InstanceType<typeof ModelForm>>('form')

const providerName = props.provider.metadata.name
const providerType = props.provider.spec.providerType

const { mutate, isPending } = useMutation({
  mutationFn: async (formState: ModelFormState) => {
    return await aiConsoleApiClient.model.createModel({
      aiModel: {
        apiVersion: 'aifoundation.halo.run/v1alpha1',
        kind: 'AiModel',
        metadata: { name: '' },
        spec: {
          providerName: providerName,
          modelId: formState.modelId,
          displayName: formState.displayName,
          enabled: formState.enabled,
          modelType: formState.modelType,
          features: formState.features?.length ? formState.features : undefined,
          discoverySource: AiModelSpecDiscoverySourceEnum.Manual,
          discoveryConfidence: AiModelSpecDiscoveryConfidenceEnum.High,
          ...(formState.adapterType ? { adapterType: formState.adapterType } : {}),
        } as AiModel['spec'],
      },
    })
  },
  onSuccess: () => {
    Toast.success('模型创建成功')
    modal.value?.close()
    queryClient.invalidateQueries({ queryKey: [QK_MODELS, providerName] })
  },
})

function onSubmit(data: ModelFormState) {
  mutate(data)
}
</script>

<template>
  <VModal
    mount-to-body
    title="创建模型"
    :centered="false"
    :width="600"
    ref="modal"
    @close="emit('close')"
  >
    <ModelForm ref="form" :provider-type="providerType" @submit="onSubmit" />
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isPending" @click="form?.submit()"> 保存 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
