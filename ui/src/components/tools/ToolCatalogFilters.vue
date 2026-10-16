<script setup lang="ts">
import { toolCategoryLabel } from '@/utils/tools'
import { computed, useId, type Ref } from 'vue'

const props = withDefaults(
  defineProps<{
    pluginNames: string[]
    categories: string[]
    searchPlaceholder?: string
    disabled?: boolean
    compact?: boolean
    showPluginFilter?: boolean
    showCategoryFilter?: boolean
  }>(),
  {
    searchPlaceholder: '搜索工具名称、标签...',
    disabled: false,
    compact: false,
    showPluginFilter: true,
    showCategoryFilter: true,
  },
)

const keyword = defineModel<string>('keyword', { default: '' })
const pluginName = defineModel<string>('pluginName', { default: '' })
const category = defineModel<string>('category', { default: '' })
const id = useId()
const keywordInput = formKitValue(keyword)
const pluginNameInput = formKitValue(pluginName)
const categoryInput = formKitValue(category)
const sourceOptions = computed(() => [
  { label: '全部来源', value: '' },
  ...props.pluginNames.map((value) => ({ label: value, value })),
])
const categoryOptions = computed(() => [
  { label: '全部分类', value: '' },
  ...props.categories.map((value) => ({ label: toolCategoryLabel(value), value })),
])

function formKitValue(model: Ref<string>) {
  return computed({
    get: () => model.value,
    set: (value: unknown) => {
      if (!props.disabled) model.value = typeof value === 'string' ? value : ''
    },
  })
}
</script>

<template>
  <div
    class=":uno: gap-2"
    :class="
      compact
        ? ':uno: grid grid-cols-1 sm:grid-cols-2'
        : ':uno: flex flex-col sm:flex-row sm:flex-wrap sm:items-center'
    "
  >
    <div
      class=":uno: min-w-0"
      :class="compact ? ':uno: sm:col-span-2' : ':uno: w-full sm:w-64 sm:flex-none'"
    >
      <FormKit
        :id="`${id}-search`"
        v-model="keywordInput"
        type="text"
        name="keyword"
        label="搜索工具"
        label-class="sr-only"
        aria-label="搜索工具"
        ignore
        :placeholder="searchPlaceholder"
        :disabled="disabled"
        outer-class="!p-0 !w-full"
      />
    </div>
    <div
      v-if="showPluginFilter"
      class=":uno: min-w-0"
      :class="{ ':uno: w-full sm:w-48 sm:flex-none': !compact }"
    >
      <FormKit
        :id="`${id}-source`"
        v-model="pluginNameInput"
        type="select"
        name="pluginName"
        label="工具来源"
        label-class="sr-only"
        aria-label="工具来源"
        :options="sourceOptions"
        :auto-select="false"
        ignore
        :disabled="disabled"
        outer-class="!p-0 !w-full"
      />
    </div>
    <div
      v-if="showCategoryFilter"
      class=":uno: min-w-0"
      :class="{ ':uno: w-full sm:w-36 sm:flex-none': !compact }"
    >
      <FormKit
        :id="`${id}-category`"
        v-model="categoryInput"
        type="select"
        name="category"
        label="工具分类"
        label-class="sr-only"
        aria-label="工具分类"
        :options="categoryOptions"
        :auto-select="false"
        ignore
        :disabled="disabled"
        outer-class="!p-0 !w-full"
      />
    </div>
  </div>
</template>
