<script setup lang="ts">
import type { AiProvider } from '@/api/generated'
import { useProviderTypes } from '@/composables/useProviderTypes'
import { VButton, VCard, VEmpty, VLoading, VStatusDot, VTag } from '@halo-dev/components'
import { computed, ref } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiSearchLine from '~icons/ri/search-line'

const props = defineProps<{
  providers: AiProvider[]
  loading: boolean
  selectedName?: string
}>()

const emit = defineEmits<{
  (e: 'select', provider: AiProvider): void
  (e: 'edit', provider: AiProvider): void
  (e: 'delete', provider: AiProvider): void
}>()

const { data: providerTypes } = useProviderTypes()
const searchQuery = ref('')

function providerTypeLabel(providerType: string): string {
  const info = providerTypes.value?.find((t) => t.providerType === providerType)
  return info?.displayName || providerType
}

const filteredProviders = computed(() => {
  if (!searchQuery.value) return props.providers
  const q = searchQuery.value.toLowerCase()
  return props.providers.filter(
    (p) =>
      p.spec.displayName.toLowerCase().includes(q) ||
      p.spec.providerType.toLowerCase().includes(q) ||
      providerTypeLabel(p.spec.providerType).toLowerCase().includes(q),
  )
})

function statusPhase(phase?: string) {
  switch (phase) {
    case 'OK':
      return 'success'
    case 'ERROR':
      return 'error'
    default:
      return 'default'
  }
}
</script>

<template>
  <div>
    <div class=":uno: border-b border-gray-200 p-3">
      <div class=":uno: relative">
        <RiSearchLine class=":uno: absolute left-2.5 top-1/2 h-4 w-4 text-gray-400 -translate-y-1/2" />
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索供应商..."
          class=":uno: w-full border border-gray-200 rounded-md px-3 py-2 pl-8 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
        />
      </div>
    </div>

    <VLoading v-if="loading" />

    <div v-else-if="filteredProviders.length === 0" class=":uno: px-4 py-8">
      <VEmpty title="暂无供应商" />
    </div>

    <div v-else class=":uno: flex flex-col gap-2 p-2">
      <VCard
        v-for="provider in filteredProviders"
        :key="provider.metadata.name"
        :class="[
          ':uno: group cursor-pointer transition-all duration-200 hover:border-blue-500',
          { ':uno: border-blue-500 bg-blue-50': selectedName === provider.metadata.name },
        ]"
        @click="emit('select', provider)"
      >
        <div class=":uno: mb-2 flex items-start justify-between">
          <div class=":uno: flex flex-wrap items-center gap-2">
            <span class=":uno: font-medium">{{ provider.spec.displayName }}</span>
            <VTag size="sm">
              {{ providerTypeLabel(provider.spec.providerType) }}
            </VTag>
          </div>
          <div class=":uno: flex gap-1 opacity-0 transition-opacity duration-200 group-hover:opacity-100" @click.stop>
            <VButton type="default" size="sm" @click="emit('edit', provider)">
              <RiEditLine />
            </VButton>
            <VButton type="danger" size="sm" @click="emit('delete', provider)">
              <RiDeleteBinLine />
            </VButton>
          </div>
        </div>
        <div class=":uno: flex items-center gap-1.5">
          <VStatusDot :state="statusPhase(provider.status?.phase)" />
          <span class=":uno: text-xs text-gray-500">{{ provider.status?.phase || 'UNKNOWN' }}</span>
          <VTag v-if="!provider.spec.enabled" size="sm" class=":uno: ml-2"> 已禁用 </VTag>
        </div>
      </VCard>
    </div>
  </div>
</template>
