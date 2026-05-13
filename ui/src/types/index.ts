

export interface TestChatRequest {
  prompt: string
}

export interface TestChatResponse {
  modelName: string
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
