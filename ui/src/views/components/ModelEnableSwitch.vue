<script lang="ts" setup>
import { aiCoreApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import { Toast, VSwitch } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'

const props = defineProps<{
  model: AiModel
}>()

const queryClient = useQueryClient()

const enabled = computed(() => props.model.spec.enabled !== false)

const mutation = useMutation({
  mutationFn: async (nextEnabled: boolean) => {
    return await aiCoreApiClient.model.patchAiModel({
      name: props.model.metadata.name,
      jsonPatchInner: [
        {
          op: 'add',
          path: '/spec/enabled',
          value: nextEnabled,
        },
      ],
    })
  },
  onSuccess: (_data, nextEnabled) => {
    Toast.success(nextEnabled ? '模型已启用' : '模型已禁用')
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
  },
  onError: (error) => {
    Toast.error('模型状态更新失败: ' + (error as Error).message)
  },
})

const isUpdating = computed(() => mutation.isPending.value)
const disabled = computed(() => !!props.model.metadata.deletionTimestamp || isUpdating.value)

function handleToggle() {
  if (disabled.value) {
    return
  }
  mutation.mutate(!enabled.value)
}
</script>

<template>
  <VSwitch
    :model-value="enabled"
    :loading="isUpdating"
    :disabled="disabled"
    v-tooltip="enabled ? '禁用模型' : '启用模型'"
    @click.stop="handleToggle"
  />
</template>
