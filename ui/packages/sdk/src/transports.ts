import { AIUIError } from './errors'
import { generateId } from './id'
import { assertHaloUIMessageStreamResponse, readTextStream, readUIMessageSSEStream } from './stream'
import type {
  ChatTransport,
  FetchFunction,
  PreparedRequest,
  Resolvable,
  SendMessagesOptions,
  UIMessage,
  UIMessageChunk,
} from './types'

export interface HttpTransportOptions<METADATA = unknown> {
  api?: string
  credentials?: Resolvable<RequestCredentials | undefined>
  headers?: Resolvable<HeadersInit | undefined>
  body?: Resolvable<Record<string, unknown> | undefined>
  fetch?: FetchFunction
  prepareSendMessagesRequest?: (
    options: SendMessagesOptions<METADATA> & {
      api: string
      body?: Record<string, unknown>
      headers?: HeadersInit
      credentials?: RequestCredentials
    },
  ) => PreparedRequest | PromiseLike<PreparedRequest>
}

export abstract class HttpChatTransport<METADATA = unknown> implements ChatTransport<METADATA> {
  protected readonly api: string
  protected readonly credentials?: Resolvable<RequestCredentials | undefined>
  protected readonly headers?: Resolvable<HeadersInit | undefined>
  protected readonly body?: Resolvable<Record<string, unknown> | undefined>
  protected readonly fetch?: FetchFunction
  protected readonly prepareSendMessagesRequest?: HttpTransportOptions<METADATA>['prepareSendMessagesRequest']

  constructor(options: HttpTransportOptions<METADATA> = {}) {
    this.api = options.api ?? '/api/chat'
    this.credentials = options.credentials
    this.headers = options.headers
    this.body = options.body
    this.fetch = options.fetch
    this.prepareSendMessagesRequest = options.prepareSendMessagesRequest
  }

  async sendMessages(
    options: SendMessagesOptions<METADATA>,
  ): Promise<AsyncIterable<UIMessageChunk>> {
    const baseBody = await resolve(this.body)
    const baseHeaders = mergeHeaders(await resolve(this.headers), options.headers)
    const baseCredentials = options.credentials ?? (await resolve(this.credentials))
    const body = {
      ...baseBody,
      ...options.body,
      id: options.chatId,
      messages: options.messages,
      trigger: options.trigger,
      messageId: options.messageId ?? null,
    }

    const prepared = await this.prepareSendMessagesRequest?.({
      ...options,
      api: this.api,
      body,
      headers: baseHeaders,
      credentials: baseCredentials,
    })

    const response = await this.fetchResponse({
      api: prepared?.api ?? this.api,
      body: prepared?.body ?? body,
      headers: prepared?.headers ?? baseHeaders,
      credentials: prepared?.credentials ?? baseCredentials,
      abortSignal: options.abortSignal,
    })

    return this.processResponse(response)
  }

  protected async fetchResponse(options: {
    api: string
    body: Record<string, unknown>
    headers?: HeadersInit
    credentials?: RequestCredentials
    abortSignal?: AbortSignal
  }): Promise<Response> {
    const fetchImpl = this.fetch ?? globalThis.fetch
    if (!fetchImpl) {
      throw new AIUIError('No fetch implementation is available.')
    }

    const response = await fetchImpl(options.api, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headersToRecord(options.headers),
      },
      body: JSON.stringify(options.body),
      credentials: options.credentials,
      signal: options.abortSignal,
    })

    if (!response.ok) {
      throw new AIUIError((await response.text()) || 'Failed to fetch chat response.', {
        status: response.status,
        response,
      })
    }
    if (!response.body) {
      throw new AIUIError('The response body is empty.', {
        status: response.status,
        response,
      })
    }
    return response
  }

  protected abstract processResponse(response: Response): AsyncIterable<UIMessageChunk>
}

export class DefaultChatTransport<METADATA = unknown> extends HttpChatTransport<METADATA> {
  protected processResponse(response: Response): AsyncIterable<UIMessageChunk> {
    assertHaloUIMessageStreamResponse(response)
    return readUIMessageSSEStream(response.body!)
  }
}

export class TextStreamChatTransport<METADATA = unknown> extends HttpChatTransport<METADATA> {
  protected async *processResponse(response: Response): AsyncIterable<UIMessageChunk> {
    const id = generateId('text')
    yield { type: 'text-start', id }
    for await (const delta of readTextStream(response.body!)) {
      yield { type: 'text-delta', id, delta }
    }
    yield { type: 'text-end', id }
    yield { type: 'finish' }
  }
}

async function resolve<T>(value: Resolvable<T> | undefined): Promise<T | undefined> {
  return typeof value === 'function' ? await (value as () => T | Promise<T>)() : value
}

function mergeHeaders(...headers: Array<HeadersInit | undefined>): HeadersInit | undefined {
  return Object.assign({}, ...headers.map(headersToRecord))
}

function headersToRecord(headers: HeadersInit | undefined): Record<string, string> {
  if (!headers) {
    return {}
  }
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries())
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers)
  }
  return headers as Record<string, string>
}

export function createUserMessage(text: string, id = generateId('msg')): UIMessage {
  return {
    id,
    role: 'user',
    parts: [{ type: 'text', id: generateId('text'), text }],
  }
}
