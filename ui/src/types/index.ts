export interface Metadata {
  name: string
  generateName?: string
  labels?: Record<string, string>
  annotations?: Record<string, string>
  creationTimestamp?: string | null
  version?: number | null
  finalizers?: string[] | null
  deletionTimestamp?: string | null
}

export interface AiProvider {
  apiVersion: string
  kind: string
  metadata: Metadata
  spec: AiProviderSpec
  status?: AiProviderStatus
}

export interface AiProviderSpec {
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl?: string
  apiKeySecretName?: string
  proxyHost?: string
  proxyPort?: number
  config?: Record<string, string>
}

export interface AiProviderStatus {
  phase: 'UNKNOWN' | 'OK' | 'ERROR'
  message?: string
  lastCheckedAt?: string
}

export interface AiModel {
  apiVersion: string
  kind: string
  metadata: Metadata
  spec: AiModelSpec
}

export interface AiModelSpec {
  providerName: string
  modelId: string
  displayName: string
  enabled: boolean
  group?: string
  capabilities?: string[]
  endpointType?: string
  supportedTextDelta?: boolean
}

export interface Secret {
  metadata: Metadata
  data?: Record<string, string>
}

export interface ConnectivityResult {
  phase: string
  message: string
  lastCheckedAt: string
}

export interface TestChatRequest {
  prompt: string
}

export interface TestChatResponse {
  modelRef: string
  content: string
  finishReason: string
}

export interface DiscoveredModel {
  modelId: string
  displayName: string
  name: string
}

export interface ProviderModelListResponse {
  models: DiscoveredModel[]
  providerName: string
}

export const SUPPORTED_PROVIDER_TYPES = [
  'aihubmix',
  'openai',
  'deepseek',
  'siliconflow',
  'doubao',
  'ernie',
  'zhipuai',
  'ollama',
  'openailike',
] as const

export const PROVIDER_TYPE_LABELS: Record<string, string> = {
  aihubmix: 'AiHubMix',
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  siliconflow: 'SiliconFlow',
  doubao: 'DouBao',
  ernie: 'ERNIE',
  zhipuai: 'ZhiPu AI',
  ollama: 'Ollama',
  openailike: 'OpenAI Compatible',
}

export const CAPABILITY_OPTIONS = [
  { label: '对话', value: 'chat' },
  { label: 'Embedding', value: 'embedding' },
  { label: '视觉', value: 'vision' },
  { label: '推理', value: 'reasoning' },
  { label: 'Function Calling', value: 'function_calling' },
] as const

export const ENDPOINT_TYPE_OPTIONS = [
  { label: 'OpenAI Chat', value: 'openai-chat' },
  { label: 'OpenAI Embedding', value: 'openai-embedding' },
  { label: 'Ollama Chat', value: 'ollama-chat' },
] as const

export const BUILT_IN_PROVIDERS = [
  'aihubmix',
  'openai',
  'deepseek',
  'siliconflow',
  'doubao',
  'ernie',
  'zhipuai',
]
