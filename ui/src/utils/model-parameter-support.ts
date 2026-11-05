import type { ModelParameterMappings, ModelParameterProfile } from '@/api/generated'
import type { ReasoningEffort, ReasoningMode } from './model-test-workbench'

export type LanguageParameter = keyof NonNullable<ModelParameterMappings['language']>

export function supportsLanguageParameter(
  profile: ModelParameterProfile | undefined,
  parameter: LanguageParameter,
) {
  if (profile?.mappings?.language?.[parameter]?.mode === 'UNSUPPORTED') return false
  if (parameter === 'temperature' || parameter === 'topP' || parameter === 'topK') {
    return profile?.constraints?.[parameter] !== false
  }
  if (parameter === 'parallelToolCalls') return profile?.constraints?.toolCalling !== false
  return true
}

export function supportsReasoningIntent(
  profile: ModelParameterProfile | undefined,
  mode: ReasoningMode,
  effort: ReasoningEffort = 'MEDIUM',
) {
  if (mode === 'DEFAULT') return true
  const selection = profile?.mappings?.language?.reasoning
  if (selection?.mode === 'UNSUPPORTED' || profile?.constraints?.reasoning?.supported === false)
    return false
  if (mode === 'DISABLED' && profile?.constraints?.reasoning?.toggle === false) return false
  const intent = mode === 'EFFORT' ? effort.toLowerCase() : mode.toLowerCase()
  if (selection?.reasoningMapping) {
    return Boolean(selection.reasoningMapping[intent as keyof typeof selection.reasoningMapping])
  }
  return true
}