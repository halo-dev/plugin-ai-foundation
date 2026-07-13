import type { TestMediaContent } from '@/api/generated'

export function numberOrUndefined(value: number | undefined) {
  return Number.isFinite(value) ? value : undefined
}

export function parseStringMapJson(input: string): {
  value?: Record<string, string>
  error?: string
} {
  const content = input.trim()
  if (!content) {
    return {}
  }
  try {
    const parsed = JSON.parse(content)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { error: 'Headers 必须是 JSON 对象' }
    }
    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      result[key] = String(value)
    }
    return { value: result }
  } catch {
    return { error: 'Headers 不是有效的 JSON' }
  }
}

export function buildTestMediaContent(options: {
  url: string
  data: string
  mediaType: string
  label: string
}): { value?: TestMediaContent; error?: string } {
  const url = options.url.trim()
  const data = options.data.trim()
  if (url && data) {
    return { error: `${options.label} 只能填写 URL 或 base64 其中一种` }
  }
  if (url) {
    return { value: { url, mediaType: options.mediaType.trim() || undefined } }
  }
  if (data) {
    return { value: { data, mediaType: options.mediaType.trim() || 'image/png' } }
  }
  return {}
}
