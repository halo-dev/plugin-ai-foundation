<script lang="ts" setup>
import type { AiProvider } from '@/api/generated'
import { useModelsFetch } from '@/composables/use-models-fetch'
import {
  VButton,
  VCard,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VEntityContainer,
  VLoading,
} from '@halo-dev/components'
import { computed, shallowRef } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import ModelCreationModal from './ModelCreationModal.vue'
import ProviderModelListItem from './ProviderModelListItem.vue'
import ProviderModelsDiscoveryModal from './ProviderModelsDiscoveryModal.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const providerName = computed(() => props.provider.metadata.name)

const { data: models, isLoading } = useModelsFetch({
  providerName,
})

const modelCount = computed(() => models.value?.length || 0)
const discoveryModalVisible = shallowRef(false)
const creationModalVisible = shallowRef(false)
</script>
<template>
  <VCard :body-class="['!p-0']">
    <template #header>
      <div class=":uno: w-full flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
        <div class=":uno: min-w-0 flex-1">
          <div class=":uno: text-sm text-gray-950 font-semibold">模型列表</div>
          <div class=":uno: mt-0.5 text-xs text-gray-500">
            当前供应商下共 {{ modelCount }} 个模型
          </div>
        </div>
        <VDropdown>
          <VButton type="secondary" size="sm">
            <template #icon>
              <RiAddLine />
            </template>
            添加模型
          </VButton>
          <template #popper>
            <VDropdownItem @click="discoveryModalVisible = true">从供应商获取</VDropdownItem>
            <VDropdownItem @click="creationModalVisible = true">手动添加</VDropdownItem>
          </template>
        </VDropdown>
      </div>
    </template>

    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="models?.length === 0"
      title="暂无模型"
      message="你可以从供应商获取或者手动添加模型"
    >
      <template #actions>
        <div class=":uno: flex flex-wrap justify-center gap-2">
          <VButton type="secondary" @click="discoveryModalVisible = true">从供应商获取</VButton>
          <VButton @click="creationModalVisible = true">手动添加</VButton>
        </div>
      </template>
    </VEmpty>

    <VEntityContainer v-else>
      <ProviderModelListItem v-for="model in models" :key="model.metadata.name" :model="model" />
    </VEntityContainer>
  </VCard>

  <ProviderModelsDiscoveryModal
    v-if="provider && discoveryModalVisible"
    :provider="provider"
    @close="discoveryModalVisible = false"
  />

  <ModelCreationModal
    v-if="provider && creationModalVisible"
    :provider="provider"
    @close="creationModalVisible = false"
  />
</template>
