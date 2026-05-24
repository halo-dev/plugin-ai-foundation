<script lang="ts" setup>
import { AiProviderStatusPhaseEnum, type AiProvider } from '@/api/generated'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import { VAvatar, VStatusDot, type StatusDotState } from '@halo-dev/components'
import { computed, toRefs } from 'vue'

const props = defineProps<{
  provider: AiProvider
  isSelected: boolean
}>()

const { provider } = toRefs(props)

const providerType = useProviderType(provider)

const statusState = computed<StatusDotState>(() => {
  if (props.provider.metadata.deletionTimestamp) {
    return 'warning'
  }
  switch (props.provider.status?.phase) {
    case AiProviderStatusPhaseEnum.Ok:
      return 'success'
    case AiProviderStatusPhaseEnum.Error:
      return 'error'
    default:
      return 'default'
  }
})
</script>
<template>
  <button
    type="button"
    class=":uno: rounded-base w-full flex cursor-pointer items-center gap-3 border p-3 text-left transition-colors"
    :class="{
      ':uno: border-blue-200 bg-blue-50 shadow-sm ring-1 ring-blue-100': isSelected,
      ':uno: border-transparent bg-white hover:border-gray-200 hover:bg-gray-50': !isSelected,
    }"
    :aria-pressed="isSelected"
  >
    <VAvatar class=":uno: flex-none" :src="providerType?.iconUrl" circle size="xs" />
    <div class=":uno: min-w-0 flex-1">
      <div class=":uno: min-w-0 flex items-center gap-1.5">
        <span class=":uno: min-w-0 truncate text-sm text-gray-950 font-semibold">
          {{ provider.spec.displayName }}
        </span>
        <span
          v-if="!provider.spec.enabled"
          class=":uno: flex-none rounded-md bg-gray-100 px-1.5 py-0.5 text-xs text-gray-500 font-medium"
        >
          已禁用
        </span>
      </div>
      <div class=":uno: mt-1 min-w-0 flex items-center gap-1.5 text-xs text-gray-500">
        <span class=":uno: truncate">{{
          providerType?.displayName || provider.spec.providerType
        }}</span>
        <span class=":uno: text-gray-300">/</span>
        <span class=":uno: truncate">{{ provider.metadata.name }}</span>
      </div>
    </div>
    <div class=":uno: flex-none">
      <VStatusDot :state="statusState" :animate="!!provider.metadata.deletionTimestamp" />
    </div>
  </button>
</template>
