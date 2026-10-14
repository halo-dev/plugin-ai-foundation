<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import type { ToolDescriptor } from '@/api/generated'
import { QK_TOOLS, useToolsFetch } from '@/composables/use-tools-fetch'
import { filterTools, groupTools, toolFilterOptions } from '@/utils/tools'
import {
  IconRefreshLine,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VTag,
} from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref, shallowRef } from 'vue'
import ToolCatalogFilters from './ToolCatalogFilters.vue'
import ToolCatalogList from './ToolCatalogList.vue'
import ToolConfigurationModal from './ToolConfigurationModal.vue'
import ToolDetailsModal from './ToolDetailsModal.vue'

const { data, isLoading, isError, isFetching, refetch } = useToolsFetch()
const keyword = ref('')
const pluginName = ref('')
const category = ref('')
const selectedTool = shallowRef<ToolDescriptor>()
const configuringTool = shallowRef<ToolDescriptor>()
const pendingIds = ref<string[]>([])
const queryClient = useQueryClient()
const tools = computed(() => data.value || [])
const options = computed(() => toolFilterOptions(tools.value))
const visibleTools = computed(() =>
  filterTools(tools.value, {
    keyword: keyword.value,
    pluginName: pluginName.value,
    category: category.value,
  }),
)
const sourceGroups = computed(() => {
  const sources = new Map<string, ToolDescriptor[]>()
  for (const tool of visibleTools.value) {
    const source = tool.pluginName || '未知插件'
    const tools = sources.get(source)
    if (tools) tools.push(tool)
    else sources.set(source, [tool])
  }
  return Array.from(sources, ([pluginName, tools]) => ({
    pluginName,
    displayName: pluginName === 'ai-foundation' ? 'AI Foundation' : pluginName,
    count: tools.length,
    groups: groupTools(tools),
  }))
})

async function updateEnabled(tool: ToolDescriptor, enabled: boolean) {
  if (!tool.id || !tool.pluginName || !tool.localName || pendingIds.value.includes(tool.id)) return
  pendingIds.value = [...pendingIds.value, tool.id]
  try {
    await aiConsoleApiClient.tool.updateToolState({
      pluginName: tool.pluginName,
      localName: tool.localName,
      toolEnabledRequest: { enabled },
    })
    await queryClient.invalidateQueries({ queryKey: [QK_TOOLS] })
    Toast.success(enabled ? '工具已启用' : '工具已禁用')
  } catch {
    Toast.error('工具状态更新失败，请重试')
  } finally {
    pendingIds.value = pendingIds.value.filter((id) => id !== tool.id)
  }
}
</script>

<template>
  <VCard :body-class="['!p-0']">
    <template #header>
      <div
        class=":uno: w-full flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <ToolCatalogFilters
          v-model:keyword="keyword"
          v-model:plugin-name="pluginName"
          v-model:category="category"
          :plugin-names="options.pluginNames"
          :categories="options.categories"
        />
        <VButton
          size="sm"
          :disabled="isFetching"
          class=":uno: flex-none self-end sm:self-center"
          aria-label="刷新工具"
          @click="refetch()"
        >
          <template #icon
            ><IconRefreshLine :class="{ ':uno: animate-spin': isFetching }"
          /></template>
          刷新
        </VButton>
      </div>
    </template>
    <VLoading v-if="isLoading" />
    <div
      v-else-if="isError"
      role="alert"
      class=":uno: flex flex-col items-center gap-3 px-4 py-12 text-sm text-gray-500"
    >
      <span>无法加载工具列表</span><VButton size="sm" @click="refetch()">重试</VButton>
    </div>
    <VEmpty
      v-else-if="!tools.length"
      title="暂无工具"
      message="启用提供工具的插件后，工具会自动出现在这里"
    />
    <VEmpty
      v-else-if="!visibleTools.length"
      title="无匹配结果"
      message="请调整搜索内容或筛选条件"
    />
    <div v-else class=":uno: divide-y divide-gray-100">
      <section
        v-for="source in sourceGroups"
        :key="source.pluginName"
        :aria-label="`${source.displayName} 提供的工具`"
        class=":uno: py-2"
      >
        <header class=":uno: flex items-center gap-2 px-4 py-2">
          <h2 class=":uno: m-0 break-all text-sm text-gray-700 font-medium">
            {{ source.displayName }}
          </h2>
          <VTag>{{ source.count }}</VTag>
        </header>
        <ToolCatalogList
          :groups="source.groups"
          management
          :pending-ids="pendingIds"
          @details="selectedTool = $event"
          @configure="configuringTool = $event"
          @enabled="updateEnabled"
        />
      </section>
    </div>
    <template #footer
      ><span class=":uno: text-sm text-gray-500"
        >共 {{ tools.length }} 个工具，当前显示 {{ visibleTools.length }} 个</span
      ></template
    >
  </VCard>
  <ToolDetailsModal v-if="selectedTool" :tool="selectedTool" @close="selectedTool = undefined" />
  <ToolConfigurationModal
    v-if="configuringTool"
    :key="configuringTool.id"
    :tool="configuringTool"
    @close="configuringTool = undefined"
  />
</template>
