<script setup lang="ts">
import { ref, computed } from 'vue'
import { VPageHeader, VButton, VModal } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import RiBrainLine from '~icons/ri/brain-line'
import RiAddLine from '~icons/ri/add-line'
import ProviderList from './ProviderList.vue'
import ProviderDetail from './ProviderDetail.vue'
import ProviderForm from './ProviderForm.vue'
import { useProviders, useDeleteProvider } from '@/composables/useProviders'
import { useModels } from '@/composables/useModels'
import type { AiProvider } from '@/types'

const { data: providers, isLoading: providersLoading } = useProviders()
const { data: allModels } = useModels()
const deleteProvider = useDeleteProvider()
const queryClient = useQueryClient()

const selectedProvider = ref<AiProvider | null>(null)
const providerFormVisible = ref(false)
const editingProvider = ref<AiProvider | null>(null)

function onSelectProvider(provider: AiProvider) {
  selectedProvider.value = provider
}

function onCreateProvider() {
  editingProvider.value = null
  providerFormVisible.value = true
}

function onEditProvider(provider: AiProvider) {
  editingProvider.value = provider
  providerFormVisible.value = true
}

function onDeleteProvider(provider: AiProvider) {
  const hasModels = allModels.value?.some(
    (m) => m.spec.providerName === provider.metadata.name
  )
  if (hasModels) {
    // eslint-disable-next-line no-undef
    Toast.warning('该供应商下仍有模型，请先删除所有模型后再删除供应商')
    return
  }
  // eslint-disable-next-line no-undef
  Dialog.warning({
    title: '确认删除',
    description: `确定要删除供应商 "${provider.spec.displayName}" 吗？`,
    onConfirm: async () => {
      await deleteProvider.mutateAsync(provider.metadata.name)
      if (selectedProvider.value?.metadata.name === provider.metadata.name) {
        selectedProvider.value = null
      }
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
    },
  })
}

function onFormSaved() {
  providerFormVisible.value = false
  editingProvider.value = null
  queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
}

const providerName = computed(() => selectedProvider.value?.metadata.name || '')
</script>

<template>
  <VPageHeader title="AI 模型配置">
    <template #icon>
      <RiBrainLine class="h-6 w-6 text-gray-700 mr-2" />
    </template>
    <template #actions>
      <VButton type="primary" @click="onCreateProvider">
        <template #icon>
          <RiAddLine />
        </template>
        添加模型供应商
      </VButton>
    </template>
  </VPageHeader>

  <div class="provider-manager">
    <div class="provider-manager__list">
      <ProviderList
        :providers="providers || []"
        :loading="providersLoading"
        :selected-name="selectedProvider?.metadata.name"
        @select="onSelectProvider"
        @edit="onEditProvider"
        @delete="onDeleteProvider"
      />
    </div>
    <div class="provider-manager__detail">
      <ProviderDetail
        v-if="selectedProvider"
        :provider="selectedProvider"
        @edit="onEditProvider"
        @delete="onDeleteProvider"
      />
      <div v-else class="empty-state">
        <p class="text-gray-500">请从左侧选择一个供应商查看详情</p>
      </div>
    </div>
  </div>

  <VModal
    v-if="providerFormVisible"
    :title="editingProvider ? '编辑供应商' : '添加供应商'"
    :width="600"
    @close="providerFormVisible = false"
  >
    <ProviderForm
      :provider="editingProvider"
      @saved="onFormSaved"
      @cancel="providerFormVisible = false"
    />
  </VModal>
</template>

<style lang="scss" scoped>
.provider-manager {
  display: flex;
  height: calc(100vh - 64px);
  gap: 0;

  &__list {
    width: 320px;
    min-width: 320px;
    border-right: 1px solid #e5e7eb;
    background: #fff;
    overflow-y: auto;
  }

  &__detail {
    flex: 1;
    overflow-y: auto;
    background: #f8fafc;
    padding: 16px;
  }
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9ca3af;
}
</style>
