<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import type { AiModel, AiProvider } from '@/api/generated'
import {
  QK_MODELS,
  useDiscoverModelsFetch,
  type DiscoveredModel,
} from '@/composables/use-models-fetch'
import { useProviderType } from '@/composables/use-provider-types-fetch'
import {
  Toast,
  VButton,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VSpace,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { useFuse } from '@vueuse/integrations/useFuse'
import { computed, ref, toRefs } from 'vue'

const props = defineProps<{
  provider: AiProvider
}>()

const { provider } = toRefs(props)

const emit = defineEmits<{
  (event: 'close'): void
}>()

const queryClient = useQueryClient()

const modal = ref<InstanceType<typeof VModal> | null>(null)

const providerName = computed(() => props.provider.metadata.name)

const { data: models, isLoading } = useDiscoverModelsFetch(providerName)

const keyword = ref('')

const allModels = computed(() => models.value?.models || [])

const { results } = useFuse(keyword, allModels, {
  fuseOptions: {
    keys: ['displayName', 'modelId'],
    threshold: 0.3,
  },
})

const filteredModels = computed(() => {
  if (!keyword.value) {
    return allModels.value
  }
  return results.value.map((r) => r.item)
})

const selectedModels = ref<Set<string>>(new Set())

function toggleSelection(model: DiscoveredModel) {
  if (selectedModels.value.has(model.modelId)) {
    selectedModels.value.delete(model.modelId)
  } else {
    selectedModels.value.add(model.modelId)
  }
}

async function handleImport() {
  const data = models.value?.models.filter((model) => selectedModels.value.has(model.modelId))

  if (!data || data.length === 0) return

  const results = await Promise.allSettled(
    data.map(async (model) => {
      const newModel: AiModel = {
        apiVersion: 'aifoundation.halo.run/v1alpha1',
        kind: 'AiModel',
        metadata: {
          name: '',
        },
        spec: {
          providerName: props.provider.metadata.name,
          modelId: model.modelId,
          displayName: model.displayName || model.modelId,
          enabled: true,
          endpointType: inferEndpointType(model),
        },
      }

      await aiConsoleApiClient.model.createModel({
        aiModel: newModel,
      })
      return model.modelId
    }),
  )

  const succeeded = results.filter((r) => r.status === 'fulfilled').length
  const failed = results.filter((r) => r.status === 'rejected')

  if (failed.length > 0) {
    const failedIds = failed
      .map(
        (r, i) =>
          `${data[i].modelId}: ${(r as PromiseRejectedResult).reason?.message || '未知错误'}`,
      )
      .join('\n')
    Toast.warning(`导入完成：成功 ${succeeded} 个，失败 ${failed.length} 个\n${failedIds}`)
  } else {
    Toast.success(`成功导入 ${succeeded} 个模型`)
  }

  modal.value?.close()

  if (succeeded > 0) {
    queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
  }
}

const providerType = useProviderType(provider)

// TODO: 优化
function inferEndpointType(dm: DiscoveredModel): string {
  const endpointTypes = providerType.value?.supportedEndpointTypes || []
  const caps = dm.capabilities || []
  if (caps.includes('embedding')) {
    const embeddingType = endpointTypes.find((t) => t.includes('embedding'))
    return embeddingType || endpointTypes[0] || 'openai-embedding'
  }
  const chatType = endpointTypes.find((t) => t.includes('chat'))
  return chatType || endpointTypes[0] || 'openai-chat'
}
</script>

<template>
  <VModal
    title="从供应商获取模型"
    ref="modal"
    :width="800"
    mount-to-body
    :centered="false"
    :body-class="['!p-0']"
    @close="emit('close')"
  >
    <div>
      <div class=":uno: p-4 border-b border-gray-100">
        <SearchInput sync v-model="keyword" placeholder="搜索模型名称或 ID..." />
      </div>
      <VLoading v-if="isLoading" />
      <VEmpty v-else-if="allModels.length === 0" title="无数据" message="无法获取到模型列表" />
      <VEmpty
        v-else-if="filteredModels.length === 0"
        title="无匹配结果"
        message="未找到匹配的模型"
      />
      <VEntityContainer v-else>
        <VEntity
          @click="toggleSelection(model)"
          v-for="model in filteredModels"
          :key="model.modelId"
        >
          <template #checkbox>
            <input @click.stop type="checkbox" v-model="selectedModels" :value="model.modelId" />
          </template>
          <template #start>
            <VEntityField :title="model.displayName" :description="model.modelId" />
          </template>
        </VEntity>
      </VEntityContainer>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" @click="handleImport" :disabled="selectedModels.size === 0">
          {{ selectedModels.size > 0 ? `导入 ${selectedModels.size} 个模型` : '导入' }}
        </VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
