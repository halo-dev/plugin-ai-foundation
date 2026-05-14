<script setup lang="ts">
import { useProvidersFetch } from '@/composables/use-providers-fetch'
import { VButton, VLoading } from '@halo-dev/components'
import { useRouteQuery } from '@vueuse/router'
import { ref, watch } from 'vue'
import ProviderCreationModal from './ProviderCreationModal.vue'
import ProviderListItem from './ProviderListItem.vue'

const { data: providers, isLoading } = useProvidersFetch()

const selectedProvider = useRouteQuery<string | undefined>('provider')

const creationModalVisible = ref(false)

watch(
  () => providers.value,
  (value) => {
    if (value?.length && !selectedProvider.value) {
      selectedProvider.value = value[0].metadata.name
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class=":uno: rounded-base h-full flex flex-col bg-white shadow-sm ring-1 ring-[#eaecf0]">
    <div class=":uno: flex-none border-b border-gray-100 px-4 py-3">
      <span class=":uno: text-base font-bold"> 供应商 </span>
    </div>

    <div class=":uno: min-h-0 flex-1 shrink">
      <VLoading v-if="isLoading" />
      <div v-else-if="providers?.length === 0">暂无供应商</div>

      <div class=":uno: h-full flex flex-col gap-2 overflow-auto p-2">
        <ProviderListItem
          :is-selected="selectedProvider === provider.metadata.name"
          v-for="provider in providers"
          :key="provider.metadata.name"
          :provider="provider"
          @click="selectedProvider = provider.metadata.name"
        />
      </div>
    </div>

    <div class=":uno: flex-none border-t border-gray-100 p-2">
      <VButton @click="creationModalVisible = true" block> 添加供应商 </VButton>
    </div>
  </div>

  <ProviderCreationModal v-if="creationModalVisible" @close="creationModalVisible = false" />
</template>
