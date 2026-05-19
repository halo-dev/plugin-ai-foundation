<script lang="ts" setup>
import { useModelsFetch } from '@/composables/use-models-fetch'
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
import { useRouteQuery } from '@vueuse/router'
import { computed, ref } from 'vue'
import AllModelListItem from './AllModelListItem.vue'

const { data, isLoading, isFetching, refetch } = useModelsFetch({})

const tab = useRouteQuery<string | undefined>('tab')

const keyword = ref('')
const modelTypeFilter = ref('')
const featureFilter = ref('')

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
    if (featureFilter.value && !(model.spec.features || []).some((item) => item === featureFilter.value)) {
      return false
    }
    return true
  })
})
</script>
<template>
  <div class=":uno: p-2">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <div
            class=":uno: relative flex flex-col flex-wrap items-start gap-4 sm:flex-row sm:items-center"
          >
            <div class=":uno: w-full flex flex-1 items-center gap-2 sm:w-auto">
              <SearchInput sync v-model="keyword" />
            </div>
            <select
              v-model="modelTypeFilter"
              class=":uno: h-8 rounded border border-gray-200 bg-white px-2 text-sm"
            >
              <option value="">全部类型</option>
              <option v-for="item in MODEL_TYPE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
            <select
              v-model="featureFilter"
              class=":uno: h-8 rounded border border-gray-200 bg-white px-2 text-sm"
            >
              <option value="">全部特性</option>
              <option v-for="item in MODEL_FEATURE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
            <VSpace spacing="lg" class=":uno: flex-wrap">
              <div class=":uno: flex flex-row gap-2">
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
              <VButton type="secondary" @click="tab = 'config'"> 配置模型 </VButton>
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
        <div class=":uno: h-8 flex items-center">
          <span class=":uno: text-sm text-gray-500">
            {{
              keyword || modelTypeFilter || featureFilter
                ? `找到 ${filteredModels.length} 个模型`
                : `共 ${allModels.length} 个模型`
            }}
          </span>
        </div>
      </template>
    </VCard>
  </div>
</template>
