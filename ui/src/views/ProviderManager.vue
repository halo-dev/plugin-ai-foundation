<script setup lang="ts">
import { VPageHeader } from '@halo-dev/components'
import RiBrainLine from '~icons/ri/brain-line'
import ProviderDetail from './ProviderDetail.vue'
import ProviderList from './components/ProviderList.vue'
</script>

<template>
  <VPageHeader title="AI 模型配置">
    <template #icon>
      <RiBrainLine />
    </template>
  </VPageHeader>

  <div class=":uno: h-[calc(100vh-3.5rem)] flex">
    <div class=":uno: w-72 flex-none p-2">
      <ProviderList />
    </div>

    <div class=":uno: min-w-0 flex-1 shrink overflow-auto p-2">
      <ProviderDetail />
    </div>
  </div>
</template>
