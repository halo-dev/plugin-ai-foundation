<script setup lang="ts">
import type { AiModel, AiModelSpec } from '@/api/generated'
import { useCreateModel, useUpdateModel } from '@/composables/useModels'
import { CAPABILITY_OPTIONS, ENDPOINT_TYPE_OPTIONS } from '@/types'
import { VButton } from '@halo-dev/components'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  model?: AiModel | null
  providerName: string
}>()

const emit = defineEmits<{
  (e: 'saved'): void
  (e: 'cancel'): void
}>()

const createModel = useCreateModel()
const updateModel = useUpdateModel()

const submitBtn = ref<HTMLButtonElement>()

const formValues = ref<Record<string, unknown>>({
  enabled: true,
  supportedTextDelta: true,
})

watch(
  () => props.model,
  (m) => {
    if (m) {
      formValues.value = {
        modelId: m.spec.modelId,
        displayName: m.spec.displayName,
        group: m.spec.group || '',
        capabilities: m.spec.capabilities || [],
        endpointType: m.spec.endpointType || '',
        supportedTextDelta: m.spec.supportedTextDelta ?? true,
        enabled: m.spec.enabled,
      }
    } else {
      formValues.value = {
        enabled: true,
        supportedTextDelta: true,
      }
    }
  },
  { immediate: true },
)

const capabilityOptions = CAPABILITY_OPTIONS.map((c) => ({
  value: c.value,
  label: c.label,
}))

const endpointTypeOptions = ENDPOINT_TYPE_OPTIONS.map((o) => ({
  value: o.value,
  label: o.label,
}))

const isEditing = computed(() => !!props.model)

function submitForm() {
  submitBtn.value?.click()
}

async function handleSubmit(values: Record<string, unknown>) {
  const spec: AiModelSpec = {
    providerName: props.providerName,
    modelId: (values.modelId as string)?.trim() || '',
    displayName: (values.displayName as string)?.trim() || '',
    enabled: !!values.enabled,
    group: (values.group as string)?.trim() || undefined,
    capabilities: (values.capabilities as string[])?.length
      ? (values.capabilities as string[])
      : undefined,
    endpointType: (values.endpointType as string) || undefined,
    supportedTextDelta: !!values.supportedTextDelta,
  }

  if (props.model) {
    const updated: AiModel = {
      ...props.model,
      spec,
    }
    await updateModel.mutateAsync({
      name: props.model.metadata.name,
      model: updated,
    })
  } else {
    const newModel: AiModel = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiModel',
      metadata: { generateName: `${props.providerName}-${spec.modelId}-`, name: '' },
      spec,
    }
    await createModel.mutateAsync(newModel)
  }

  emit('saved')
}
</script>

<template>
  <div class="model-form">
    <FormKit
      id="model-form"
      type="form"
      v-model="formValues"
      :actions="false"
      @submit="handleSubmit"
    >
      <FormKit
        type="text"
        name="modelId"
        label="模型 ID"
        validation="required"
        placeholder="例如: gpt-4o"
        :disabled="isEditing"
      />

      <FormKit
        type="text"
        name="displayName"
        label="显示名称"
        validation="required"
        placeholder="例如: GPT-4o"
      />

      <FormKit type="text" name="group" label="分组" placeholder="例如: chat, embedding" />

      <FormKit type="checkbox" name="capabilities" label="能力标签" :options="capabilityOptions" />

      <FormKit
        type="select"
        name="endpointType"
        label="Endpoint 类型"
        :options="endpointTypeOptions"
      />

      <FormKit type="switch" name="supportedTextDelta" label="支持流式输出" />

      <FormKit type="switch" name="enabled" label="启用" />

      <button ref="submitBtn" type="submit" style="display: none"></button>
    </FormKit>

    <div class="form-actions">
      <VButton type="secondary" @click="emit('cancel')">取消</VButton>
      <VButton
        type="primary"
        :loading="createModel.isPending.value || updateModel.isPending.value"
        @click="submitForm"
      >
        保存
      </VButton>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.model-form {
  padding: 8px 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}
</style>
