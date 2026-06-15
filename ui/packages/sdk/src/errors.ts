export class AIUIError extends Error {
  readonly status?: number
  readonly response?: Response

  constructor(
    message: string,
    options: { status?: number; response?: Response; cause?: unknown } = {},
  ) {
    super(message)
    this.name = 'AIUIError'
    this.status = options.status
    this.response = options.response
    if (options.cause !== undefined) {
      Object.defineProperty(this, 'cause', {
        configurable: true,
        enumerable: false,
        value: options.cause,
      })
    }
  }
}

export class AIUIProtocolError extends AIUIError {
  constructor(message: string, options: { cause?: unknown } = {}) {
    super(message, options)
    this.name = 'AIUIProtocolError'
  }
}

export type AIUISchemaValidationTarget = 'message-metadata' | 'data-part'

export interface AIUISchemaValidationErrorOptions {
  target: AIUISchemaValidationTarget
  partType?: string
  partName?: string
  partId?: string
  cause?: unknown
}

export class AIUISchemaValidationError extends AIUIProtocolError {
  readonly target: AIUISchemaValidationTarget
  readonly partType?: string
  readonly partName?: string
  readonly partId?: string

  constructor(message: string, options: AIUISchemaValidationErrorOptions) {
    super(message, { cause: options.cause })
    this.name = 'AIUISchemaValidationError'
    this.target = options.target
    this.partType = options.partType
    this.partName = options.partName
    this.partId = options.partId
  }
}

export function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error))
}

export function isProtocolError(error: unknown): boolean {
  return error instanceof AIUIProtocolError
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError'
}
