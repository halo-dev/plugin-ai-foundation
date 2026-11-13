<script setup lang="ts">
import { aiConsoleApiClient } from '@/api'
import type { ToolConfig, ToolDescriptor } from '@/api/generated'
import { QK_TOOLS } from '@/composables/use-tools-fetch'
import { toolDisplayName } from '@/utils/tools'
import { submitForm, type FormKitSchemaCondition, type FormKitSchemaNode } from '@formkit/core'
import { Toast, VButton, VLoading, VModal, VSpace } from '@halo-dev/components'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, toRaw, useId, watch } from 'vue'

const props = defineProps<{ tool: ToolDescriptor }>()
const emit = defineEmits<{ close: [] }>()
const modal = ref<InstanceType<typeof VModal>>()
const queryClient = useQueryClient()
const formId = `ai-tool-config-${useId()}`
const values = ref<Record<string, unknown>>({})
const formInitialized = ref(false)
// FormKit adds runtime slot data to this object, so keep it separate from saved values.
const schemaData = computed(() => ({ ...values.value }))
const saving = ref(false)
const saveError = ref('')
const schema = computed(
  () =>
    (props.tool.configurationSchema as unknown as (FormKitSchemaNode | FormKitSchemaCondition)[]) ||
    [],
)
const hasConfiguration = computed(() => schema.value.length > 0)
const { data, isLoading, isFetching, isError, refetch } = useQuery({
  queryKey: [
    'plugin:ai-foundation:tool-configuration',
    props.tool.pluginName,
    props.tool.localName,
  ],
  queryFn: async () => {
    const { data } = await aiConsoleApiClient.tool.getToolConfiguration({
      pluginName: props.tool.pluginName || '',
      localName: props.tool.localName || '',
    })
    return data
  },
  enabled: hasConfiguration,
  cacheTime: 0,
  staleTime: 0,
  refetchOnMount: 'always',
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
})

watch(
  [data, isFetching, isError],
  ([configuration, fetching, error]) => {
    if (configuration && !fetching && !error && !formInitialized.value) {
      values.value = structuredClone(toRaw(configuration.values || {}))
      formInitialized.value = true
    }
  },
  { immediate: true },
)

async function saveConfiguration(formValues: Record<string, unknown>) {
  if (saving.value || !props.tool.pluginName || !props.tool.localName) return
  saving.value = true
  saveError.value = ''
  try {
    await aiConsoleApiClient.tool.updateToolConfiguration({
      pluginName: props.tool.pluginName,
      localName: props.tool.localName,
      toolConfig: { values: formValues as ToolConfig['values'] },
    })
    await queryClient.invalidateQueries({ queryKey: [QK_TOOLS] })
    Toast.success('工具配置已保存')
    modal.value?.close()
  } catch {
    saveError.value = '配置保存失败，请检查配置后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <VModal
    ref="modal"
    :title="`配置 ${toolDisplayName(tool)}`"
    :width="620"
    mount-to-body
    @close="emit('close')"
  >
    <div class=":uno: max-h-[70vh] overflow-y-auto">
      <VLoading v-if="hasConfiguration && (isLoading || (!formInitialized && isFetching))" />
      <div
        v-else-if="hasConfiguration && isError"
        role="alert"
        class=":uno: flex flex-col items-center gap-3 py-8 text-sm text-gray-500"
      >
        <span>无法加载工具配置</span><VButton size="sm" @click="refetch()">重试</VButton>
      </div>
      <FormKit
        v-else-if="hasConfiguration"
        :id="formId"
        v-model="values"
        type="form"
        :actions="false"
        :disabled="saving"
        @submit="saveConfiguration"
      >
        <FormKitSchema :schema="schema" :data="schemaData" />
      </FormKit>
      <p v-else class=":uno: text-sm text-gray-500">此工具无需配置</p>
      <p v-if="saveError" role="alert" class=":uno: mt-3 text-sm text-red-500">{{ saveError }}</p>
    </div>
    <template #footer>
      <VSpace>
        <VButton
          v-if="hasConfiguration"
          type="secondary"
          :loading="saving"
          :disabled="saving || isLoading || isError"
          @click="submitForm(formId)"
          >保存</VButton
        >
        <VButton :disabled="saving" @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
