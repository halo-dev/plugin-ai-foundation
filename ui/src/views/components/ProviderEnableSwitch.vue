<script lang="ts" setup>
import { aiCoreApiClient } from '@/api'
import type { AiProvider } from '@/api/generated'
import { QK_MODEL_OPTIONS } from '@/composables/use-model-options-fetch'
import { reloadProviderQueries } from '@/composables/use-providers-fetch'
import { Toast, VSwitch } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'

const props = defineProps<{
  provider: AiProvider
}>()

const queryClient = useQueryClient()

const enabled = computed(() => props.provider.spec.enabled !== false)

const mutation = useMutation({
  mutationFn: async (nextEnabled: boolean) => {
    return await aiCoreApiClient.provider.patchAiProvider({
      name: props.provider.metadata.name,
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
    Toast.success(nextEnabled ? '供应商已启用' : '供应商已禁用')
    reloadProviderQueries(queryClient, props.provider.metadata.name)
    queryClient.invalidateQueries({ queryKey: [QK_MODEL_OPTIONS] })
  },
  onError: (error) => {
    Toast.error('供应商状态更新失败: ' + (error as Error).message)
  },
})

const isUpdating = computed(() => mutation.isPending.value)
const disabled = computed(() => !!props.provider.metadata.deletionTimestamp || isUpdating.value)

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
    v-tooltip="enabled ? '禁用供应商' : '启用供应商'"
    @click.stop="handleToggle"
  />
</template>
