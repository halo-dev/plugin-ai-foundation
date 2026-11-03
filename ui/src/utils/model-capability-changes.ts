import type {
  AiModel,
  AiProvider,
  ImageGenerationCapability,
  LanguageCapability,
} from '@/api/generated'
import {
  featureConstraints,
  featureSupport,
  featuresWithConstraints,
  parameterFeatures,
  withFeatureSupport,
} from './model-feature-support'

type ModelSpec = AiModel['spec']
type CapabilitySnapshot = Pick<
  ModelSpec,
  'modelType' | 'adapterType' | 'features' | 'capabilities' | 'capabilitySources' | 'catalogMatch'
> &
  Partial<
    Pick<ModelSpec, 'modelId' | 'providerName' | 'catalogParameters' | 'parameterConstraints'>
  >

const languageFields = [
  'imageInput',
  'fileInput',
  'reasoningHistory',
  'inputMediaTypes',
  'inputSources',
] as const

function sameValue(left: unknown, right: unknown) {
  return JSON.stringify(left) === JSON.stringify(right)
}

type CatalogProvider = Pick<
  AiProvider['spec'],
  'providerType' | 'baseUrl' | 'chatEndpointPath' | 'parameterMappings'
>

function matchesCatalogIdentity(
  previous: CapabilitySnapshot,
  next: ModelSpec,
  provider?: CatalogProvider,
) {
  const profile = next.catalogParameters
  if (
    (next.modelType !== previous.modelType && next.adapterType === previous.adapterType) ||
    !profile?.providerType ||
    !profile.providerBaseUrl ||
    !profile.adapterType ||
    profile.modelId !== next.modelId ||
    profile.adapterType !== next.adapterType ||
    (previous.providerName !== undefined && next.providerName !== previous.providerName) ||
    (provider?.chatEndpointPath?.trim() &&
      next.adapterType.endsWith('-chat') &&
      next.adapterType !== 'ollama-chat')
  ) {
    return false
  }
  const providerType = provider?.providerType || previous.catalogParameters?.providerType
  if (providerType && profile.providerType !== providerType) return false
  // A blank configured endpoint uses provider metadata defaults, which are not available here.
  if (provider) {
    return !provider.baseUrl?.trim() || profile.providerBaseUrl === provider.baseUrl
  }
  const previousBaseUrl = previous.catalogParameters?.providerBaseUrl
  return !previousBaseUrl || profile.providerBaseUrl === previousBaseUrl
}

// Imports and saved-model edits must apply the same manual choices and adapter boundaries.
export function reconcileModelCapabilities(
  previous: CapabilitySnapshot,
  next: ModelSpec,
  options: { importing?: boolean; provider?: CatalogProvider } = {},
): ModelSpec {
  const spec = { ...next }
  const invalidCatalogParameters =
    spec.catalogParameters && !matchesCatalogIdentity(previous, spec, options.provider)
  if (invalidCatalogParameters) {
    delete spec.catalogParameters
  }
  if (spec.modelType === 'language') {
    for (const feature of parameterFeatures) {
      if (
        options.importing &&
        (previous.features?.includes(feature) === true) !==
          (spec.features?.includes(feature) === true)
      ) {
        spec.parameterConstraints = withFeatureSupport(
          spec.parameterConstraints,
          feature,
          spec.features?.includes(feature) === true,
        )
      }
    }
    const features = featuresWithConstraints(
      spec.features,
      featureConstraints(
        spec.catalogParameters,
        spec.parameterConstraints,
        options.provider?.parameterMappings,
        spec.parameterMappings,
      ),
    )
    spec.features = features.length || spec.features !== undefined ? features : undefined
  }
  // Existing non-catalog models keep the form's established edit semantics. Discovery imports
  // still need feature overrides to take precedence over explicit provider capability flags.
  if (!previous.catalogMatch && !options.importing) return spec
  if (
    spec.modelType !== previous.modelType ||
    (previous.modelId !== undefined && spec.modelId !== previous.modelId) ||
    (previous.catalogMatch?.modelId !== undefined &&
      spec.modelId !== previous.catalogMatch.modelId) ||
    (previous.providerName !== undefined && spec.providerName !== previous.providerName)
  ) {
    delete spec.catalogMatch
    return spec
  }
  // The import builder does not copy provenance. Keep it for every model type before
  // capability-specific branches return, then remove only the fields they override.
  if (previous.catalogMatch) {
    spec.catalogMatch = {
      ...previous.catalogMatch,
      fields: previous.catalogMatch.fields?.filter((field) => {
        if (spec.modelType === 'language') return true
        if (field === 'adapterType' && spec.adapterType !== previous.adapterType) return false
        return field !== 'parameters' || !invalidCatalogParameters
      }),
    }
  }
  if (spec.modelType === 'image-generation' && previous.catalogMatch) {
    return reconcileImageCapabilities(previous, spec)
  }
  const changedFeatures = [
    ...new Set([...(previous.features || []), ...(spec.features || [])]),
  ].filter((feature) => previous.features?.includes(feature) !== spec.features?.includes(feature))
  if (spec.modelType !== 'language' || (!previous.catalogMatch && !changedFeatures.length)) {
    return spec
  }
  return reconcileLanguageCapabilities(previous, spec, changedFeatures)
}

function manuallyChangedFeatures(
  previous: CapabilitySnapshot,
  spec: ModelSpec,
  changedFeatures: NonNullable<ModelSpec['features']>,
) {
  const manualFeatureChanges = new Set(
    changedFeatures.filter((feature) => {
      const automatic = featureSupport(feature, spec.catalogParameters?.constraints)
      return (
        automatic === undefined ||
        featureSupport(feature, spec.parameterConstraints) !== undefined ||
        automatic !== (spec.features?.includes(feature) === true)
      )
    }),
  )
  for (const feature of parameterFeatures) {
    const manual = featureSupport(feature, spec.parameterConstraints)
    if (manual !== undefined && manual !== featureSupport(feature, previous.parameterConstraints)) {
      manualFeatureChanges.add(feature)
    }
  }
  return manualFeatureChanges
}

function reconcileLanguageCapabilities(
  previous: CapabilitySnapshot,
  spec: ModelSpec,
  changedFeatures: NonNullable<ModelSpec['features']>,
): ModelSpec {
  const manualFeatureChanges = manuallyChangedFeatures(previous, spec, changedFeatures)
  const before = previous.capabilities?.language
  const language: LanguageCapability = { ...spec.capabilities?.language }
  // The form derives imageInput from the feature checkbox and represents empty lists as absent.
  // Preserve unchanged catalog values so a rename cannot broaden explicit empty coverage.
  language.imageInput ??= before?.imageInput
  for (const key of ['inputMediaTypes', 'inputSources'] as const) {
    if (language[key] === undefined && before?.[key]?.length === 0) language[key] = []
  }
  const edited = new Set(languageFields.filter((key) => !sameValue(before?.[key], language[key])))
  let fields = [...(previous.catalogMatch?.fields || [])]
  if (spec.adapterType !== previous.adapterType) {
    for (const key of languageFields) {
      if (fields.includes(`language.${key}`) && !edited.has(key)) delete language[key]
    }
    fields = fields.filter((field) => !field.startsWith('language.'))
  }

  for (const feature of changedFeatures) {
    if (manualFeatureChanges.has(feature)) {
      fields = fields.filter((field) => field !== `features.${feature}`)
    }
    const enabled = spec.features?.includes(feature) === true
    if (feature === 'vision') {
      language.imageInput = enabled
      if (!edited.has('inputMediaTypes')) {
        language.inputMediaTypes = updateMediaTypes(language.inputMediaTypes, 'image/', enabled, [
          'image/png',
          'image/jpeg',
        ])
      }
    }
    if (feature === 'audio-input') {
      if (!edited.has('inputMediaTypes')) {
        language.inputMediaTypes = updateMediaTypes(language.inputMediaTypes, 'audio/', enabled, [
          'audio/wav',
          'audio/mpeg',
        ])
      }
      if (!edited.has('fileInput')) {
        language.fileInput =
          enabled ||
          language.inputMediaTypes?.some(
            (type) => !type.startsWith('image/') && !type.startsWith('audio/'),
          ) === true
      }
    }
    if (
      (feature === 'vision' || feature === 'audio-input') &&
      enabled &&
      !edited.has('inputSources') &&
      !language.inputSources?.length
    ) {
      language.inputSources = ['data']
    }
  }

  for (const feature of parameterFeatures) {
    const field = `features.${feature}`
    if (
      featureSupport(feature, spec.parameterConstraints) !== undefined ||
      spec.catalogParameters?.featureEvidence?.[feature] !== undefined
    ) {
      fields = fields.filter((item) => item !== field)
    } else if (
      spec.catalogParameters?.featureEvidence !== undefined &&
      featureSupport(feature, spec.catalogParameters.constraints) !== undefined &&
      !fields.includes(field)
    ) {
      fields.push(field)
    }
  }

  for (const key of languageFields) {
    // A feature toggle can replace one modality while retaining catalog formats in another.
    // Keep their attribution so a later adapter change still clears incompatible coverage.
    if (
      key === 'inputMediaTypes' &&
      !edited.has(key) &&
      language.inputMediaTypes?.some((type) => before?.inputMediaTypes?.includes(type))
    ) {
      continue
    }
    if (!sameValue(before?.[key], language[key])) {
      fields = fields.filter((field) => field !== `language.${key}`)
    }
  }
  const manual = manualFeatureChanges.size > 0 || edited.size > 0
  const sources = {
    ...spec.capabilitySources,
    language: manual
      ? ('manual' as const)
      : (previous.capabilitySources?.language ?? previous.capabilities?.sources?.language),
  }
  spec.capabilities = { ...spec.capabilities, language, sources }
  spec.capabilitySources = sources
  // Keep the import identity even if every field was overridden: precise media lists must
  // not fall back to the pre-catalog audio wildcard. Only fields still attributed are shown.
  if (previous.catalogMatch) spec.catalogMatch = { ...previous.catalogMatch, fields }
  return spec
}

function updateMediaTypes(
  current: string[] | undefined,
  prefix: string,
  enabled: boolean,
  supported: string[],
) {
  const remaining = current?.filter((type) => !type.startsWith(prefix))
  return enabled ? [...(remaining || []), ...supported] : remaining
}

function reconcileImageCapabilities(previous: CapabilitySnapshot, spec: ModelSpec): ModelSpec {
  const before = previous.capabilities?.imageGeneration
  if (!before && !spec.capabilities?.imageGeneration) return spec
  const imageGeneration: ImageGenerationCapability = { ...spec.capabilities?.imageGeneration }
  // Empty catalog coverage must survive the form's empty-list normalization.
  if (imageGeneration.outputMediaTypes === undefined && before?.outputMediaTypes?.length === 0) {
    imageGeneration.outputMediaTypes = []
  }
  const edited = !sameValue(before?.outputMediaTypes, imageGeneration.outputMediaTypes)
  const manual = !sameValue(before ?? {}, imageGeneration)
  let fields = [...(spec.catalogMatch?.fields || [])]
  if (spec.adapterType !== previous.adapterType && !edited) {
    if (fields.includes('imageGeneration.outputMediaTypes')) {
      delete imageGeneration.outputMediaTypes
    }
  }
  if (edited || spec.adapterType !== previous.adapterType) {
    fields = fields.filter((field) => field !== 'imageGeneration.outputMediaTypes')
  }
  // Clearing the final field restores inheritance. False and explicit empty lists still
  // declare capabilities and must not be mistaken for an unconfigured domain.
  if (!hasDefinedValues(imageGeneration)) {
    const capabilities = { ...spec.capabilities }
    delete capabilities.imageGeneration
    const embeddedSources = { ...capabilities.sources }
    delete embeddedSources.imageGeneration
    if (hasDefinedValues(embeddedSources)) capabilities.sources = embeddedSources
    else delete capabilities.sources
    const sources = { ...spec.capabilitySources }
    delete sources.imageGeneration
    return {
      ...spec,
      capabilities: hasDefinedValues(capabilities) ? capabilities : undefined,
      capabilitySources: hasDefinedValues(sources) ? sources : undefined,
      catalogMatch: { ...spec.catalogMatch!, fields },
    }
  }
  const previousSource =
    previous.capabilitySources?.imageGeneration ?? previous.capabilities?.sources?.imageGeneration
  if (manual && previousSource !== 'manual') {
    // The source applies to the whole domain. Capture the same defaults as
    // ModelCapabilityService before switching to manual, only for untouched fields.
    if (before?.textToImage == null && imageGeneration.textToImage == null) {
      imageGeneration.textToImage = true
    }
    if (before?.maxImagesPerCall == null && imageGeneration.maxImagesPerCall == null) {
      imageGeneration.maxImagesPerCall = 1
    }
  }
  const sources = {
    ...spec.capabilitySources,
    imageGeneration: manual ? ('manual' as const) : previousSource,
  }
  return {
    ...spec,
    capabilities: { ...spec.capabilities, imageGeneration, sources },
    capabilitySources: sources,
    catalogMatch: { ...spec.catalogMatch!, fields },
  }
}

function hasDefinedValues(value: object): boolean {
  return Object.values(value).some((field) => field !== undefined && field !== null)
}