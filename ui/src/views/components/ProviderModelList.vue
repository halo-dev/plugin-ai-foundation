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
import { computed, ref } from 'vue'
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

const groupedModels = computed(() => {
  const groups = new Map<string, NonNullable<typeof models.value>>()
  for (const model of models.value || []) {
    const key = model.spec.group || '未分组'
    groups.set(key, [...(groups.get(key) || []), model])
  }
  return Array.from(groups.entries()).map(([group, items]) => ({ group, items }))
})

const discoveryModalVisible = ref(false)
const creationModalVisible = ref(false)
</script>
<template>
  <VCard :body-class="['!p-0']" title="模型列表">
    <template #actions>
      <div class=":uno: px-4">
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
    />

    <div v-else>
      <details v-for="group in groupedModels" :key="group.group" class=":uno: border-t first:border-t-0" open>
        <summary class=":uno: cursor-pointer bg-gray-50 px-4 py-2 text-sm font-medium text-gray-700">
          {{ group.group }} · {{ group.items.length }}
        </summary>
        <VEntityContainer>
          <ProviderModelListItem
            v-for="model in group.items"
            :key="model.metadata.name"
            :model="model"
          />
        </VEntityContainer>
      </details>
    </div>
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
