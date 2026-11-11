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
  AiModelSpecAdapterTypeEnum,
  AiModelSpecModelTypeEnum,
  LanguageCapabilityInputSourcesEnum,
  ModelCapabilitySourcesImageGenerationEnum,
  ModelCapabilitySourcesLanguageEnum,
  AiModelSpecFeaturesEnum as ModelFeature,
} from '@/api/generated'
import { useModelCatalogParameters } from '@/composables/use-model-catalog-parameters'
import { useModelParameters } from '@/composables/use-model-parameters'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import type { ModelFormState } from '@/types/form'
import { booleanCapabilityFormValue, booleanCapabilityValue } from '@/utils/boolean-capability-form'
import {
  capabilityDomainSource,
  capabilitySourceLabel,
  hasCapabilityDomain,
} from '@/utils/capabilities'
import {
  adapterOptionsForProviderType,
  defaultAdapterForProviderType,
  defaultModelTypeForProviderType,
  defaultParameterMappingsForAdapter,
  modelFeatureOptionsForProviderType,
  modelSupportedFeaturesForProviderType,
  modelTypeOptionsForProviderType,
} from '@/utils/model'
import {
  featureConstraints,
  featuresWithConstraints,
  filterFeatureSupport,
  parameterFeatures,
  withFeatureSupport,
} from '@/utils/model-feature-support'
import {
  mappingsForModelType,
  validateReasoningMappings,
  type MappingModelType,
} from '@/utils/parameter-mappings'
import {
  formatProviderNativeOptions,
  parseProviderNativeOptions,
} from '@/utils/provider-native-options'
import type { FormKitTypeDefinition } from '@formkit/core'
import { submitForm } from '@formkit/core'
import { Toast } from '@halo-dev/components'
import { computed, ref, shallowRef, watch } from 'vue'
import AdvancedSettingsCollapsible from './AdvancedSettingsCollapsible.vue'
import ModelCatalogProvenance from './ModelCatalogProvenance.vue'
import ModelNativeParameterConstraintFields from './ModelNativeParameterConstraintFields.vue'
import ModelParameterConstraintFields from './ModelParameterConstraintFields.vue'
import ParameterMappingFields from './ParameterMappingFields.vue'

const props = defineProps<{
  formState?: ModelFormState
  providerType: string
  providerName?: string
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

const defaultModelType = computed(() => {
  return defaultModelTypeForProviderType(selectedProviderType.value, props.formState?.modelType)
})

const selectedModelType = ref<AiModelSpecModelTypeEnum>(
  props.formState?.modelType || defaultModelType.value,
)
const adapterOptions = computed(() =>
  adapterOptionsForProviderType(selectedProviderType.value, selectedModelType.value),
)
const hasMultipleAdapterOptions = computed(() => adapterOptions.value.length > 1)
const selectedAdapterType = shallowRef<AiModelSpecAdapterTypeEnum | undefined>(
  props.formState?.adapterType,
)
const defaultParameterMappings = computed(() =>
  defaultParameterMappingsForAdapter(selectedProviderType.value, selectedAdapterType.value),
)
const featureOptions = computed(() => {
  return modelFeatureOptionsForProviderType(
    selectedProviderType.value,
    selectedAdapterType.value,
  ).map((item) => ({
    value: item.value,
    label: item.label,
    attrs: {
      onChange: (event: Event) => onFeatureChange(item.value, event),
    },
  }))
})
const selectedAdapterDescription = computed(
  () =>
    adapterOptions.value.find((option) => option.value === selectedAdapterType.value)
      ?.description || '选择此模型调用供应商时使用的原生 API 协议。',
)
const parameterMappings = ref(props.formState?.parameterMappings)
const parameterConstraints = ref(props.formState?.parameterConstraints)
const modelId = shallowRef(props.formState?.modelId || '')
const isDecisionModel = computed(
  () => selectedModelType.value === AiModelSpecModelTypeEnum.Decision,
)
const modelDraft = computed<AiModel | undefined>(() =>
  props.providerName && !isDecisionModel.value
    ? {
        apiVersion: 'aifoundation.halo.run/v1alpha1',
        kind: 'AiModel',
        metadata: { name: props.modelName || '' },
        spec: {
          providerName: props.providerName,
          displayName: props.formState?.displayName || modelId.value,
          enabled: props.formState?.enabled ?? true,
          modelId: modelId.value,
          modelType: selectedModelType.value,
          adapterType: selectedAdapterType.value,
        },
      }
    : undefined,
)
const formIdentity = computed(() =>
  JSON.stringify([
    props.modelName,
    props.providerName,
    props.providerType,
    modelId.value,
    selectedModelType.value,
    selectedAdapterType.value,
  ]),
)
const parameterIdentityReady = computed(() =>
  Boolean(props.providerName && selectedProviderType.value),
)
const { data: catalogParameters, waitForCurrent: waitForCatalogParameters } =
  useModelCatalogParameters(
    modelDraft,
    computed(() => props.providerType),
    parameterIdentityReady,
    computed(() => props.formState?.catalogParameters),
  )
const parameterDraft = computed(() =>
  modelDraft.value
    ? {
        ...modelDraft.value,
        spec: { ...modelDraft.value.spec, catalogParameters: catalogParameters.value },
      }
    : undefined,
)
const {
  data: inheritedParameters,
  isError: inheritedParametersError,
  isFetching: inheritedParametersLoading,
} = useModelParameters(parameterDraft)
const effectiveDraft = computed(() =>
  parameterDraft.value && !validateReasoningMappings(parameterMappings.value).length
    ? {
        ...parameterDraft.value,
        spec: {
          ...parameterDraft.value.spec,
          parameterMappings: parameterMappings.value,
          parameterConstraints: parameterConstraints.value,
        },
      }
    : undefined,
)
const { data: effectiveParameters } = useModelParameters(effectiveDraft)

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

const featureBaseline = ref(props.formState?.features || [])
const selectedFeatures = ref<AiModelSpecFeaturesEnum[]>([])
const effectiveFeatureConstraints = computed(() =>
  selectedModelType.value === AiModelSpecModelTypeEnum.Language
    ? featureConstraints(
        catalogParameters.value,
        parameterConstraints.value,
        props.inheritedMappings,
        parameterMappings.value,
      )
    : undefined,
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
    props.formState?.catalogMatch,
    'language',
  ),
)
const imageGenerationSourceLabel = computed(() =>
  capabilitySourceLabel(
    capabilityDomainSource(
      props.formState?.capabilities,
      props.formState?.capabilitySources,
      'imageGeneration',
    ),
    props.formState?.catalogMatch,
    'imageGeneration',
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
  [adapterOptions, () => props.formState?.adapterType],
  ([, formAdapter]) => {
    if (!selectedProviderType.value) return
    selectedAdapterType.value = defaultAdapterForProviderType(
      selectedProviderType.value,
      selectedModelType.value,
      formAdapter || selectedAdapterType.value,
    )
  },
  { immediate: true },
)

watch(
  () => props.formState?.modelId,
  (value) => {
    modelId.value = value || ''
  },
)

watch(
  () => props.formState?.parameterMappings,
  (value) => {
    parameterMappings.value = value
  },
)

watch(
  () => props.formState?.parameterConstraints,
  (value) => {
    parameterConstraints.value = value
  },
)

watch(
  () => props.formState?.features,
  (features) => {
    featureBaseline.value = features || []
  },
)

const supportedFeatures = computed(() =>
  modelSupportedFeaturesForProviderType(selectedProviderType.value, selectedAdapterType.value),
)

watch(
  [featureBaseline, effectiveFeatureConstraints, supportedFeatures],
  ([features, constraints, supported]) => {
    selectedFeatures.value = featuresWithConstraints(features, constraints, supported)
  },
  { immediate: true },
)

watch(
  () => props.formState?.capabilities?.language?.fileInput,
  (value) => {
    languageFileInputValue.value = booleanCapabilityFormValue(value)
  },
)

const isSubmitting = shallowRef(false)
async function onSubmit(data: ModelFormRawState) {
  if (isSubmitting.value) return
  let nativeOptions: Record<string, unknown> | undefined
  try {
    nativeOptions = parseProviderNativeOptions(data.nativeOptionsJson)
  } catch (error) {
    Toast.error(error instanceof Error ? error.message : '供应商原生参数不是有效的 JSON 对象。')
    return
  }
  const normalizedMappings = mappingsForModelType(
    parameterMappings.value,
    data.modelType as MappingModelType,
    selectedProviderType.value?.parameterDefinitions,
  )
  const mappingErrors = validateReasoningMappings(normalizedMappings)
  if (mappingErrors.length) {
    Toast.error(mappingErrors[0])
    return
  }
  if (!selectedProviderType.value) {
    Toast.error('供应商配置尚未加载完成，请稍后重试。')
    return
  }
  isSubmitting.value = true
  const submittedIdentity = formIdentity.value
  try {
    if (!(await waitForCatalogParameters()) || submittedIdentity !== formIdentity.value) return
    const features = featuresWithConstraints(
      data.features,
      effectiveFeatureConstraints.value,
      supportedFeatures.value,
    )
    const capabilities = buildCapabilities({ ...data, features })
    const capabilitySources = buildCapabilitySources(capabilities)
    if (capabilities && capabilitySources) {
      capabilities.sources = capabilitySources
    }
    emit('submit', {
      modelId: data.modelId,
      displayName: data.displayName,
      enabled: data.enabled,
      modelType: data.modelType,
      features: features.length ? features : undefined,
      adapterType: selectedAdapterType.value,
      capabilities,
      capabilitySources,
      parameterMappings: normalizedMappings,
      parameterConstraints:
        data.modelType === AiModelSpecModelTypeEnum.Decision
          ? undefined
          : filterFeatureSupport(parameterConstraints.value, supportedFeatures.value),
      catalogParameters: catalogParameters.value,
      nativeOptions,
    })
  } finally {
    isSubmitting.value = false
  }
}

defineExpose({
  submit: () => submitForm('model-form'),
  isSubmitting,
})

interface ModelFormRawState extends ModelFormState {
  nativeOptionsJson?: string
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
  domain: 'language' | 'imageGeneration',
  nextDomain?: LanguageCapability | ImageGenerationCapability,
) {
  if (!nextDomain) {
    return undefined
  }
  const previousDomain = props.formState?.capabilities?.[domain]
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

function onFeatureChange(feature: AiModelSpecFeaturesEnum, event: Event) {
  const input = event.target
  if (!(input instanceof HTMLInputElement) || input.type !== 'checkbox') return
  const features = new Set(featureBaseline.value)
  if (input.checked) features.add(feature)
  else features.delete(feature)
  featureBaseline.value = [...features]
  const parameterFeature = parameterFeatures.find((item) => item === feature)
  if (!parameterFeature) return
  // Only a checkbox interaction creates an override; FormKit value sync must not do so.
  parameterConstraints.value = withFeatureSupport(
    parameterConstraints.value,
    parameterFeature,
    input.checked,
  )
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
      @input="modelId = String($event || '')"
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
      v-if="hasMultipleAdapterOptions"
      type="select"
      name="adapterType"
      label="调用接口"
      :help="selectedAdapterDescription"
      validation="required"
      :options="adapterOptions"
      :value="selectedAdapterType"
      @input="selectedAdapterType = $event as AiModelSpecAdapterTypeEnum"
    />

    <FormKit
      :type="'switch' as unknown as FormKitTypeDefinition<boolean>"
      name="enabled"
      label="启用"
      :value="formState?.enabled ?? true"
    />

    <AdvancedSettingsCollapsible v-if="!isDecisionModel" title="参数映射">
      <p class=":uno: mb-3 text-xs text-gray-500">
        未配置的参数使用供应商覆盖、模型目录和内置默认值。
      </p>
      <ParameterMappingFields
        v-model="parameterMappings"
        v-model:constraints="parameterConstraints"
        context="model"
        :model-type="selectedModelType as MappingModelType"
        :adapter-type="selectedAdapterType"
        :definitions="selectedProviderType?.parameterDefinitions"
        :templates="selectedProviderType?.parameterMappingTemplates"
        :defaults="defaultParameterMappings"
        :inherited-mappings="inheritedParameters?.mappings || inheritedMappings"
        :effective-mappings="effectiveParameters?.mappings"
        :resolved-inheritance="Boolean(inheritedParameters)"
      />
    </AdvancedSettingsCollapsible>

    <AdvancedSettingsCollapsible v-if="!isDecisionModel" title="Token 限制">
      <p v-if="inheritedParametersError" class=":uno: text-xs text-red-600">
        暂时无法获取继承配置，请稍后重试。
      </p>
      <ModelParameterConstraintFields
        v-model="parameterConstraints"
        :model-type="selectedModelType"
        :inherited="inheritedParameters?.constraints"
      />
    </AdvancedSettingsCollapsible>

    <AdvancedSettingsCollapsible title="供应商原生参数">
      <FormKit
        type="textarea"
        name="nativeOptionsJson"
        label="原生参数（JSON）"
        help="仅用于这个模型的供应商专属参数，由对应 Provider 校验并发送。业务插件无法在请求中覆盖；密钥仍须通过供应商的 Secret 配置。"
        placeholder='例如：{&#10;  "reasoning_effort": "high"&#10;}'
        :rows="8"
        :value="formatProviderNativeOptions(formState?.nativeOptions)"
      />
      <ModelNativeParameterConstraintFields
        v-if="isLanguageCapabilityVisible"
        v-model="parameterConstraints"
        :inherited="inheritedParameters?.constraints"
        :loading="inheritedParametersLoading"
        :failed="inheritedParametersError"
      />
    </AdvancedSettingsCollapsible>

    <AdvancedSettingsCollapsible
      v-if="isLanguageCapabilityVisible || isImageGenerationCapabilityVisible"
      title="模型能力"
      :source-label="visibleCapabilitySourceLabel"
    >
      <template v-if="isLanguageCapabilityVisible">
        <ModelCatalogProvenance v-if="formState?.catalogMatch" :match="formState.catalogMatch" />
        <FormKit
          v-model="selectedFeatures"
          type="checkbox"
          name="features"
          label="模型能力"
          :options="featureOptions"
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
