<script setup lang="ts">
import type { AiProvider } from '@/api/generated'
import { useModels } from '@/composables/useModels'
import { useDeleteProvider, useProviders } from '@/composables/useProviders'
import { Dialog, Toast, VButton, VModal, VPageHeader } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiBrainLine from '~icons/ri/brain-line'
import ProviderDetail from './ProviderDetail.vue'
import ProviderForm from './ProviderForm.vue'
import ProviderList from './ProviderList.vue'

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
  const hasModels = allModels.value?.some((m) => m.spec.providerName === provider.metadata.name)
  if (hasModels) {
    Toast.warning('该供应商下仍有模型，请先删除所有模型后再删除供应商')
    return
  }
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
</script>

<template>
  <VPageHeader title="AI 模型配置">
    <template #icon>
      <RiBrainLine class=":uno: mr-2 h-6 w-6 text-gray-700" />
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

  <div class=":uno: h-[calc(100vh-64px)] flex gap-0">
    <div class=":uno: min-w-80 w-80 overflow-y-auto border-r border-gray-200 bg-white">
      <ProviderList
        :providers="providers || []"
        :loading="providersLoading"
        :selected-name="selectedProvider?.metadata.name"
        @select="onSelectProvider"
        @edit="onEditProvider"
        @delete="onDeleteProvider"
      />
    </div>
    <div class=":uno: flex-1 overflow-y-auto bg-slate-50 p-4">
      <ProviderDetail
        v-if="selectedProvider"
        :key="selectedProvider.metadata.name"
        :provider="selectedProvider"
        @edit="onEditProvider"
        @delete="onDeleteProvider"
      />
      <div v-else class=":uno: h-full flex items-center justify-center text-gray-400">
        <p>请从左侧选择一个供应商查看详情</p>
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
