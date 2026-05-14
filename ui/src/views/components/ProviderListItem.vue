<script lang="ts" setup>
import type { AiProvider } from '@/api/generated'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import { VAvatar } from '@halo-dev/components'
import { computed } from 'vue'

const props = defineProps<{
  provider: AiProvider
  isSelected: boolean
}>()

const { data: providerTypes } = useProviderTypesFetch()

const providerType = computed(() => {
  return providerTypes.value?.find((t) => t.providerType === props.provider.spec.providerType)
})
</script>
<template>
  <div
    class=":uno: flex cursor-pointer items-center gap-2 border border-white rounded-lg p-2"
    :class="{
      ':uno: bg-blue-50 outline outline-blue-200': isSelected,
      ':uno: bg-gray-50': !isSelected,
    }"
  >
    <VAvatar :src="providerType?.iconUrl" circle size="xs" />
    <div>
      <div class=":uno: text-sm font-semibold">
        {{ provider.spec.displayName }}
      </div>
    </div>
  </div>
</template>
