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
import { useFuse } from '@vueuse/integrations'
import { computed, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import AllModelListItem from './components/AllModelListItem.vue'

const { data, isLoading, isFetching, refetch } = useModelsFetch({})
const router = useRouter()

const keyword = shallowRef('')
const modelTypeFilter = shallowRef('')
const featureFilter = shallowRef('')

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

const hasFilters = computed(() => !!(keyword.value || modelTypeFilter.value || featureFilter.value))

const resultText = computed(() => {
  return hasFilters.value
    ? `找到 ${filteredModels.value.length} 个模型`
    : `共 ${allModels.value.length} 个模型`
})

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
            <div class=":uno: grid grid-cols-1 w-full gap-2 sm:grid-cols-2 lg:w-auto">
              <select
                v-model="modelTypeFilter"
                class=":uno: h-9 min-w-36 rounded-md bg-white text-sm text-gray-700 outline-none !border !border-gray-200 !border-solid !px-3 !py-0 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500"
              >
                <option value="">全部类型</option>
                <option v-for="item in MODEL_TYPE_OPTIONS" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select
                v-model="featureFilter"
                class=":uno: h-9 min-w-36 rounded-md bg-white text-sm text-gray-700 outline-none !border !border-gray-200 !border-solid !px-3 !py-0 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500"
              >
                <option value="">全部特性</option>
                <option v-for="item in MODEL_FEATURE_OPTIONS" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </div>
            <VSpace spacing="lg" class=":uno: flex-wrap lg:ml-auto">
              <div class=":uno: flex flex-row gap-2">
                <button
                  type="button"
                  class=":uno: group size-9 inline-flex cursor-pointer items-center justify-center border border-gray-200 rounded-md bg-white hover:bg-gray-50"
                  @click="refetch()"
                  v-tooltip="`刷新`"
                >
                  <IconRefreshLine
                    :class="{ ':uno: animate-spin text-gray-900': isFetching }"
                    class=":uno: size-4 text-gray-600 group-hover:text-gray-900"
                  />
                </button>
              </div>
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
