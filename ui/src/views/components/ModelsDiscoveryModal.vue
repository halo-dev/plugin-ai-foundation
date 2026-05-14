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

  if (!data) return

  for (const model of data) {
    const generateName =
      `${props.provider.metadata.name}-${model.modelId.replace(/\//g, '-')}-`.toLocaleLowerCase()
    const newModel: AiModel = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiModel',
      metadata: {
        generateName,
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

    // TODO: 优化
    await aiConsoleApiClient.model.createModel({
      aiModel: newModel,
    })
  }

  Toast.success(`成功导入 ${data.length} 个模型`)

  modal.value?.close()

  queryClient.invalidateQueries({ queryKey: [QK_MODELS] })
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
      <VLoading v-if="isLoading" />
      <VEmpty v-else-if="models?.models.length === 0" title="无数据" message="无法获取到模型列表" />
      <VEntityContainer v-else>
        <VEntity
          @click="toggleSelection(model)"
          v-for="model in models?.models"
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
