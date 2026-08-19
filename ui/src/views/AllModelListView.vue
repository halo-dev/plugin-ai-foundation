<script lang="ts" setup>
import { useModelsFetch } from '@/composables/use-models-fetch'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import { MODEL_FEATURE_OPTIONS, MODEL_TYPE_OPTIONS } from '@/types'
import {
  IconRefreshLine,
  VButton,
  VCard,
  VEmpty,
  VEntityContainer,
  VLoading,
  VSpace,
} from '@halo-dev/components'
import { useFuse } from '@vueuse/integrations/useFuse'
import { useRouteQuery } from '@vueuse/router'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AllModelListItem from './components/AllModelListItem.vue'

const { data, isLoading, isFetching, refetch } = useModelsFetch({})
const router = useRouter()

const keyword = useRouteQuery<string>('keyword', '')
const modelTypeFilter = useRouteQuery<string | undefined>('modelType')
const featureFilter = useRouteQuery<string | undefined>('feature')

const allModels = computed(() => data.value || [])

const { results } = useFuse(keyword, allModels, {
  fuseOptions: {
    keys: ['spec.displayName', 'spec.modelId', 'spec.providerName'],
    threshold: 0.3,
  },
})

const keywordModels = computed(() => {
  if (!keyword.value) {
    return allModels.value
  }
  return results.value.map((r) => r.item)
})

const filteredModels = computed(() => {
  return keywordModels.value.filter((model) => {
    if (modelTypeFilter.value && model.spec.modelType !== modelTypeFilter.value) {
      return false
    }
    if (
      featureFilter.value &&
      !(model.spec.features || []).some((item) => item === featureFilter.value)
    ) {
      return false
    }
    return true
  })
})

const hasFilters = computed(() => !!(modelTypeFilter.value || featureFilter.value))

const resultText = computed(() => {
  return keyword.value || hasFilters.value
    ? `找到 ${filteredModels.value.length} 个模型`
    : `共 ${allModels.value.length} 个模型`
})

function handleClearFilters() {
  modelTypeFilter.value = undefined
  featureFilter.value = undefined
}

function openProviderConfig() {
  void router.push({
    name: AI_FOUNDATION_ROUTE_NAMES.PROVIDERS,
  })
}
</script>
<template>
  <div class=":uno: p-2">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: block w-full bg-white px-4 py-3">
          <div class=":uno: mb-3 min-w-0 flex flex-col gap-1">
            <div class=":uno: text-sm text-gray-950 font-semibold">模型列表</div>
            <div class=":uno: text-xs text-gray-500">{{ resultText }}</div>
          </div>
          <div class=":uno: flex flex-col flex-wrap items-start gap-3 lg:flex-row lg:items-center">
            <div class=":uno: w-full flex flex-1 items-center gap-2 lg:w-auto">
              <SearchInput sync v-model="keyword" />
            </div>
            <VSpace>
              <FilterCleanButton v-if="hasFilters" @click="handleClearFilters" />
              <FilterDropdown
                v-model="modelTypeFilter"
                label="类型"
                :items="[
                  {
                    label: '全部',
                  },
                  ...MODEL_TYPE_OPTIONS,
                ]"
              />
              <FilterDropdown
                v-model="featureFilter"
                label="特性"
                :items="[
                  {
                    label: '全部',
                  },
                  ...MODEL_FEATURE_OPTIONS,
                ]"
              />
              <button
                type="button"
                class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
                @click="refetch()"
                v-tooltip="`刷新`"
              >
                <IconRefreshLine
                  :class="{ ':uno: animate-spin text-gray-900': isFetching }"
                  class=":uno: h-4 w-4 text-gray-600 group-hover:text-gray-900"
                />
              </button>
            </VSpace>
          </div>
        </div>
      </template>

      <VLoading v-if="isLoading" />

      <Transition v-else-if="!filteredModels.length" appear name="fade">
        <VEmpty message="暂无匹配结果" title="你可以尝试刷新，或者在配置选项卡中配置供应商">
          <template #actions>
            <VSpace>
              <VButton :loading="isFetching" @click="refetch()"> 刷新 </VButton>
              <VButton type="secondary" @click="openProviderConfig"> 配置模型 </VButton>
            </VSpace>
          </template>
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <AllModelListItem
            v-for="model in filteredModels"
            :key="model.metadata.name"
            :model="model"
          />
        </VEntityContainer>
      </Transition>

      <template #footer>
        <div class=":uno: min-h-9 flex items-center px-1">
          <span class=":uno: text-sm text-gray-500">{{ resultText }}</span>
        </div>
      </template>
    </VCard>
  </div>
</template>
