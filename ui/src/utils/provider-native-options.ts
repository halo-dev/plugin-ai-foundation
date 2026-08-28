export type ProviderNativeOptions = Record<string, unknown>

export function parseProviderNativeOptions(value: unknown): ProviderNativeOptions | undefined {
  if (typeof value !== 'string') {
    return undefined
  }
  if (!value.trim()) {
    return undefined
  }
  const parsed: unknown = JSON.parse(value)
  if (!isPlainObject(parsed)) {
    throw new Error('供应商原生参数必须是 JSON 对象。')
  }
  return Object.keys(parsed).length ? parsed : undefined
}

export function formatProviderNativeOptions(value?: ProviderNativeOptions) {
  if (!value) {
    return ''
  }
  if (!Object.keys(value).length) {
    return ''
  }
  return JSON.stringify(value, null, 2)
}

function isPlainObject(value: unknown): value is ProviderNativeOptions {
  if (value === null) {
    return false
  }
  if (typeof value !== 'object') {
    return false
  }
  return !Array.isArray(value)
}
