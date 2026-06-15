import type { PreparedRequest } from './types'

export interface OpenAPIRequestArgs {
  url: string
  options?: {
    data?: unknown
    body?: BodyInit | null
    headers?: HeadersInit
    withCredentials?: boolean
    credentials?: RequestCredentials
  }
}

export function fromOpenAPIRequestArgs(
  args: OpenAPIRequestArgs,
  fallbackBody?: Record<string, unknown>,
): PreparedRequest {
  return {
    api: args.url,
    headers: args.options?.headers,
    body: normalizeBody(args.options?.data, fallbackBody),
    credentials:
      args.options?.credentials ?? (args.options?.withCredentials ? 'include' : undefined),
  }
}

function normalizeBody(
  body: unknown,
  fallbackBody?: Record<string, unknown>,
): Record<string, unknown> | undefined {
  if (isRecord(body)) {
    return body
  }
  if (body == null) {
    return fallbackBody
  }
  if (typeof body === 'string') {
    try {
      const parsed = JSON.parse(body)
      return isRecord(parsed) ? parsed : fallbackBody
    } catch {
      return fallbackBody
    }
  }
  return fallbackBody
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
