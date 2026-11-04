<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { AiModelSpecDiscoveryConfidenceEnum, AiModelSpecDiscoverySourceEnum } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import type { ModelFormState } from '@/types/form'
import { modelSupportedFeaturesForProviderType } from '@/utils/model'
import { reconcileModelCapabilities } from '@/utils/model-capability-changes'
import {
  featureSupport,
  parameterFeatures,
  withFeatureSupport,
} from '@/utils/model-feature-support'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, useTemplateRef } from 'vue'
import ModelForm from './ModelForm.vue'

const props = defineProps<{
  model: AiModel
}>()

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = useTemplateRef<InstanceType<typeof VModal>>('modal')
const form = useTemplateRef<InstanceType<typeof ModelForm>>('form')

const providerName = computed(() => props.model.spec.providerName)

const { data: providers } = useProvidersFetch()
const provider = computed(() =>
  providers.value?.find((p) => p.metadata.name === providerName.value),
)
const providerType = computed(() => provider.value?.spec.providerType)
const { data: providerTypes } = useProviderTypesFetch()
const providerTypeInfo = computed(() =>
  providerTypes.value?.find((type) => type.providerType === providerType.value),
)
const providerMappings = computed(() => provider.value?.spec.parameterMappings)
const formState = computed<ModelFormState>(() => {
  const spec = props.model.spec
  return {
    modelId: spec.modelId,
    displayName: spec.displayName,
    enabled: spec.enabled,
    modelType: spec.modelType,
    features: spec.features,
    adapterType: spec.adapterType,
    capabilities: spec.capabilities,
    capabilitySources: spec.capabilitySources,
    catalogMatch: spec.catalogMatch,
    catalogParameters: spec.catalogParameters,
    parameterConstraints: parameterConstraintsForEdit(spec),
    parameterMappings: spec.parameterMappings,
    nativeOptions: spec.nativeOptions,
  }
})

function parameterConstraintsForEdit(spec: AiModel['spec']) {
  let constraints = spec.parameterConstraints
  if (
    spec.modelType !== 'language' ||
    spec.catalogParameters ||
    spec.catalogMatch ||
    !spec.features
  ) {
    return constraints
  }
  // Before catalog profiles, saved feature selections were the only record of these choices.
  for (const feature of parameterFeatures) {
    if (featureSupport(feature, constraints) === undefined) {
      constraints = withFeatureSupport(constraints, feature, spec.features.includes(feature))
    }
  }
  return constraints
}

const { mutate, isPending } = useMutation({
  mutationFn: async (formState: ModelFormState) => {
    return await aiConsoleApiClient.model.updateModel({
      name: props.model.metadata.name,
      aiModel: {
        ...props.model,
        spec: reconcileModelCapabilities(
          props.model.spec,
          {
            ...props.model.spec,
            modelId: formState.modelId,
            displayName: formState.displayName,
            enabled: formState.enabled,
            modelType: formState.modelType,
            features: formState.features?.length ? formState.features : undefined,
            capabilities: formState.capabilities,
            capabilitySources: formState.capabilitySources,
            parameterMappings: formState.parameterMappings,
            parameterConstraints: formState.parameterConstraints,
            catalogParameters: formState.catalogParameters,
            nativeOptions: formState.nativeOptions,
            adapterType: formState.adapterType || props.model.spec.adapterType,
            discoverySource:
              props.model.spec.discoverySource || AiModelSpecDiscoverySourceEnum.Manual,
            discoveryConfidence:
              props.model.spec.discoveryConfidence || AiModelSpecDiscoveryConfidenceEnum.High,
          },
          {
            provider: provider.value?.spec,
            supportedFeatures: modelSupportedFeaturesForProviderType(
              providerTypeInfo.value,
              formState.adapterType || props.model.spec.adapterType,
            ),
          },
        ),
      },
    })
  },
  onSuccess: () => {
    Toast.success('模型编辑成功')
    modal.value?.close()
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
  },
})

function onSubmit(data: ModelFormState) {
  mutate(data)
}
</script>

<template>
  <VModal
    mount-to-body
    title="编辑模型"
    :centered="false"
    :width="600"
    ref="modal"
    @close="emit('close')"
  >
    <ModelForm
      ref="form"
      :provider-name="providerName"
      :provider-type="providerType || ''"
      :inherited-mappings="providerMappings"
      :model-name="model.metadata.name"
      :form-state="formState"
      @submit="onSubmit"
    />
    <template #footer>
      <VSpace>
        <VButton
          type="secondary"
          :loading="isPending || form?.isSubmitting"
          @click="form?.submit()"
        >
          保存
        </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>