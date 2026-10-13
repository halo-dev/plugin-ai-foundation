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
import UsageDateRangePicker from './UsageDateRangePicker.vue'
import UsageSegmented from './UsageSegmented.vue'
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
        <h2 class=":uno: text-sm text-gray-950 font-semibold">用量统计</h2>
      </div>
      <UsageSegmented
        :model-value="state.range"
        aria-label="时间范围"
        :options="[...USAGE_RANGE_OPTIONS]"
        @update:model-value="update({ range: $event || '30d' })"
      />
      <VSpace class=":uno: shrink-0" spacing="xs" align="center">
        <VButton
          v-tooltip="'刷新'"
          aria-label="刷新"
          size="sm"
          ghost
          class=":uno: usage-toolbar-button"
          circle
          @click="emit('refresh')"
        >
          <IconRefreshLine :class="{ ':uno: animate-spin': fetching }" />
        </VButton>
        <VDropdown placement="bottom-end" :distance="6">
          <VButton aria-label="更多操作" circle ghost size="sm" class=":uno: usage-toolbar-button">
            <MingcuteMore2Line />
          </VButton>
          <template #popper>
            <VDropdownItem v-close-popper type="danger" @click="emit('reset')">
              重置统计
            </VDropdownItem>
          </template>
        </VDropdown>
      </VSpace>
    </div>

    <div class=":uno: flex flex-wrap items-center gap-2" v-if="state.range === 'custom'">
      <UsageDateRangePicker
        :from-date="state.fromDate"
        :to-date="state.toDate"
        @update="update($event)"
      />
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
      仅支持小写字母、数字和 . _ -，长度 1-64；当前输入不会生效。
    </div>
  </div>
</template>

<style scoped>
.usage-filter-more {
  height: 32px;
}
.usage-feature-input {
  width: 180px;
  height: 32px;
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
  padding: 0 10px;
  font-size: 12px;
  color: #374151;
}
.usage-feature-input:focus {
  border-color: #9ca3af;
  outline: none;
  box-shadow: 0 0 0 1px #e5e7eb;
}
.usage-feature-input[aria-invalid='true'] {
  border-color: #fca5a5;
}
</style>
