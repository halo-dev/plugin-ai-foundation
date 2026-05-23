export const AI_FOUNDATION_ROUTE_NAMES = {
  ROOT: 'AiFoundation',
  PROVIDERS: 'AiFoundationProviders',
  MODELS: 'AiFoundationModels',
  DEFAULTS: 'AiFoundationDefaults',
  TEST: 'AiFoundationTest',
} as const

export type AiFoundationRouteName =
  (typeof AI_FOUNDATION_ROUTE_NAMES)[keyof typeof AI_FOUNDATION_ROUTE_NAMES]
