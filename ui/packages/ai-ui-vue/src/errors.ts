export class AIUIError extends Error {
  readonly status?: number
  readonly response?: Response

  constructor(message: string, options: { status?: number; response?: Response; cause?: unknown } = {}) {
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

export function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error))
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError'
}
