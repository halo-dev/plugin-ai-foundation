<script setup lang="ts">
import { useProviderQueryState } from '@/composables/use-provider-state'
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { VEmpty, VLoading } from '@halo-dev/components'
import { computed, shallowRef, watch } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import ProviderCreationModal from './ProviderCreationModal.vue'
import ProviderListItem from './ProviderListItem.vue'

const { data: providers, isLoading } = useProvidersFetch()

const { selectedProvider } = useProviderQueryState()

const creationModalVisible = shallowRef(false)
const providerCount = computed(() => providers.value?.length || 0)

watch(
  () => providers.value,
  (value) => {
    if (
      value?.length &&
      (!selectedProvider.value || !value.find((p) => p.metadata.name === selectedProvider.value))
    ) {
      selectedProvider.value = value[0].metadata.name
    }
  },
  { immediate: true },
)
</script>

<template>
  <div
    class=":uno: rounded-base h-full flex flex-col overflow-hidden border border-gray-200 bg-white shadow-sm"
  >
    <div class=":uno: flex-none border-b border-gray-100 px-4 py-3">
      <div class=":uno: flex items-center justify-between gap-3">
        <div>
          <div class=":uno: text-sm text-gray-950 font-semibold">供应商</div>
          <div class=":uno: mt-0.5 text-xs text-gray-500">已接入 {{ providerCount }} 个</div>
        </div>
        <button
          type="button"
          class=":uno: h-8 w-8 inline-flex items-center justify-center rounded-md text-gray-500 hover:bg-gray-100 hover:text-gray-900"
          v-tooltip="`接入供应商`"
          @click="creationModalVisible = true"
        >
          <RiAddLine class=":uno: h-4 w-4" />
        </button>
      </div>
    </div>

    <div class=":uno: min-h-0 flex-1 shrink bg-gray-50/60">
      <VLoading v-if="isLoading" />

      <div
        v-else-if="providerCount === 0"
        class=":uno: h-full flex items-center justify-center p-4"
      >
        <VEmpty title="暂无供应商" message="接入供应商后即可添加模型" />
      </div>

      <div v-else class=":uno: h-full flex flex-col gap-2 overflow-auto p-2">
        <ProviderListItem
          :is-selected="selectedProvider === provider.metadata.name"
          v-for="provider in providers"
          :key="provider.metadata.name"
          :provider="provider"
          @click="selectedProvider = provider.metadata.name"
        />
      </div>
    </div>
  </div>

  <ProviderCreationModal v-if="creationModalVisible" @close="creationModalVisible = false" />
</template>
