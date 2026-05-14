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
import ModelsDiscoveryModal from './ModelsDiscoveryModal.vue'
import ProviderModelListItem from './ProviderModelListItem.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const providerName = computed(() => props.provider.metadata.name)

const { data: models, isLoading } = useModelsFetch({
  providerName,
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

    <VEntityContainer v-else>
      <ProviderModelListItem v-for="model in models" :key="model.metadata.name" :model="model" />
    </VEntityContainer>
  </VCard>

  <ModelsDiscoveryModal
    v-if="provider && discoveryModalVisible"
    :provider="provider"
    @close="discoveryModalVisible = false"
  />

  <ModelCreationModal
    v-if="provider && creationModalVisible"
    :provider-name="provider.metadata.name"
    @close="creationModalVisible = false"
  />
</template>
