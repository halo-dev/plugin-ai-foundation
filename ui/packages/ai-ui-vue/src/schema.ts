import { AIUIError } from './errors'
import type { JsonSchema } from './types'

export type SchemaLike<T = unknown> =
  | JsonSchema
  | {
      safeParse?: (value: unknown) => { success: boolean; error?: { message?: string } }
      parse?: (value: unknown) => T
      toJSONSchema?: () => JsonSchema
      toJsonSchema?: () => JsonSchema
    }

export function toJsonSchema(schema: SchemaLike): JsonSchema {
  if (isJsonSchema(schema)) {
    return schema
  }
  const withModernMethod = schema as { toJSONSchema?: () => JsonSchema }
  if (typeof withModernMethod.toJSONSchema === 'function') {
    return withModernMethod.toJSONSchema()
  }
  const withLegacyMethod = schema as { toJsonSchema?: () => JsonSchema }
  if (typeof withLegacyMethod.toJsonSchema === 'function') {
    return withLegacyMethod.toJsonSchema()
  }
  throw new AIUIError('A JSON Schema object is required unless the schema can export JSON Schema.')
}

export function validateFinalValue<T>(value: unknown, schema: SchemaLike<T>): T {
  const zodLike = schema as { safeParse?: (value: unknown) => { success: boolean; data?: T; error?: unknown } }
  if (typeof zodLike.safeParse === 'function') {
    const result = zodLike.safeParse(value)
    if (!result.success) {
      throw new AIUIError(`Object validation failed: ${String(result.error)}`)
    }
    return result.data as T
  }
  validateJsonSchemaValue(value, toJsonSchema(schema), '$')
  return value as T
}

export function parsePartialJson(text: string): unknown | undefined {
  const trimmed = text.trim()
  if (!trimmed) {
    return undefined
  }
  try {
    return JSON.parse(trimmed)
  } catch {
    const repaired = repairPartialJson(trimmed)
    if (!repaired) {
      return undefined
    }
    try {
      return JSON.parse(repaired)
    } catch {
      return undefined
    }
  }
}

function repairPartialJson(text: string): string | undefined {
  const stack: string[] = []
  let inString = false
  let escaped = false
  let result = ''

  for (const char of text) {
    result += char
    if (inString) {
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === '"') {
        inString = false
      }
      continue
    }
    if (char === '"') {
      inString = true
    } else if (char === '{') {
      stack.push('}')
    } else if (char === '[') {
      stack.push(']')
    } else if (char === '}' || char === ']') {
      if (stack[stack.length - 1] !== char) {
        return undefined
      }
      stack.pop()
    }
  }

  if (inString) {
    result += '"'
  }
  result = result.replace(/,\s*$/u, '')
  while (stack.length) {
    result += stack.pop()
  }
  return result
}

function validateJsonSchemaValue(value: unknown, schema: JsonSchema, path: string): void {
  if (schema.enum && !schema.enum.some((item) => Object.is(item, value))) {
    throw new AIUIError(`${path} must be one of ${schema.enum.join(', ')}`)
  }

  if (schema.type === 'object' || schema.properties) {
    if (!isRecord(value)) {
      throw new AIUIError(`${path} must be an object`)
    }
    for (const key of schema.required ?? []) {
      if (!(key in value)) {
        throw new AIUIError(`${path}.${key} is required`)
      }
    }
    for (const [key, child] of Object.entries(schema.properties ?? {})) {
      if (value[key] !== undefined) {
        validateJsonSchemaValue(value[key], child, `${path}.${key}`)
      }
    }
    return
  }

  if (schema.type === 'array' || schema.items) {
    if (!Array.isArray(value)) {
      throw new AIUIError(`${path} must be an array`)
    }
    if (schema.items) {
      value.forEach((item, index) => validateJsonSchemaValue(item, schema.items!, `${path}[${index}]`))
    }
    return
  }

  if (schema.type && !matchesType(value, schema.type)) {
    throw new AIUIError(`${path} must be ${schema.type}`)
  }
}

function matchesType(value: unknown, type: string): boolean {
  switch (type) {
    case 'string':
      return typeof value === 'string'
    case 'number':
      return typeof value === 'number'
    case 'integer':
      return Number.isInteger(value)
    case 'boolean':
      return typeof value === 'boolean'
    case 'null':
      return value === null
    default:
      return true
  }
}

function isJsonSchema(schema: SchemaLike): schema is JsonSchema {
  return typeof schema === 'object' && schema !== null && ('type' in schema || 'properties' in schema)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
