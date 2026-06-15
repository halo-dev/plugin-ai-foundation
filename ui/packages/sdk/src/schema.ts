import {
  AIUIError,
  AIUISchemaValidationError,
  type AIUISchemaValidationErrorOptions,
} from './errors'
import type { JsonSchema } from './types'

export interface StandardSchemaIssue {
  message?: string
  path?: readonly (string | number | symbol)[]
}

export type StandardSchemaValidationResult<T = unknown> =
  | { value: T; issues?: undefined }
  | { issues: readonly StandardSchemaIssue[]; value?: unknown }

export interface StandardSchemaLike<T = unknown> {
  '~standard': {
    version?: number
    vendor?: string
    validate: (
      value: unknown,
    ) => StandardSchemaValidationResult<T> | PromiseLike<StandardSchemaValidationResult<T>>
  }
}

export type SafeParseSchema<T = unknown> = {
  safeParse: (
    value: unknown,
  ) => { success: true; data: T } | { success: false; error?: { message?: string } | unknown }
  parse?: (value: unknown) => T
  toJSONSchema?: () => JsonSchema
  toJsonSchema?: () => JsonSchema
}

export type ParseSchema<T = unknown> = {
  parse: (value: unknown) => T
  safeParse?: SafeParseSchema<T>['safeParse']
  toJSONSchema?: () => JsonSchema
  toJsonSchema?: () => JsonSchema
}

export type SchemaLike<T = unknown> =
  | JsonSchema
  | StandardSchemaLike<T>
  | SafeParseSchema<T>
  | ParseSchema<T>

export type MessageMetadataSchema<METADATA = unknown> = SchemaLike<METADATA>
export type DataPartSchemas = Record<string, SchemaLike>

export interface RuntimeSchemaValidationContext {
  target: AIUISchemaValidationErrorOptions['target']
  partType?: string
  partName?: string
  partId?: string
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
  return validateSchemaValue(value, schema, {
    makeError: (message, cause) => new AIUIError(`Object validation failed: ${message}`, { cause }),
  })
}

export function validateRuntimeSchema<T>(
  value: unknown,
  schema: SchemaLike<T>,
  context: RuntimeSchemaValidationContext,
): T {
  return validateSchemaValue(value, schema, {
    makeError: (message, cause) =>
      new AIUISchemaValidationError(`UI message ${context.target} validation failed: ${message}`, {
        ...context,
        cause,
      }),
  })
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

function validateSchemaValue<T>(
  value: unknown,
  schema: SchemaLike<T>,
  options: {
    makeError: (message: string, cause?: unknown) => Error
  },
): T {
  if (isStandardSchema(schema)) {
    try {
      const result = schema['~standard'].validate(value)
      if (isPromiseLike(result)) {
        throw options.makeError('Async schemas are not supported for UI message streams.')
      }
      if ('issues' in result && result.issues && result.issues.length > 0) {
        throw options.makeError(formatStandardSchemaIssues(result.issues), result.issues)
      }
      return result.value as T
    } catch (error) {
      if (isAdapterError(error)) {
        throw error
      }
      throw options.makeError(errorMessage(error), error)
    }
  }

  const safeParseSchema = schema as { safeParse?: (value: unknown) => unknown }
  if (typeof safeParseSchema.safeParse === 'function') {
    try {
      const result = safeParseSchema.safeParse(value) as
        | { success: true; data: T }
        | { success: false; error?: unknown }
      if (!result.success) {
        throw options.makeError(errorMessage(result.error), result.error)
      }
      return result.data
    } catch (error) {
      if (isAdapterError(error)) {
        throw error
      }
      throw options.makeError(errorMessage(error), error)
    }
  }

  const parseSchema = schema as { parse?: (value: unknown) => T }
  if (typeof parseSchema.parse === 'function') {
    try {
      return parseSchema.parse(value)
    } catch (error) {
      throw options.makeError(errorMessage(error), error)
    }
  }

  try {
    validateJsonSchemaValue(value, toJsonSchema(schema), '$')
    return value as T
  } catch (error) {
    if (isAdapterError(error)) {
      throw error
    }
    throw options.makeError(errorMessage(error), error)
  }
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
      value.forEach((item, index) =>
        validateJsonSchemaValue(item, schema.items!, `${path}[${index}]`),
      )
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
  return (
    typeof schema === 'object' && schema !== null && ('type' in schema || 'properties' in schema)
  )
}

function isStandardSchema<T>(schema: SchemaLike<T>): schema is StandardSchemaLike<T> {
  return (
    typeof schema === 'object' &&
    schema !== null &&
    '~standard' in schema &&
    typeof (schema as StandardSchemaLike<T>)['~standard']?.validate === 'function'
  )
}

function isPromiseLike(value: unknown): value is PromiseLike<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as PromiseLike<unknown>).then === 'function'
  )
}

function isAdapterError(error: unknown): error is AIUIError | AIUISchemaValidationError {
  return error instanceof AIUIError
}

function formatStandardSchemaIssues(issues: readonly StandardSchemaIssue[]): string {
  return issues
    .map((issue) => {
      const path = issue.path?.length ? `${issue.path.map(String).join('.')}: ` : ''
      return `${path}${issue.message ?? 'Invalid value'}`
    })
    .join('; ')
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  if (typeof error === 'object' && error !== null && 'message' in error) {
    return String((error as { message?: unknown }).message)
  }
  return String(error)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
