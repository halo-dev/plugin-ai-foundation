<script setup lang="ts">
import type {
  AiModel,
  AiModelSpecFeaturesEnum,
  ImageGenerationCapability,
  LanguageCapability,
  ModelCapabilitySources,
  ModelParameterMappings,
} from '@/api/generated'
import {
  AiModelSpecModelTypeEnum,
  LanguageCapabilityInputSourcesEnum,
  ModelCapabilitySourcesImageGenerationEnum,
  ModelCapabilitySourcesLanguageEnum,
  AiModelSpecFeaturesEnum as ModelFeature,
} from '@/api/generated'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ModelFormState } from '@/types/form'
import { booleanCapabilityFormValue, booleanCapabilityValue } from '@/utils/boolean-capability-form'
import {
  capabilityDomainSource,
  capabilitySourceLabel,
  hasCapabilityDomain,
} from '@/utils/capabilities'
import {
  defaultModelTypeForProviderType,
  modelFeatureOptionsForProviderType,
  modelTypeOptionsForProviderType,
} from '@/utils/model'
import {
  mappingsForModelType,
  validateReasoningMappings,
  type MappingModelType,
} from '@/utils/parameter-mappings'
import type { FormKitTypeDefinition } from '@formkit/core'
import { submitForm } from '@formkit/core'
import { Toast } from '@halo-dev/components'
import { computed, ref, watch } from 'vue'
import AdvancedSettingsCollapsible from './AdvancedSettingsCollapsible.vue'
import ParameterMappingFields from './ParameterMappingFields.vue'

const props = defineProps<{
  formState?: ModelFormState
  providerType: string
  modelName?: string
  inheritedMappings?: ModelParameterMappings
}>()

const emit = defineEmits<{
  (e: 'submit', data: ModelFormState): void
}>()

const isEditing = computed(() => !!props.formState)

const { data: providerTypes } = useProviderTypesFetch()

const selectedProviderType = computed(() => {
  return providerTypes.value?.find((type) => type.providerType === props.providerType)
})

const modelTypeOptions = computed(() => {
  return modelTypeOptionsForProviderType(selectedProviderType.value).map((item) => ({
    value: item.value,
    label: item.label,
  }))
})

const featureOptions = computed(() => {
  return modelFeatureOptionsForProviderType(selectedProviderType.value).map((item) => ({
    value: item.value,
    label: item.label,
  }))
})

const defaultModelType = computed(() => {
  return defaultModelTypeForProviderType(selectedProviderType.value, props.formState?.modelType)
})

const selectedModelType = ref<AiModelSpecModelTypeEnum>(
  props.formState?.modelType || defaultModelType.value,
)
const parameterMappings = ref(props.formState?.parameterMappings)

const booleanCapabilityOptions = [
  { label: '未知', value: '' },
  { label: '支持', value: 'true' },
  { label: '不支持', value: 'false' },
]

const reasoningHistoryOptions = [
  { label: '继承供应商', value: '' },
  { label: '支持', value: 'true' },
  { label: '不支持', value: 'false' },
]

const inputSourceOptions = [
  { label: 'Base64 / Data', value: LanguageCapabilityInputSourcesEnum.Data },
  { label: 'URL', value: LanguageCapabilityInputSourcesEnum.Url },
]

const selectedFeatures = ref<string[]>(
  props.formState?.features ? [...props.formState.features] : [],
)
const languageFileInputValue = ref(
  booleanCapabilityFormValue(props.formState?.capabilities?.language?.fileInput),
)

const hasImageRecognitionFeature = computed(() =>
  selectedFeatures.value.includes(ModelFeature.Vision),
)
const hasAudioRecognitionFeature = computed(() =>
  selectedFeatures.value.includes(ModelFeature.AudioInput),
)

const shouldShowLanguageMediaDetails = computed(
  () =>
    hasImageRecognitionFeature.value ||
    hasAudioRecognitionFeature.value ||
    languageFileInputValue.value === 'true',
)
const languageInputMediaTypesPlaceholder = computed(() => {
  if (hasImageRecognitionFeature.value && hasAudioRecognitionFeature.value) {
    return 'image/*\naudio/*'
  }
  if (hasAudioRecognitionFeature.value) {
    return 'audio/*'
  }
  if (hasImageRecognitionFeature.value) {
    return 'image/*'
  }
  return 'application/pdf'
})

const checkboxGroupClasses = {
  fieldset: ':uno: border-0 p-0 m-0',
  legend: ':uno: mb-2 text-sm text-gray-700 font-medium',
  options: ':uno: flex flex-wrap gap-2',
  option: ':uno: m-0',
  wrapper:
    ':uno: inline-flex min-h-9 items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-700 transition-colors hover:border-gray-300',
  input: ':uno: h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-2 focus:ring-blue-500/20',
  label: ':uno: m-0 text-sm text-gray-700',
  help: ':uno: mt-1 text-xs text-gray-500',
}

const isLanguageCapabilityVisible = computed(
  () => selectedModelType.value === AiModelSpecModelTypeEnum.Language,
)
const isImageGenerationCapabilityVisible = computed(
  () => selectedModelType.value === AiModelSpecModelTypeEnum.ImageGeneration,
)

const languageSourceLabel = computed(() =>
  capabilitySourceLabel(
    capabilityDomainSource(
      props.formState?.capabilities,
      props.formState?.capabilitySources,
      'language',
    ),
  ),
)
const imageGenerationSourceLabel = computed(() =>
  capabilitySourceLabel(
    capabilityDomainSource(
      props.formState?.capabilities,
      props.formState?.capabilitySources,
      'imageGeneration',
    ),
  ),
)
const visibleCapabilitySourceLabel = computed(() =>
  isLanguageCapabilityVisible.value ? languageSourceLabel.value : imageGenerationSourceLabel.value,
)

watch(
  defaultModelType,
  (value) => {
    if (!props.formState?.modelType) {
      selectedModelType.value = value
    }
  },
  { immediate: true },
)

watch(
  () => props.formState?.modelType,
  (value) => {
    selectedModelType.value = value || defaultModelType.value
  },
)

watch(
  () => props.formState?.features,
  (value) => {
    selectedFeatures.value = value ? [...value] : []
  },
)

watch(
  () => props.formState?.capabilities?.language?.fileInput,
  (value) => {
    languageFileInputValue.value = booleanCapabilityFormValue(value)
  },
)

function onSubmit(data: ModelFormRawState) {
  const normalizedMappings = mappingsForModelType(
    parameterMappings.value,
    data.modelType as MappingModelType,
  )
  const mappingErrors = validateReasoningMappings(normalizedMappings)
  if (mappingErrors.length) {
    Toast.error(mappingErrors[0])
    return
  }
  const capabilities = buildCapabilities(data)
  const capabilitySources = buildCapabilitySources(capabilities)
  if (capabilities && capabilitySources) {
    capabilities.sources = capabilitySources
  }
  emit('submit', {
    modelId: data.modelId,
    displayName: data.displayName,
    enabled: data.enabled,
    modelType: data.modelType,
    features: data.features?.length ? data.features : undefined,
    adapterType: data.adapterType,
    capabilities,
    capabilitySources,
    parameterMappings: normalizedMappings,
  })
}

defineExpose({
  submit: () => submitForm('model-form'),
})

interface ModelFormRawState extends ModelFormState {
  languageFileInput?: boolean | string
  languageReasoningHistory?: boolean | string
  languageInputMediaTypes?: string
  languageInputSources?: string | string[]
  imageGenerationTextToImage?: boolean | string
  imageGenerationImageToImage?: boolean | string
  imageGenerationMaskInput?: boolean | string
  imageGenerationMaxImagesPerCall?: number | string
  imageGenerationSizes?: string
  imageGenerationAspectRatios?: string
  imageGenerationOutputMediaTypes?: string
}

function buildCapabilities(data: ModelFormRawState): AiModel['spec']['capabilities'] {
  const language =
    data.modelType === AiModelSpecModelTypeEnum.Language ? buildLanguageCapability(data) : undefined
  const imageGeneration =
    data.modelType === AiModelSpecModelTypeEnum.ImageGeneration
      ? buildImageGenerationCapability(data)
      : undefined
  if (!language && !imageGeneration) {
    return undefined
  }
  return {
    language,
    imageGeneration,
  }
}

function buildLanguageCapability(data: ModelFormRawState): LanguageCapability | undefined {
  const fileInput = booleanCapabilityValue(data.languageFileInput)
  const shouldKeepMediaDetails =
    hasFeature(data.features, ModelFeature.Vision) ||
    hasFeature(data.features, ModelFeature.AudioInput) ||
    fileInput === true
  const domain: LanguageCapability = {
    fileInput,
    reasoningHistory: booleanCapabilityValue(data.languageReasoningHistory),
    inputMediaTypes: shouldKeepMediaDetails ? listValue(data.languageInputMediaTypes) : undefined,
    inputSources: shouldKeepMediaDetails
      ? (listValue(data.languageInputSources) as LanguageCapability['inputSources'])
      : undefined,
  }
  return hasCapabilityDomain(domain) ? domain : undefined
}

function buildImageGenerationCapability(
  data: ModelFormRawState,
): ImageGenerationCapability | undefined {
  const domain: ImageGenerationCapability = {
    textToImage: booleanCapabilityValue(data.imageGenerationTextToImage),
    imageToImage: booleanCapabilityValue(data.imageGenerationImageToImage),
    maskInput: booleanCapabilityValue(data.imageGenerationMaskInput),
    maxImagesPerCall: positiveIntegerValue(data.imageGenerationMaxImagesPerCall),
    sizes: listValue(data.imageGenerationSizes),
    aspectRatios: listValue(data.imageGenerationAspectRatios),
    outputMediaTypes: listValue(data.imageGenerationOutputMediaTypes),
  }
  return hasCapabilityDomain(domain) ? domain : undefined
}

function buildCapabilitySources(
  capabilities?: AiModel['spec']['capabilities'],
): ModelCapabilitySources | undefined {
  if (!capabilities) {
    return undefined
  }
  const sources: ModelCapabilitySources = {
    language: sourceFor('language', capabilities.language),
    imageGeneration: sourceFor('imageGeneration', capabilities.imageGeneration),
  }
  return sources.language || sources.imageGeneration ? sources : undefined
}

function sourceFor(
  domain: keyof ModelCapabilitySources,
  nextDomain?: LanguageCapability | ImageGenerationCapability,
) {
  if (!nextDomain) {
    return undefined
  }
  const previousDomain =
    props.formState?.capabilities?.[domain as keyof NonNullable<AiModel['spec']['capabilities']>]
  const previousSource =
    props.formState?.capabilitySources?.[domain] || props.formState?.capabilities?.sources?.[domain]
  if (sameJson(previousDomain, nextDomain)) {
    return previousSource
  }
  return domain === 'language'
    ? ModelCapabilitySourcesLanguageEnum.Manual
    : ModelCapabilitySourcesImageGenerationEnum.Manual
}

function listValue(value: unknown) {
  const values = Array.isArray(value)
    ? value
    : typeof value === 'string'
      ? value.split(/[\n,]/)
      : []
  const normalized = values.map((item) => String(item).trim()).filter(Boolean)
  return normalized.length ? normalized : undefined
}

function positiveIntegerValue(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : undefined
}

function listFormValue(values?: string[]) {
  return values?.join('\n') || ''
}

function onFeaturesInput(value: unknown) {
  selectedFeatures.value = normalizeFeatureValues(value)
}

function onLanguageFileInput(value: unknown) {
  languageFileInputValue.value = booleanCapabilityFormValue(booleanCapabilityValue(value))
}

function hasFeature(features: ModelFormRawState['features'], feature: AiModelSpecFeaturesEnum) {
  return normalizeFeatureValues(features).includes(feature)
}

function normalizeFeatureValues(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => String(item))
}

function sameJson(a: unknown, b: unknown) {
  return JSON.stringify(a || undefined) === JSON.stringify(b || undefined)
}
</script>

<template>
  <FormKit id="model-form" type="form" @submit="onSubmit">
    <FormKit
      v-if="isEditing"
      type="text"
      name="modelName"
      label="内部模型 ID"
      help="Halo 内用于识别此模型的 ID，调用 AI Foundation SDK 时传递此值。"
      disabled
      :value="modelName"
    />

    <FormKit
      type="text"
      name="modelId"
      label="供应商模型 ID"
      help="调用第三方供应商时传递的模型 ID，例如 OpenAI 的 gpt-4o。"
      validation="required"
      placeholder="例如: gpt-4o"
      :disabled="isEditing"
      :value="formState?.modelId"
    />

    <FormKit
      type="text"
      name="displayName"
      label="显示名称"
      validation="required"
      placeholder="例如: GPT-4o"
      :value="formState?.displayName"
    />

    <FormKit
      type="select"
      name="modelType"
      label="模型类型"
      validation="required"
      :options="modelTypeOptions"
      :value="selectedModelType"
      @input="selectedModelType = $event as AiModelSpecModelTypeEnum"
    />

    <FormKit
      :type="'switch' as unknown as FormKitTypeDefinition<boolean>"
      name="enabled"
      label="启用"
      :value="formState?.enabled ?? true"
    />

    <AdvancedSettingsCollapsible title="参数映射" source-label="模型覆盖">
      <p class=":uno: mb-3 text-xs text-gray-500">
        仅在这个模型需要例外处理时覆盖。未配置的参数继续继承 Provider 设置。
      </p>
      <ParameterMappingFields
        v-model="parameterMappings"
        context="model"
        :model-type="selectedModelType as MappingModelType"
        :adapter-type="formState?.adapterType"
        :templates="selectedProviderType?.parameterMappingTemplates"
        :defaults="selectedProviderType?.defaultParameterMappings"
        :inherited-mappings="inheritedMappings"
      />
    </AdvancedSettingsCollapsible>

    <AdvancedSettingsCollapsible
      v-if="isLanguageCapabilityVisible || isImageGenerationCapabilityVisible"
      title="模型能力"
      :source-label="visibleCapabilitySourceLabel"
    >
      <template v-if="isLanguageCapabilityVisible">
        <FormKit
          type="checkbox"
          name="features"
          label="模型能力"
          :options="featureOptions"
          :value="selectedFeatures"
          @input="onFeaturesInput"
        />
        <FormKit
          type="select"
          name="languageReasoningHistory"
          label="支持推理历史回传"
          help="继承供应商时使用供应商默认能力；仅在单个模型需要覆盖时明确选择支持或不支持。"
          :options="reasoningHistoryOptions"
          :value="booleanCapabilityFormValue(formState?.capabilities?.language?.reasoningHistory)"
        />
        <FormKit
          type="select"
          name="languageFileInput"
          label="支持文件/音频识别"
          :options="booleanCapabilityOptions"
          :value="languageFileInputValue"
          @input="onLanguageFileInput"
        />
        <FormKit
          v-if="shouldShowLanguageMediaDetails"
          type="checkbox"
          name="languageInputSources"
          label="支持输入来源"
          help="Data 表示 base64 或 data URL；URL 表示供应方原生支持远程文件地址。"
          :options="inputSourceOptions"
          :value="formState?.capabilities?.language?.inputSources"
          :classes="checkboxGroupClasses"
        />
        <FormKit
          v-if="shouldShowLanguageMediaDetails"
          type="textarea"
          name="languageInputMediaTypes"
          label="支持媒体类型"
          :placeholder="languageInputMediaTypesPlaceholder"
          help="每行或用逗号填写一个 MIME 类型，例如 image/*、audio/*、application/pdf。"
          :value="listFormValue(formState?.capabilities?.language?.inputMediaTypes)"
        />
      </template>

      <template v-if="isImageGenerationCapabilityVisible">
        <div class=":uno: space-y-3">
          <FormKit
            type="select"
            name="imageGenerationTextToImage"
            label="文生图"
            :options="booleanCapabilityOptions"
            :value="
              booleanCapabilityFormValue(formState?.capabilities?.imageGeneration?.textToImage)
            "
          />
          <FormKit
            type="select"
            name="imageGenerationImageToImage"
            label="图生图"
            :options="booleanCapabilityOptions"
            :value="
              booleanCapabilityFormValue(formState?.capabilities?.imageGeneration?.imageToImage)
            "
          />
        </div>
        <FormKit
          type="select"
          name="imageGenerationMaskInput"
          label="蒙版输入"
          :options="booleanCapabilityOptions"
          :value="booleanCapabilityFormValue(formState?.capabilities?.imageGeneration?.maskInput)"
        />
        <FormKit
          type="number"
          name="imageGenerationMaxImagesPerCall"
          label="单次最大图片数"
          min="1"
          :value="formState?.capabilities?.imageGeneration?.maxImagesPerCall"
        />
        <FormKit
          type="textarea"
          name="imageGenerationSizes"
          label="支持尺寸"
          placeholder="1024x1024"
          :value="listFormValue(formState?.capabilities?.imageGeneration?.sizes)"
        />
        <FormKit
          type="textarea"
          name="imageGenerationAspectRatios"
          label="支持宽高比"
          placeholder="1:1"
          :value="listFormValue(formState?.capabilities?.imageGeneration?.aspectRatios)"
        />
        <FormKit
          type="textarea"
          name="imageGenerationOutputMediaTypes"
          label="支持输出媒体类型"
          placeholder="image/png"
          :value="listFormValue(formState?.capabilities?.imageGeneration?.outputMediaTypes)"
        />
      </template>
    </AdvancedSettingsCollapsible>
  </FormKit>
</template>
