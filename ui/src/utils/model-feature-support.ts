import type {
  AiModelSpecFeaturesEnum,
  ModelParameterConstraints,
  ModelParameterMappings,
  ModelParameterProfile,
} from '@/api/generated'

export const parameterFeatures = ['tool-call', 'structured-output', 'reasoning'] as const
export type ParameterFeature = (typeof parameterFeatures)[number]

export function featureSupport(
  feature: string,
  constraints?: ModelParameterConstraints,
): boolean | undefined {
  if (feature === 'tool-call') return constraints?.toolCalling
  if (feature === 'structured-output') return constraints?.structuredOutput
  if (feature === 'reasoning') return constraints?.reasoning?.supported
}

export function withFeatureSupport(
  constraints: ModelParameterConstraints | undefined,
  feature: ParameterFeature,
  supported: boolean,
): ModelParameterConstraints {
  if (feature === 'reasoning') {
    return { ...constraints, reasoning: { ...constraints?.reasoning, supported } }
  }
  return {
    ...constraints,
    [feature === 'tool-call' ? 'toolCalling' : 'structuredOutput']: supported,
  }
}

export function filterFeatureSupport(
  constraints: ModelParameterConstraints | undefined,
  supportedFeatures: readonly AiModelSpecFeaturesEnum[] | undefined,
): ModelParameterConstraints | undefined {
  if (!constraints || !supportedFeatures) return constraints
  const result = { ...constraints }
  if (!supportedFeatures.includes('tool-call')) delete result.toolCalling
  if (!supportedFeatures.includes('structured-output')) delete result.structuredOutput
  if (!supportedFeatures.includes('reasoning') && result.reasoning) {
    const reasoning = { ...result.reasoning }
    delete reasoning.supported
    if (Object.keys(reasoning).length) result.reasoning = reasoning
    else delete result.reasoning
  }
  return Object.keys(result).length ? result : undefined
}

// Administrator reasoning mappings replace catalog reasoning constraints in the runtime resolver.
export function featureConstraints(
  profile?: ModelParameterProfile,
  manual?: ModelParameterConstraints,
  ...mappings: (ModelParameterMappings | undefined)[]
): ModelParameterConstraints {
  const reasoningOverride = mappings.some((mapping) => {
    const mode = mapping?.language?.reasoning?.mode
    return mode !== undefined && mode !== 'INHERIT'
  })
  return {
    toolCalling: manual?.toolCalling ?? profile?.constraints?.toolCalling,
    structuredOutput: manual?.structuredOutput ?? profile?.constraints?.structuredOutput,
    reasoning: {
      supported:
        manual?.reasoning?.supported ??
        (reasoningOverride ? undefined : profile?.constraints?.reasoning?.supported),
    },
  }
}

export function featuresWithConstraints(
  features: readonly AiModelSpecFeaturesEnum[] | undefined,
  constraints?: ModelParameterConstraints,
  supportedFeatures?: readonly AiModelSpecFeaturesEnum[],
): AiModelSpecFeaturesEnum[] {
  const result = new Set(features)
  for (const feature of parameterFeatures) {
    const supported = featureSupport(feature, constraints)
    if (supported === true) result.add(feature)
    if (supported === false) result.delete(feature)
  }
  return [...result].filter((feature) => !supportedFeatures || supportedFeatures.includes(feature))
}