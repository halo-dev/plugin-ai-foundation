<script lang="ts" setup>
import type { ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { groupModelOptionsByProvider } from '@/utils/model-options'

import { VLoading } from '@halo-dev/components'
import { onClickOutside } from '@vueuse/core'
import { useFuse } from '@vueuse/integrations/useFuse'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import RiArrowDownSLine from '~icons/ri/arrow-down-s-line'
import RiBrainLine from '~icons/ri/brain-line'
import RiCheckLine from '~icons/ri/check-line'
import RiCloseLine from '~icons/ri/close-line'
import RiSearchLine from '~icons/ri/search-line'
import {
  isModelOptionSelectable,
  modelFeatureLabel,
  modelOptionUnavailableReasonLabel,
  modelTypeLabel,
  normalizeRequiredFeatures,
} from './ai-model-selector'
const props = withDefaults(
  defineProps<{
    name?: string
    label?: string
    help?: string
    modelType?: string
    providerName?: string
    providerType?: string
    enabled?: boolean
    available?: boolean
    requiredFeatures?: string | string[]
    modelValue?: string
    placeholder?: string
    searchPlaceholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    name: undefined,
    label: undefined,
    help: undefined,
    modelType: undefined,
    providerName: undefined,
    providerType: undefined,
    enabled: undefined,
    available: true,
    requiredFeatures: undefined,
    modelValue: undefined,
    placeholder: '请选择模型',
    searchPlaceholder: '搜索...',
    clearable: true,
    disabled: false,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | undefined): void
}>()

const keyword = ref('')
const selectedValue = ref('')
const rootRef = ref<HTMLElement>()
const searchInputRef = ref<HTMLInputElement>()
const isOpen = ref(false)
const activeModelName = ref<string>()
const selectedModelSnapshot = ref<ModelOption>()

const effectiveLabel = computed(() => props.label)
const effectiveHelp = computed(() => props.help)
const effectiveDisabled = computed(() => props.disabled ?? false)
const effectivePlaceholder = computed(() => props.placeholder)
const effectiveSearchPlaceholder = computed(() => props.searchPlaceholder)
const effectiveClearable = computed(() => props.clearable)

const modelType = computed(() => props.modelType)
const providerName = computed(() => props.providerName)
const providerType = computed(() => props.providerType)
const enabled = computed(() => props.enabled)
const available = computed(() => props.available)
const requiredFeatures = computed(() => normalizeRequiredFeatures(props.requiredFeatures))

const { data: modelOptions, isLoading } = useModelOptionsFetch({
  modelType,
  providerName,
  providerType,
  enabled,
  available,
  requiredFeatures,
})

const { results: fuseResults } = useFuse(
  keyword,
  computed(() => modelOptions.value ?? []),
  {
    fuseOptions: {
      keys: ['displayName', 'modelId', 'name', 'provider.displayName', 'provider.name'],
      threshold: 0.2,
      shouldSort: true,
    },
    matchAllWhenSearchEmpty: true,
  },
)

const groups = computed(() => {
  return groupModelOptionsByProvider(fuseResults.value.map((r) => r.item))
})

const selectedModel = computed(() => {
  return modelOptions.value?.find((model) => model.name === selectedValue.value)
})
const selectableModels = computed(() => {
  return groups.value.flatMap((group) => {
    return group.models.filter((model) => model.name && isModelOptionSelectable(model))
  })
})

const hasModels = computed(() => groups.value.some((group) => group.models.length > 0))
const selectedDisplayName = computed(() => {
  const model = selectedModel.value || selectedModelSnapshot.value
  return model?.displayName || model?.modelId || selectedValue.value
})

onClickOutside(rootRef, () => {
  isOpen.value = false
})

onMounted(async () => {
  await nextTick()
  if (typeof props.modelValue === 'string') {
    selectedValue.value = props.modelValue
  }
})

watch(
  () => props.modelValue,
  (value) => {
    if ((value || '') !== selectedValue.value) {
      selectedValue.value = value || ''
    }
  },
)

watch(selectedValue, (value) => {
  emit('update:modelValue', value || undefined)
  if (!value) {
    selectedModelSnapshot.value = undefined
  }
})

watch(
  selectedModel,
  (model) => {
    if (model) {
      selectedModelSnapshot.value = model
    }
  },
  { immediate: true },
)

watch([selectableModels, isOpen], () => {
  if (!isOpen.value) {
    return
  }

  if (
    activeModelName.value &&
    selectableModels.value.some((model) => model.name === activeModelName.value)
  ) {
    return
  }

  activeModelName.value =
    selectableModels.value.find((model) => model.name === selectedValue.value)?.name ||
    selectableModels.value[0]?.name
})

watch(
  () => effectiveDisabled.value,
  (disabled) => {
    if (disabled) {
      isOpen.value = false
    }
  },
)

async function toggleOpen() {
  if (effectiveDisabled.value) {
    return
  }

  if (isOpen.value) {
    isOpen.value = false
    return
  }

  await openDropdown()
}

function selectModel(model: ModelOption) {
  if (effectiveDisabled.value || !model.name || !isModelOptionSelectable(model)) {
    return
  }
  selectedValue.value = model.name
  selectedModelSnapshot.value = model
  keyword.value = ''
  isOpen.value = false
}

function clearSelection() {
  if (effectiveDisabled.value) {
    return
  }
  selectedValue.value = ''
}

async function openDropdown() {
  isOpen.value = true
  activeModelName.value =
    selectableModels.value.find((model) => model.name === selectedValue.value)?.name ||
    selectableModels.value[0]?.name
  await nextTick()
  searchInputRef.value?.focus()
}

async function moveActive(delta: number) {
  if (effectiveDisabled.value) {
    return
  }
  if (!isOpen.value) {
    await openDropdown()
    return
  }

  const items = selectableModels.value
  if (!items.length) {
    return
  }

  const currentIndex = items.findIndex((model) => model.name === activeModelName.value)
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + delta + items.length) % items.length
  activeModelName.value = items[nextIndex]?.name
  await scrollActiveOptionIntoView()
}

async function confirmActive() {
  if (effectiveDisabled.value) {
    return
  }
  if (!isOpen.value) {
    await openDropdown()
    return
  }

  const model = selectableModels.value.find((item) => item.name === activeModelName.value)
  if (model) {
    selectModel(model)
  }
}

function closeDropdown() {
  isOpen.value = false
}

async function scrollActiveOptionIntoView() {
  await nextTick()
  rootRef.value
    ?.querySelector('[data-ai-model-selector-active="true"]')
    ?.scrollIntoView({ block: 'nearest' })
}

function handleKeyboard(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      moveActive(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      moveActive(-1)
      break
    case 'Enter':
      event.preventDefault()
      confirmActive()
      break
    case ' ':
      if (event.target !== searchInputRef.value) {
        event.preventDefault()
        confirmActive()
      }
      break
    case 'Escape':
      event.preventDefault()
      closeDropdown()
      break
  }
}
</script>

<template>
  <div
    ref="rootRef"
    class=":uno: py-4 text-sm transition-all formkit-disabled:pointer-events-none formkit-disabled:cursor-not-allowed first:pt-0 last:pb-0 formkit-disabled:opacity-70"
    :data-disabled="effectiveDisabled || undefined"
  >
    <FormKit type="hidden" :name="name" :value="selectedValue || undefined" />

    <label v-if="effectiveLabel" class=":uno: mb-1.5 block text-sm text-gray-700 font-medium">
      {{ effectiveLabel }}
    </label>

    <div class=":uno: relative sm:max-w-lg">
      <button
        type="button"
        class=":uno: group relative h-9 w-full flex cursor-pointer items-center border border-gray-200 rounded-md bg-white px-3 text-left text-[14px] transition-colors hover:border-gray-400 focus:outline-none"
        :class="{
          ':uno: border-gray-200 bg-gray-50': effectiveDisabled,
        }"
        :disabled="effectiveDisabled"
        :aria-expanded="isOpen"
        aria-haspopup="listbox"
        @click="toggleOpen"
        @keydown="handleKeyboard"
      >
        <img
          v-if="selectedValue && (selectedModel || selectedModelSnapshot)?.provider?.iconUrl"
          :src="(selectedModel || selectedModelSnapshot)!.provider!.iconUrl!"
          class=":uno: mr-1.5 h-4 w-4 flex-none rounded-sm object-contain"
          alt=""
        />
        <RiBrainLine
          v-else-if="selectedValue"
          class=":uno: mr-1.5 h-4 w-4 flex-none text-gray-400"
          aria-hidden="true"
        />
        <span
          class=":uno: min-w-0 flex-1 truncate"
          :class="selectedValue ? ':uno: text-gray-900' : ':uno: text-gray-500'"
        >
          {{ selectedValue ? selectedDisplayName : effectivePlaceholder }}
        </span>

        <span
          v-if="effectiveClearable && selectedValue && !effectiveDisabled"
          role="button"
          aria-label="清除"
          class=":uno: ml-1 h-5 w-5 flex flex-none items-center justify-center rounded text-gray-500 -mr-1 hover:bg-gray-100 hover:text-gray-700"
          @click.stop="clearSelection"
        >
          <RiCloseLine class=":uno: h-3.5 w-3.5" />
        </span>

        <RiArrowDownSLine
          class=":uno: ml-1 h-4 w-4 flex-none text-gray-500 transition-transform duration-200"
          :class="{ ':uno: rotate-180': isOpen }"
          aria-hidden="true"
        />
      </button>

      <div
        v-if="isOpen"
        class=":uno: absolute z-50 mt-1 w-full overflow-hidden border border-gray-200 rounded-md bg-white shadow-md"
      >
        <div class=":uno: border-b border-gray-100 p-1">
          <div
            class=":uno: h-8 flex items-center gap-1.5 border border-gray-200 rounded bg-gray-50 px-2"
          >
            <RiSearchLine class=":uno: h-4 w-4 flex-none text-gray-500" aria-hidden="true" />
            <input
              ref="searchInputRef"
              v-model="keyword"
              type="text"
              autocomplete="off"
              :placeholder="effectiveSearchPlaceholder"
              :disabled="effectiveDisabled"
              class=":uno: h-full min-w-0 flex-1 border-none bg-transparent text-base text-gray-800 outline-none !p-0 placeholder:text-sm placeholder:text-gray-500"
              @keydown="handleKeyboard"
            />
            <button
              v-if="keyword"
              type="button"
              class=":uno: h-5 w-5 flex flex-none items-center justify-center rounded text-gray-500 hover:bg-gray-100 hover:text-gray-700"
              aria-label="清空"
              @click="keyword = ''"
            >
              <RiCloseLine class=":uno: h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <VLoading v-if="isLoading" />

        <div v-else-if="!hasModels" class=":uno: px-3 py-5 text-center text-[13px] text-gray-500">
          暂无匹配模型
        </div>

        <div v-else class=":uno: max-h-60 overflow-y-auto pb-1" role="listbox">
          <div
            v-for="group in groups"
            :key="group.key"
            class=":uno: mt-1.5 first:mt-0"
          >
            <div
              class=":uno: sticky top-0 z-10 flex select-none items-center gap-2 bg-gray-50 px-3 py-1.5"
            >
              <img
                v-if="group.models[0]?.provider?.iconUrl"
                :src="group.models[0].provider?.iconUrl"
                class=":uno: h-4 w-4 flex-none rounded-sm object-contain"
                alt=""
              />
              <RiBrainLine v-else class=":uno: h-4 w-4 flex-none text-gray-400" aria-hidden="true" />
              <span class=":uno: text-[11px] text-gray-500 font-semibold tracking-wide uppercase">
                {{ group.label }}
              </span>
            </div>

            <div
              v-for="model in group.models"
              :key="model.name"
              role="option"
              :aria-selected="model.name === selectedValue"
              :data-ai-model-selector-active="model.name === activeModelName ? 'true' : undefined"
              class=":uno: relative mx-1.5 flex cursor-pointer select-none items-center gap-1.5 rounded-lg pl-3 pr-2 py-2 text-[13px] leading-5 transition-colors"
              :class="[
                model.name === selectedValue
                  ? ':uno: bg-blue-50 font-medium text-blue-700'
                  : model.name === activeModelName
                    ? ':uno: bg-gray-100 text-gray-900'
                    : ':uno: text-gray-800 hover:bg-gray-100 hover:text-gray-900',
                effectiveDisabled || !isModelOptionSelectable(model)
                  ? ':uno: cursor-not-allowed opacity-50'
                  : '',
              ]"
              @mouseenter="activeModelName = model.name"
              @click="selectModel(model)"
            >
              <span class=":uno: min-w-0 flex-1">
                <span class=":uno: flex items-center gap-1.5">
                  <img
                    v-if="model.provider?.iconUrl"
                    :src="model.provider.iconUrl"
                    class=":uno: h-3.5 w-3.5 flex-none rounded-sm object-contain opacity-75"
                    alt=""
                  />
                  <RiBrainLine
                    v-else
                    class=":uno: h-3.5 w-3.5 flex-none opacity-55"
                    aria-hidden="true"
                  />

                  <span class=":uno: min-w-0 truncate leading-5">
                    {{ model.displayName || model.modelId || model.name }}
                  </span>
                  <span
                    v-if="
                      model.modelId &&
                      model.modelId !== model.displayName &&
                      model.modelId !== model.name
                    "
                    class=":uno: flex-none text-[11px] leading-4 opacity-50"
                  >
                    {{ model.modelId }}
                  </span>
                </span>
                <span
                  v-if="
                    model.modelType || model.features?.length || !isModelOptionSelectable(model)
                  "
                  class=":uno: mt-1 flex flex-wrap items-center gap-1"
                >
                  <span
                    v-if="modelTypeLabel(model.modelType)"
                    class=":uno: h-4 inline-flex items-center rounded bg-gray-100 px-1 text-[10px] text-gray-600 leading-4"
                  >
                    {{ modelTypeLabel(model.modelType) }}
                  </span>
                  <span
                    v-for="feature in model.features"
                    :key="feature"
                    class=":uno: h-4 inline-flex items-center rounded bg-gray-100 px-1 text-[10px] text-gray-600 leading-4"
                  >
                    {{ modelFeatureLabel(feature) }}
                  </span>
                  <span
                    v-if="!isModelOptionSelectable(model)"
                    class=":uno: text-[11px] text-red-600 leading-4"
                  >
                    {{ modelOptionUnavailableReasonLabel(model.unavailableReason) }}
                  </span>
                </span>
              </span>

              <RiCheckLine
                v-if="model.name === selectedValue"
                class=":uno: h-3.5 w-3.5 flex-none text-blue-600"
                aria-hidden="true"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <p v-if="effectiveHelp" class=":uno: mt-2 text-xs text-gray-500">{{ effectiveHelp }}</p>
  </div>
</template>
