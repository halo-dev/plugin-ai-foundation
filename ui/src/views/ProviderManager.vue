<script setup lang="ts">
import type { Tab } from '@/components/SegmentedTabs.vue'
import SegmentedTabs from '@/components/SegmentedTabs.vue'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import { VPageHeader } from '@halo-dev/components'
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import MingcuteAiLine from '~icons/mingcute/ai-line'

const tabs: Tab[] = [
  {
    label: '模型配置',
    value: AI_FOUNDATION_ROUTE_NAMES.PROVIDERS,
  },
  {
    label: '模型列表',
    value: AI_FOUNDATION_ROUTE_NAMES.MODELS,
  },
  {
    label: '默认模型',
    value: AI_FOUNDATION_ROUTE_NAMES.DEFAULTS,
  },
  {
    label: '测试',
    value: AI_FOUNDATION_ROUTE_NAMES.TEST,
  },
]

const route = useRoute()
const router = useRouter()

const activeRouteName = computed({
  get: () => {
    const matchedTab = tabs.find((item) => {
      return route.matched.some((record) => record.name === item.value)
    })
    return matchedTab?.value || AI_FOUNDATION_ROUTE_NAMES.PROVIDERS
  },
  set: (value) => {
    if (value !== activeRouteName.value) {
      void router.push({ name: value })
    }
  },
})
</script>

<template>
  <VPageHeader title="AI Foundation">
    <template #icon>
      <MingcuteAiLine />
    </template>
  </VPageHeader>

  <div class=":uno: flex p-2 pb-0">
    <SegmentedTabs v-model="activeRouteName" :tabs="tabs" />
  </div>

  <RouterView />
</template>
