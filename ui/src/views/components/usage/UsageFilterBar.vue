<script setup lang="ts">
import type { AiModel, AiProvider, CallerPluginInfo } from '@/api/generated'
import { USAGE_RANGE_OPTIONS, type UsageFilterState } from '@/composables/use-usage-filters'
import {
  USAGE_MODEL_TYPE_OPTIONS,
  USAGE_OPERATION_OPTIONS,
  USAGE_QUALITY_OPTIONS,
  USAGE_STATUS_OPTIONS,
} from '@/utils/usage'
import { IconRefreshLine, VButton, VDropdown, VDropdownItem, VSpace } from '@halo-dev/components'
import { computed, ref, watch } from 'vue'
import MingcuteFilterLine from '~icons/mingcute/filter-line'
import MingcuteMore2Line from '~icons/mingcute/more-2-line'
import UsageSelect from './UsageSelect.vue'

const props = defineProps<{
  state: UsageFilterState
  callers?: CallerPluginInfo[]
  providers?: AiProvider[]
  models?: AiModel[]
  featureInvalid?: boolean
  hasDimensionFilters?: boolean
  fetching?: boolean
}>()

const emit = defineEmits<{
  (event: 'change', patch: Partial<UsageFilterState>): void
  (event: 'clear'): void
  (event: 'refresh'): void
  (event: 'reset'): void
}>()

const callerItems = computed(() => [
  { label: '全部', value: undefined },
  ...(props.callers || [])
    .filter((caller) => caller.pluginName)
    .map((caller) => ({
      label: caller.displayName || (caller.pluginName as string),
      description: caller.displayName ? caller.pluginName : undefined,
      value: caller.pluginName as string,
    })),
])

const providerItems = computed(() => [
  { label: '全部', value: undefined },
  ...(props.providers || []).map((provider) => ({
    label: provider.spec.displayName || provider.metadata.name,
    description: provider.spec.displayName ? provider.metadata.name : undefined,
    value: provider.metadata.name,
  })),
])

const modelItems = computed(() => [
  { label: '全部', value: undefined },
  ...(props.models || []).map((model) => ({
    label: model.spec.displayName || model.metadata.name,
    description: model.spec.displayName ? model.metadata.name : undefined,
    value: model.metadata.name,
  })),
])

const expanded = ref(false)
const advancedActive = computed(() =>
  [
    props.state.modelType,
    props.state.operation,
    props.state.usageQuality,
    props.state.feature,
  ].some(Boolean),
)

watch(
  advancedActive,
  (active) => {
    if (active) expanded.value = true
  },
  { immediate: true },
)

const ALL_OPTION = { label: '全部', value: undefined }

function update(patch: Partial<UsageFilterState>) {
  emit('change', patch)
}
</script>

<template>
  <div class=":uno: flex flex-col gap-4">
    <div class=":uno: flex flex-wrap items-center gap-3">
      <div class=":uno: mr-auto min-w-0">
        <h2 class=":uno: text-base text-gray-950 font-semibold">用量概览</h2>
        <p class=":uno: mt-1 text-xs text-gray-500">查看 AI 消耗与调用活动</p>
      </div>
      <div class=":uno: usage-toolbar">
        <VSpace class=":uno: flex-wrap" align="center">
          <UsageSelect
            :model-value="state.range"
            label="时间范围"
            :items="[...USAGE_RANGE_OPTIONS]"
            @update:model-value="update({ range: $event || '30d' })"
          />
        </VSpace>

        <VSpace class=":uno: ml-auto shrink-0" align="center">
          <VButton
            v-tooltip="'刷新'"
            aria-label="刷新"
            size="sm"
            class=":uno: usage-toolbar-button"
            :loading="fetching"
            @click="emit('refresh')"
          >
            <template #icon><IconRefreshLine /></template>
          </VButton>
          <VDropdown placement="bottom-end" :distance="6">
            <VButton aria-label="更多操作" size="sm" class=":uno: usage-toolbar-button"
              ><template #icon><MingcuteMore2Line /></template
            ></VButton>
            <template #popper
              ><VDropdownItem v-close-popper type="danger" @click="emit('reset')"
                >重置统计</VDropdownItem
              ></template
            >
          </VDropdown>
        </VSpace>
      </div>
    </div>

    <div class=":uno: flex flex-wrap items-center gap-2" v-if="state.range === 'custom'">
      <input
        type="date"
        aria-label="开始日期"
        class=":uno: usage-date-input"
        :value="state.fromDate"
        @input="update({ fromDate: ($event.target as HTMLInputElement).value || undefined })"
      />
      <span class=":uno: text-xs text-gray-400">至</span>
      <input
        type="date"
        aria-label="结束日期"
        class=":uno: usage-date-input"
        :value="state.toDate"
        @input="update({ toDate: ($event.target as HTMLInputElement).value || undefined })"
      />
    </div>
    <div v-if="state.range === 'custom'" class=":uno: text-xs text-gray-400">
      起止日期均包含在所选范围内。
    </div>

    <div class=":uno: flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
      <UsageSelect
        :model-value="state.callerPlugin"
        label="调用插件"
        :items="callerItems"
        searchable
        @update:model-value="update({ callerPlugin: $event || undefined })"
      />
      <UsageSelect
        :model-value="state.providerName"
        label="供应商"
        allow-custom
        :items="providerItems"
        searchable
        @update:model-value="update({ providerName: $event || undefined })"
      />
      <UsageSelect
        :model-value="state.modelName"
        label="模型"
        allow-custom
        :items="modelItems"
        searchable
        @update:model-value="update({ modelName: $event || undefined })"
      />
      <UsageSelect
        :model-value="state.status"
        label="状态"
        :items="[ALL_OPTION, ...USAGE_STATUS_OPTIONS]"
        @update:model-value="update({ status: $event || undefined })"
      />
      <VButton
        size="sm"
        class=":uno: usage-filter-more"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
        ><template #icon><MingcuteFilterLine /></template>更多筛选</VButton
      >
      <VButton
        v-if="hasDimensionFilters"
        size="sm"
        ghost
        class=":uno: usage-filter-more"
        aria-label="清除筛选"
        @click="emit('clear')"
        >清除筛选</VButton
      >
    </div>
    <div
      v-show="expanded"
      class=":uno: flex flex-wrap items-center gap-4 border-t border-gray-100 pt-3"
    >
      <UsageSelect
        :model-value="state.modelType"
        label="模型类型"
        :items="[ALL_OPTION, ...USAGE_MODEL_TYPE_OPTIONS]"
        @update:model-value="update({ modelType: $event || undefined })"
      />
      <UsageSelect
        :model-value="state.operation"
        label="操作"
        :items="[ALL_OPTION, ...USAGE_OPERATION_OPTIONS]"
        @update:model-value="update({ operation: $event || undefined })"
      />
      <UsageSelect
        :model-value="state.usageQuality"
        label="用量质量"
        :items="[ALL_OPTION, ...USAGE_QUALITY_OPTIONS]"
        @update:model-value="update({ usageQuality: $event || undefined })"
      />
      <div class=":uno: flex items-center gap-1">
        <input
          type="text"
          aria-label="功能标识"
          placeholder="功能标识"
          class=":uno: usage-feature-input"
          :aria-invalid="featureInvalid || undefined"
          :class="featureInvalid ? ':uno: border-red-300' : ':uno: border-gray-200'"
          :value="state.feature"
          @input="update({ feature: ($event.target as HTMLInputElement).value || undefined })"
        />
      </div>
    </div>
    <div v-if="featureInvalid" class=":uno: text-xs text-red-600">
      功能标识需匹配 [a-z0-9._-]，长度 1-64；当前值不会作为过滤条件。
    </div>
  </div>
</template>

<style scoped>
.usage-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
@media (max-width: 639px) {
  .usage-toolbar {
    width: 100%;
  }
}
.usage-toolbar-button {
  width: 32px;
  height: 32px;
  padding: 0;
}
.usage-toolbar-button :deep(.btn-icon) {
  margin: 0;
  width: 16px;
  height: 16px;
}
.usage-toolbar-button :deep(.btn-content) {
  display: none;
}
.usage-filter-more {
  height: 32px;
}
.usage-date-input,
.usage-feature-input {
  height: 32px;
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
  padding: 0 10px;
  font-size: 12px;
  color: #374151;
}
.usage-date-input {
  width: 150px;
}
.usage-feature-input {
  width: 180px;
}
.usage-date-input:focus,
.usage-feature-input:focus {
  border-color: #9ca3af;
  outline: none;
  box-shadow: 0 0 0 1px #e5e7eb;
}
.usage-feature-input[aria-invalid='true'] {
  border-color: #fca5a5;
}
</style>
