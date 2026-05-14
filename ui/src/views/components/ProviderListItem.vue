<script lang="ts" setup>
import type { AiProvider } from '@/api/generated'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { VAvatar, VStatusDot } from '@halo-dev/components'
import { toRefs } from 'vue'

const props = defineProps<{
  provider: AiProvider
  isSelected: boolean
}>()

const { provider } = toRefs(props)

const providerType = useProviderType(provider)
</script>
<template>
  <div
    class=":uno: flex cursor-pointer items-center gap-2 border border-white rounded-lg p-2"
    :class="{
      ':uno: bg-blue-50 outline outline-blue-200': isSelected,
      ':uno: bg-gray-50': !isSelected,
    }"
  >
    <VAvatar class=":uno: flex-none" :src="providerType?.iconUrl" circle size="xs" />
    <div class=":uno: min-w-0 flex-1">
      <div class=":uno: truncate text-sm font-semibold">
        {{ provider.spec.displayName }}
      </div>
    </div>
    <div class=":uno: flex-none" v-if="provider.metadata.deletionTimestamp">
      <VStatusDot state="warning" animate />
    </div>
  </div>
</template>
