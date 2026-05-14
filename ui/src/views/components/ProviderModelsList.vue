<script lang="ts" setup>
import type { AiProvider } from '@/api/generated'
import { useModelsFetch } from '@/composables/use-models-fetch'
import {
  VButton,
  VCard,
  VDropdownItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VSpace,
} from '@halo-dev/components'
import { computed, ref } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiDownloadCloudLine from '~icons/ri/download-cloud-line'
import ModelsDiscoveryModal from './ModelsDiscoveryModal.vue'

const props = defineProps<{
  provider: AiProvider
}>()

const providerName = computed(() => props.provider.metadata.name)

const { data: models, isLoading } = useModelsFetch({
  providerName,
})

const discoveryModalVisible = ref(false)
</script>
<template>
  <VCard :body-class="['!p-0']" title="模型列表">
    <template #actions>
      <div class=":uno: px-4">
        <VSpace>
          <VButton type="secondary" size="sm" @click="discoveryModalVisible = true">
            <template #icon>
              <RiDownloadCloudLine />
            </template>
            从供应商获取
          </VButton>
          <VButton type="primary" size="sm">
            <template #icon>
              <RiAddLine />
            </template>
            添加模型
          </VButton>
        </VSpace>
      </div>
    </template>

    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="models?.length === 0"
      title="暂无模型"
      message="你可以从供应商获取或者手动添加模型"
    />

    <VEntityContainer>
      <VEntity v-for="model in models" :key="model.metadata.name">
        <template #start>
          <VEntityField :title="model.spec.displayName" :description="model.spec.modelId" />
        </template>
        <template #end></template>
        <template #dropdownItems>
          <VDropdownItem>删除</VDropdownItem>
        </template>
      </VEntity>
    </VEntityContainer>
  </VCard>

  <ModelsDiscoveryModal
    v-if="provider && discoveryModalVisible"
    :provider="provider"
    @close="discoveryModalVisible = false"
  />
</template>
