<script setup lang="ts">
import type { ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import type { RequiredModelCapabilitiesValue } from '@/utils/capabilities'
import { groupModelOptionsByProvider } from '@/utils/model-options'
import { VLoading } from '@halo-dev/components'
import { useFuse } from '@vueuse/integrations/useFuse'
import {
  SelectContent,
  SelectGroup,
  SelectIcon,
  SelectLabel,
  SelectPortal,
  SelectRoot,
  SelectScrollDownButton,
  SelectScrollUpButton,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'
import { computed, nextTick, shallowRef, useId, useTemplateRef, watch } from 'vue'
import MingcuteCloseLine from '~icons/mingcute/close-line'
import MingcuteDownLine from '~icons/mingcute/down-line'
import MingcuteSearchLine from '~icons/mingcute/search-line'
import RiBrainLine from '~icons/ri/brain-line'
import {
  isModelOptionSelectable,
  normalizeRequiredFeatures,
  selectedModelDisplayName,
} from './ai-model-selector'
import AiModelSelectOption from './AiModelSelectOption.vue'

const props = withDefaults(
  defineProps<{
    name?: string
    label?: string
    help?: string
    modelType?: string
    providerName?: string
    providerType?: string
    enabled?: boolean
    available?: boolean | null
    requiredFeatures?: string | string[]
    requiredCapabilities?: RequiredModelCapabilitiesValue
    modelValue?: string
    placeholder?: string
    searchPlaceholder?: string
    clearable?: boolean
    disabled?: boolean
    fullWidth?: boolean
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
    requiredCapabilities: undefined,
    modelValue: undefined,
    placeholder: '请选择模型',
    searchPlaceholder: '搜索...',
    clearable: true,
    disabled: false,
    fullWidth: false,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | undefined): void
}>()

const triggerId = useId()
const helpId = `${triggerId}-help`
const searchInputRef = useTemplateRef<HTMLInputElement>('searchInput')
const keyword = shallowRef('')
const isOpen = shallowRef(false)
const selectedModelSnapshot = shallowRef<ModelOption>()

const modelType = computed(() => props.modelType)
const providerName = computed(() => props.providerName)
const providerType = computed(() => props.providerType)
const enabled = computed(() => props.enabled)
const available = computed(() => (props.available === null ? undefined : props.available))
const requiredFeatures = computed(() => normalizeRequiredFeatures(props.requiredFeatures))
const requiredCapabilities = computed(() => props.requiredCapabilities)

const { data: modelOptions, isLoading } = useModelOptionsFetch({
  modelType,
  providerName,
  providerType,
  enabled,
  available,
  requiredFeatures,
  requiredCapabilities,
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
  return groupModelOptionsByProvider(fuseResults.value.map((result) => result.item))
})
const selectedValue = computed(() => props.modelValue || undefined)
const selectedModel = computed(() => {
  return modelOptions.value?.find((model) => model.name === selectedValue.value)
})
const selectedModelDetails = computed(() => selectedModel.value || selectedModelSnapshot.value)
const selectedDisplayName = computed(() => {
  return selectedModelDisplayName(
    selectedModel.value,
    selectedModelSnapshot.value,
    selectedValue.value || '',
  )
})
const selectableModels = computed(() => {
  return groups.value.flatMap((group) => {
    return group.models.filter((model) => model.name && isModelOptionSelectable(model))
  })
})
const hasModels = computed(() => groups.value.some((group) => group.models.length > 0))
const emptyText = computed(() => {
  if (keyword.value) {
    return '未找到匹配模型'
  }
  if (requiredFeatures.value?.length || requiredCapabilities.value) {
    return '没有满足能力要求的模型'
  }
  return '暂无匹配模型'
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

watch(selectedValue, (value) => {
  if (!value) {
    selectedModelSnapshot.value = undefined
  }
})

watch(isOpen, (open) => {
  if (!open) {
    keyword.value = ''
  }
})

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      isOpen.value = false
    }
  },
)

function handleOpenChange(open: boolean) {
  isOpen.value = open
  if (!open) {
    return
  }

  nextTick(() => {
    window.requestAnimationFrame(() => {
      if (isOpen.value) {
        searchInputRef.value?.focus({ preventScroll: true })
      }
    })
  })
}

function handleValueChange(value: unknown) {
  if (typeof value === 'string') {
    emit('update:modelValue', value || undefined)
  }
}

function rememberSelection(model: ModelOption) {
  if (!model.name || !isModelOptionSelectable(model)) {
    return
  }

  selectedModelSnapshot.value = model
  keyword.value = ''
}

function clearSelection() {
  if (props.disabled) {
    return
  }

  selectedModelSnapshot.value = undefined
  emit('update:modelValue', undefined)
}

async function focusModelFromSearch(delta: -1 | 1) {
  const models = selectableModels.value
  if (!models.length) {
    return
  }

  const selectedIndex = models.findIndex((model) => model.name === selectedValue.value)
  const nextIndex =
    selectedIndex < 0
      ? delta === 1
        ? 0
        : models.length - 1
      : (selectedIndex + delta + models.length) % models.length

  await nextTick()
  const optionElements = searchInputRef.value
    ?.closest('[role="listbox"]')
    ?.querySelectorAll<HTMLElement>('[data-ai-model-selectable]')
  optionElements?.[nextIndex]?.focus({ preventScroll: true })
}

function selectCurrentSearchResult() {
  const model =
    selectableModels.value.find((item) => item.name === selectedValue.value) ||
    selectableModels.value[0]
  if (!model?.name) {
    return
  }

  rememberSelection(model)
  emit('update:modelValue', model.name)
  isOpen.value = false
}

function handleSearchKeydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      event.stopPropagation()
      focusModelFromSearch(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      event.stopPropagation()
      focusModelFromSearch(-1)
      break
    case 'Enter':
      event.preventDefault()
      event.stopPropagation()
      selectCurrentSearchResult()
      break
    case 'Escape':
      event.preventDefault()
      event.stopPropagation()
      isOpen.value = false
      break
    case 'Tab':
      break
    default:
      event.stopPropagation()
  }
}
</script>

<template>
  <div
    class=":uno: py-4 text-sm transition-all formkit-disabled:pointer-events-none formkit-disabled:cursor-not-allowed first:pt-0 last:pb-0 formkit-disabled:opacity-70"
    :data-disabled="props.disabled || undefined"
  >
    <label
      v-if="props.label"
      :for="triggerId"
      class=":uno: mb-1.5 block text-sm text-gray-700 font-medium"
    >
      {{ props.label }}
    </label>

    <SelectRoot
      :model-value="selectedValue"
      :open="isOpen"
      :name="props.name"
      :disabled="props.disabled"
      @update:model-value="handleValueChange"
      @update:open="handleOpenChange"
    >
      <div class=":uno: relative" :class="props.fullWidth ? ':uno: w-full' : ':uno: sm:max-w-lg'">
        <SelectTrigger
          :id="triggerId"
          :aria-describedby="props.help ? helpId : undefined"
          class=":uno: group h-9 w-full flex cursor-pointer items-center border border-gray-200 rounded-md bg-white px-3 text-left text-[14px] transition-colors disabled:cursor-not-allowed focus:border-gray-400 hover:border-gray-400 disabled:bg-gray-50 focus:outline-none"
          :class="props.clearable && selectedValue && !props.disabled ? ':uno: pr-8' : ':uno: pr-3'"
        >
          <SelectValue class=":uno: min-w-0 flex flex-1 items-center">
            <img
              v-if="selectedValue && selectedModelDetails?.provider?.iconUrl"
              :src="selectedModelDetails.provider.iconUrl"
              class=":uno: mr-1.5 size-4 flex-none rounded-sm object-contain"
              alt=""
            />
            <RiBrainLine
              v-else-if="selectedValue"
              class=":uno: mr-1.5 size-4 flex-none text-gray-400"
              aria-hidden="true"
            />
            <span
              class=":uno: min-w-0 flex-1 truncate"
              :class="selectedValue ? ':uno: text-gray-900' : ':uno: text-gray-500'"
            >
              {{ selectedValue ? selectedDisplayName : props.placeholder }}
            </span>
          </SelectValue>

          <SelectIcon class=":uno: ml-1 flex-none">
            <MingcuteDownLine
              class=":uno: size-4 text-gray-500 transition-transform duration-200 group-data-[state=open]:rotate-180"
              aria-hidden="true"
            />
          </SelectIcon>
        </SelectTrigger>

        <button
          v-if="props.clearable && selectedValue && !props.disabled"
          type="button"
          aria-label="清除"
          class=":uno: absolute right-2 top-1/2 z-10 size-5 flex items-center justify-center rounded text-gray-500 -translate-y-1/2 hover:bg-gray-100 hover:text-gray-700"
          @pointerdown.stop.prevent
          @click.stop="clearSelection"
        >
          <MingcuteCloseLine class=":uno: size-3.5" />
        </button>
      </div>

      <SelectPortal>
        <SelectContent
          position="popper"
          align="start"
          :side-offset="4"
          :collision-padding="8"
          :body-lock="false"
          :disable-outside-pointer-events="false"
          class=":uno: z-[9999] overflow-hidden border border-gray-200 rounded-md bg-white shadow-md"
          :style="{
            width: 'min(var(--reka-select-trigger-width), 32rem)',
            maxHeight: 'min(26rem, var(--reka-select-content-available-height))',
          }"
        >
          <div class=":uno: border-b border-gray-100 p-1">
            <div
              class=":uno: h-8 flex items-center gap-1.5 border border-gray-200 rounded bg-gray-50 px-2"
            >
              <MingcuteSearchLine class=":uno: size-4 flex-none text-gray-500" aria-hidden="true" />
              <input
                ref="searchInput"
                v-model="keyword"
                type="text"
                autocomplete="off"
                :placeholder="props.searchPlaceholder"
                :disabled="props.disabled"
                class=":uno: h-full min-w-0 flex-1 border-none text-base text-gray-800 outline-none !bg-transparent !p-0 placeholder:text-sm placeholder:text-gray-500"
                @keydown="handleSearchKeydown"
              />
              <button
                v-if="keyword"
                type="button"
                class=":uno: size-5 flex flex-none items-center justify-center rounded text-gray-500 hover:bg-gray-100 hover:text-gray-700"
                aria-label="清空"
                @click="keyword = ''"
              >
                <MingcuteCloseLine class=":uno: size-3.5" />
              </button>
            </div>
          </div>

          <VLoading v-if="isLoading" />

          <div v-else-if="!hasModels" class=":uno: px-3 py-5 text-center text-[13px] text-gray-500">
            {{ emptyText }}
          </div>

          <template v-else>
            <SelectScrollUpButton
              class=":uno: h-5 flex cursor-default items-center justify-center border-b border-gray-100 bg-gray-50/80 text-gray-500"
            >
              <MingcuteDownLine class=":uno: size-4 rotate-180" aria-hidden="true" />
            </SelectScrollUpButton>

            <SelectViewport class=":uno: max-h-80 pb-1">
              <SelectGroup v-for="group in groups" :key="group.key">
                <SelectLabel
                  class=":uno: sticky top-0 z-10 flex select-none items-center gap-2 border-b border-gray-100 bg-gray-50/95 px-3 py-2"
                >
                  <img
                    v-if="group.models[0]?.provider?.iconUrl"
                    :src="group.models[0].provider.iconUrl"
                    class=":uno: size-4 flex-none rounded-sm object-contain"
                    alt=""
                  />
                  <RiBrainLine
                    v-else
                    class=":uno: size-4 flex-none text-gray-400"
                    aria-hidden="true"
                  />
                  <span class=":uno: text-xs text-gray-700 font-medium">
                    {{ group.label }}
                  </span>
                </SelectLabel>

                <div class=":uno: py-1.5">
                  <AiModelSelectOption
                    v-for="model in group.models"
                    :key="model.name"
                    :model="model"
                    :selected="model.name === selectedValue"
                    @select="rememberSelection"
                  />
                </div>
              </SelectGroup>
            </SelectViewport>

            <SelectScrollDownButton
              class=":uno: h-5 flex cursor-default items-center justify-center border-t border-gray-100 bg-gray-50/80 text-gray-500"
            >
              <MingcuteDownLine class=":uno: size-4" aria-hidden="true" />
            </SelectScrollDownButton>
          </template>
        </SelectContent>
      </SelectPortal>
    </SelectRoot>

    <p v-if="props.help" :id="helpId" class=":uno: mt-2 text-xs text-gray-500">
      {{ props.help }}
    </p>
  </div>
</template>
