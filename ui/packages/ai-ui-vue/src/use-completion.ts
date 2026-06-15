import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { AIUIError, isAbortError, toError } from './errors'
import { generateId } from './id'
import { readTextStream } from './stream'
import type { FetchFunction, PreparedRequest, Resolvable } from './types'

export interface UseCompletionOptions {
  id?: string
  api?: string
  initialCompletion?: string
  initialInput?: string
  headers?: Resolvable<HeadersInit | undefined>
  body?: Resolvable<Record<string, unknown> | undefined>
  credentials?: Resolvable<RequestCredentials | undefined>
  fetch?: FetchFunction
  prepareRequest?: (options: {
    api: string
    body: Record<string, unknown>
    headers?: HeadersInit
    credentials?: RequestCredentials
  }) => PreparedRequest | PromiseLike<PreparedRequest>
  onError?: (error: Error) => void
  onFinish?: (prompt: string, completion: string) => void
}

export interface CompletionRequestOptions {
  headers?: HeadersInit
  body?: Record<string, unknown>
  credentials?: RequestCredentials
}

interface CompletionStore {
  subscribers: number
  completion: ReturnType<typeof ref<string>>
  input: ReturnType<typeof ref<string>>
  status: ReturnType<typeof ref<'idle' | 'loading'>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  abortController?: AbortController
}

const stores = new Map<string, CompletionStore>()

export function useCompletion(options: UseCompletionOptions = {}) {
  const id = options.id ?? generateId('completion')
  const store = getOrCreateStore(id, options)
  store.subscribers += 1
  onScopeDispose(() => {
    store.subscribers -= 1
    if (store.subscribers <= 0) {
      stores.delete(id)
    }
  })

  async function complete(prompt?: string, requestOptions: CompletionRequestOptions = {}): Promise<string | undefined> {
    const actualPrompt = prompt ?? store.input.value ?? ''
    store.status.value = 'loading'
    store.error.value = undefined
    store.completion.value = ''
    const abortController = new AbortController()
    store.abortController = abortController
    try {
      const body = { ...(await resolve(options.body)), ...requestOptions.body, prompt: actualPrompt }
      const headers = mergeHeaders(await resolve(options.headers), requestOptions.headers)
      const credentials = requestOptions.credentials ?? (await resolve(options.credentials))
      const prepared = await options.prepareRequest?.({
        api: options.api ?? '/api/completion',
        body,
        headers,
        credentials,
      })
      const response = await postTextRequest({
        api: prepared?.api ?? options.api ?? '/api/completion',
        fetch: options.fetch,
        body: prepared?.body ?? body,
        headers: prepared?.headers ?? headers,
        credentials: prepared?.credentials ?? credentials,
        signal: abortController.signal,
      })
      for await (const chunk of readTextStream(response.body!)) {
        store.completion.value += chunk
      }
      options.onFinish?.(actualPrompt, store.completion.value)
      return store.completion.value
    } catch (error) {
      if (!isAbortError(error) && !abortController.signal.aborted) {
        const normalized = toError(error)
        store.error.value = normalized
        options.onError?.(normalized)
      }
      return undefined
    } finally {
      if (store.abortController === abortController) {
        store.abortController = undefined
      }
      store.status.value = 'idle'
    }
  }

  function stop() {
    store.abortController?.abort()
  }

  function setCompletion(value: string) {
    store.completion.value = value
  }

  function setInput(value: string) {
    store.input.value = value
  }

  function handleInputChange(event: Event | string) {
    setInput(typeof event === 'string' ? event : (event.target as HTMLInputElement | null)?.value ?? '')
  }

  async function handleSubmit(event?: Event) {
    event?.preventDefault()
    return complete()
  }

  return {
    id,
    completion: readonly(store.completion),
    input: readonly(store.input),
    error: readonly(store.error),
    isLoading: computed(() => store.status.value === 'loading'),
    complete,
    stop,
    setCompletion,
    setInput,
    handleInputChange,
    handleSubmit,
  }
}

async function postTextRequest(options: {
  api: string
  fetch?: FetchFunction
  body: Record<string, unknown>
  headers?: HeadersInit
  credentials?: RequestCredentials
  signal?: AbortSignal
}): Promise<Response> {
  const fetchImpl = options.fetch ?? globalThis.fetch
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
    signal: options.signal,
  })
  if (!response.ok) {
    throw new AIUIError((await response.text()) || 'Failed to fetch completion.', {
      status: response.status,
      response,
    })
  }
  if (!response.body) {
    throw new AIUIError('The response body is empty.', { status: response.status, response })
  }
  return response
}

function getOrCreateStore(id: string, options: UseCompletionOptions): CompletionStore {
  const existing = stores.get(id)
  if (existing) {
    return existing
  }
  const store: CompletionStore = {
    subscribers: 0,
    completion: ref(options.initialCompletion ?? ''),
    input: ref(options.initialInput ?? ''),
    status: ref('idle'),
    error: shallowRef(),
  }
  stores.set(id, store)
  return store
}

async function resolve<T>(value: Resolvable<T> | undefined): Promise<T | undefined> {
  return typeof value === 'function' ? await (value as () => T | Promise<T>)() : value
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

function mergeHeaders(...headers: Array<HeadersInit | undefined>): HeadersInit | undefined {
  return Object.assign({}, ...headers.map(headersToRecord))
}
