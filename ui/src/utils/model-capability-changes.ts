import type { AiModel, LanguageCapability } from '@/api/generated'

type ModelSpec = AiModel['spec']
type CapabilitySnapshot = Pick<
  ModelSpec,
  'modelType' | 'adapterType' | 'features' | 'capabilities' | 'capabilitySources' | 'catalogMatch'
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

// Imports and saved-model edits must apply the same manual choices and adapter boundaries.
export function reconcileModelCapabilities(
  previous: CapabilitySnapshot,
  next: ModelSpec,
  options: { importing?: boolean } = {},
): ModelSpec {
  const spec = { ...next }
  // Existing non-catalog models keep the form's established edit semantics. Discovery imports
  // still need feature overrides to take precedence over explicit provider capability flags.
  if (!previous.catalogMatch && !options.importing) return spec
  if (spec.modelType !== previous.modelType) {
    delete spec.catalogMatch
    return spec
  }
  const changedFeatures = [
    ...new Set([...(previous.features || []), ...(spec.features || [])]),
  ].filter((feature) => previous.features?.includes(feature) !== spec.features?.includes(feature))
  if (spec.modelType !== 'language' || (!previous.catalogMatch && !changedFeatures.length)) {
    return spec
  }

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
    fields = fields.filter((field) => field !== `features.${feature}`)
    const enabled = spec.features?.includes(feature) === true
    if (feature === 'vision') {
      language.imageInput = enabled
      if (!edited.has('inputMediaTypes')) {
        language.inputMediaTypes = language.inputMediaTypes?.filter(
          (type) => !type.startsWith('image/'),
        )
        if (enabled) {
          language.inputMediaTypes = [
            ...(language.inputMediaTypes || []),
            'image/png',
            'image/jpeg',
          ]
        }
      }
    }
    if (feature === 'audio-input') {
      if (!edited.has('inputMediaTypes')) {
        language.inputMediaTypes = language.inputMediaTypes?.filter(
          (type) => !type.startsWith('audio/'),
        )
        if (enabled) {
          language.inputMediaTypes = [
            ...(language.inputMediaTypes || []),
            'audio/wav',
            'audio/mpeg',
          ]
        }
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
  const manual = changedFeatures.length > 0 || edited.size > 0
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
