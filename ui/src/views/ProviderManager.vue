<script setup lang="ts">
import type { Tab } from '@/components/SegmentedTabs.vue'
import SegmentedTabs from '@/components/SegmentedTabs.vue'
import { VPageHeader } from '@halo-dev/components'
import { useRouteQuery } from '@vueuse/router'
import RiBrainLine from '~icons/ri/brain-line'
import ProviderDetail from './ProviderDetail.vue'
import AllModelList from './components/AllModelList.vue'
import ModelTestWorkbench from './components/ModelTestWorkbench.vue'
import ProviderList from './components/ProviderList.vue'

const tabs: Tab[] = [
  {
    label: '配置',
    value: 'config',
  },
  {
    label: '模型列表',
    value: 'models',
  },
  {
    label: '测试',
    value: 'test',
  },
]

const activeTab = useRouteQuery<Tab['value'] | undefined>('tab', 'config')
</script>

<template>
  <VPageHeader title="AI 模型配置">
    <template #icon>
      <RiBrainLine />
    </template>
  </VPageHeader>

  <div class=":uno: flex p-2 pb-0">
    <SegmentedTabs v-model="activeTab" :tabs="tabs" />
  </div>

  <div v-if="activeTab === 'config'" class=":uno: h-[calc(100vh-6.5rem)] flex flex-col sm:flex-row">
    <div class=":uno: h-64 min-h-0 flex-none p-2 sm:h-auto sm:w-72">
      <ProviderList />
    </div>

    <div class=":uno: min-h-0 min-w-0 flex-1 shrink overflow-auto p-2">
      <ProviderDetail />
    </div>
  </div>

  <AllModelList v-if="activeTab === 'models'" />

  <ModelTestWorkbench v-if="activeTab === 'test'" />
</template>
