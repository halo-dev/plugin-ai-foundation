import type {
  ImageGenerationCapability,
  LanguageCapability,
  ModelCapabilities,
  ModelCapabilitySources,
  ModelOption,
} from '@/api/generated'
import {
  ModelCapabilitySourcesImageGenerationEnum,
  ModelCapabilitySourcesLanguageEnum,
} from '@/api/generated'

export type RequiredModelCapabilities = Pick<ModelCapabilities, 'language' | 'imageGeneration'>
export type RequiredModelCapabilitiesValue = RequiredModelCapabilities | string | undefined

export function normalizeRequiredCapabilities(value?: RequiredModelCapabilitiesValue) {
  if (!value) {
    return undefined
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed || undefined
  }
  const normalized = pruneEmpty({
    language: pruneEmpty(value.language),
    imageGeneration: pruneEmpty(value.imageGeneration),
  })
  return normalized ? JSON.stringify(normalized) : undefined
}

export function capabilitySummaryLabels(capabilities?: ModelCapabilities) {
  const labels: string[] = []
  const language = capabilities?.language
  const image = capabilities?.imageGeneration

  if (language?.imageInput === true) {
    labels.push('图片识别')
  }
  if (language?.fileInput === true) {
    labels.push(hasAudioInput(language) ? '音频识别' : '文件识别')
  }
  if (language?.inputSources?.includes('url')) {
    labels.push('URL 输入')
  }
  if (image?.textToImage === true) {
    labels.push('文生图')
  }
  if (image?.imageToImage === true) {
    labels.push('图生图')
  }
  if (image?.maskInput === true) {
    labels.push('蒙版')
  }
  if (image?.maxImagesPerCall && image.maxImagesPerCall > 1) {
    labels.push(`单次 ${image.maxImagesPerCall} 张`)
  }

  return labels
}

export function capabilitySourceLabel(value?: string) {
  switch (value) {
    case ModelCapabilitySourcesLanguageEnum.Remote:
      return '远程'
    case ModelCapabilitySourcesLanguageEnum.BuiltIn:
      return '内置'
    case ModelCapabilitySourcesLanguageEnum.Manual:
      return '手动'
    case ModelCapabilitySourcesLanguageEnum.Unknown:
      return '未知'
    default:
      return value || '未知'
  }
}

export function capabilityDomainSource(
  capabilities: ModelCapabilities | undefined,
  sources: ModelCapabilitySources | undefined,
  domain: keyof ModelCapabilitySources,
) {
  return sources?.[domain] || capabilities?.sources?.[domain]
}

export function capabilityUnavailableDetailsLabel(model: ModelOption) {
  if (!model.unavailableDetails?.length) {
    return ''
  }
  const labels = model.unavailableDetails
    .map((detail) => capabilityPathLabel(detail.path))
    .filter(Boolean)
  return labels.length ? `缺少能力：${Array.from(new Set(labels)).join('、')}` : ''
}

export function capabilityPathLabel(path?: string) {
  switch (path) {
    case 'language.imageInput':
      return '支持图片识别'
    case 'language.fileInput':
      return '支持文件/音频识别'
    case 'language.inputMediaTypes':
      return '支持媒体类型'
    case 'language.inputSources':
      return '支持输入来源'
    case 'imageGeneration.textToImage':
      return '文生图'
    case 'imageGeneration.imageToImage':
      return '图生图'
    case 'imageGeneration.maskInput':
      return '蒙版'
    case 'imageGeneration.maxImagesPerCall':
      return '图片数量'
    case 'imageGeneration.sizes':
      return '图片尺寸'
    case 'imageGeneration.aspectRatios':
      return '宽高比'
    case 'imageGeneration.outputMediaTypes':
      return '支持输出媒体类型'
    default:
      return path || ''
  }
}

export function manualCapabilitySources(capabilities?: ModelCapabilities): ModelCapabilitySources {
  return {
    language: capabilities?.language ? ModelCapabilitySourcesLanguageEnum.Manual : undefined,
    imageGeneration: capabilities?.imageGeneration
      ? ModelCapabilitySourcesImageGenerationEnum.Manual
      : undefined,
  }
}

export function hasCapabilityDomain(domain?: LanguageCapability | ImageGenerationCapability) {
  return Boolean(pruneEmpty(domain))
}

function pruneEmpty<T>(value: T): T | undefined {
  if (!value || typeof value !== 'object') {
    return value
  }

  const entries = Object.entries(value as Record<string, unknown>).filter(([, entryValue]) => {
    if (entryValue === undefined || entryValue === null || entryValue === '') {
      return false
    }
    if (Array.isArray(entryValue)) {
      return entryValue.length > 0
    }
    if (typeof entryValue === 'object') {
      return Boolean(pruneEmpty(entryValue))
    }
    return true
  })

  if (!entries.length) {
    return undefined
  }
  return Object.fromEntries(entries) as T
}

function hasAudioInput(language: LanguageCapability) {
  return (
    language.inputMediaTypes?.some((mediaType) => mediaType.toLowerCase().startsWith('audio/')) ===
    true
  )
}
