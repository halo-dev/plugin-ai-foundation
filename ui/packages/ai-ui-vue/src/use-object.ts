import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { AIUIError, isAbortError, toError } from './errors'
import { generateId } from './id'
import { parsePartialJson, toJsonSchema, validateFinalValue, type SchemaLike } from './schema'
import { readTextStream } from './stream'
import type { DeepPartial, FetchFunction, JsonSchema, PreparedRequest, Resolvable } from './types'

export interface UseObjectOptions<T = unknown> {
  id?: string
  api?: string
  schema: SchemaLike<T>
  initialValue?: DeepPartial<T>
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
  onFinish?: (event: { object: T; text: string }) => void
}

export interface ObjectRequestOptions {
  headers?: HeadersInit
  body?: Record<string, unknown>
  credentials?: RequestCredentials
}

interface ObjectStore<T> {
  subscribers: number
  object: ReturnType<typeof shallowRef<DeepPartial<T> | undefined>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  isLoading: ReturnType<typeof ref<boolean>>
  text: string
  abortController?: AbortController
}

const stores = new Map<string, ObjectStore<unknown>>()

export function experimental_useObject<T = unknown, INPUT = unknown>(options: UseObjectOptions<T>) {
  const id = options.id ?? generateId('object')
  const store = getOrCreateStore(id, options)
  store.subscribers += 1
  onScopeDispose(() => {
    store.subscribers -= 1
    if (store.subscribers <= 0) {
      stores.delete(id)
    }
  })

  async function submit(input: INPUT, submitOptions: ObjectRequestOptions = {}) {
    store.isLoading.value = true
    store.error.value = undefined
    store.text = ''
    store.object.value = undefined
    const abortController = new AbortController()
    store.abortController = abortController
    try {
      const jsonSchema = toJsonSchema(options.schema)
      const body = {
        ...(await resolve(options.body)),
        ...submitOptions.body,
        input,
        schema: jsonSchema,
        output: { type: 'object', schema: jsonSchema },
      }
      const headers = mergeHeaders(await resolve(options.headers), submitOptions.headers)
      const credentials = submitOptions.credentials ?? (await resolve(options.credentials))
      const prepared = await options.prepareRequest?.({
        api: options.api ?? '/api/object',
        body,
        headers,
        credentials,
      })
      const response = await postObjectRequest({
        api: prepared?.api ?? options.api ?? '/api/object',
        fetch: options.fetch,
        body: prepared?.body ?? body,
        headers: prepared?.headers ?? headers,
        credentials: prepared?.credentials ?? credentials,
        signal: abortController.signal,
      })

      for await (const chunk of readTextStream(response.body!)) {
        store.text += chunk
        const partial = parsePartialJson(store.text)
        if (partial !== undefined) {
          store.object.value = partial as DeepPartial<T>
        }
      }

      const finalValue = validateFinalValue<T>(JSON.parse(store.text), options.schema)
      store.object.value = finalValue as DeepPartial<T>
      options.onFinish?.({ object: finalValue, text: store.text })
      return finalValue
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
      store.isLoading.value = false
    }
  }

  function stop() {
    store.abortController?.abort()
  }

  function clear() {
    store.text = ''
    store.object.value = undefined
    store.error.value = undefined
  }

  return {
    id,
    object: readonly(store.object),
    error: readonly(store.error),
    isLoading: readonly(store.isLoading),
    submit,
    stop,
    clear,
    text: computed(() => store.text),
  }
}

async function postObjectRequest(options: {
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
    throw new AIUIError((await response.text()) || 'Failed to fetch object stream.', {
      status: response.status,
      response,
    })
  }
  if (!response.body) {
    throw new AIUIError('The response body is empty.', { status: response.status, response })
  }
  return response
}

function getOrCreateStore<T>(id: string, options: UseObjectOptions<T>): ObjectStore<T> {
  const existing = stores.get(id)
  if (existing) {
    return existing as ObjectStore<T>
  }
  const store: ObjectStore<T> = {
    subscribers: 0,
    object: shallowRef<DeepPartial<T> | undefined>(options.initialValue),
    error: shallowRef(),
    isLoading: ref(false),
    text: '',
  }
  stores.set(id, store as ObjectStore<unknown>)
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

export function jsonSchema<T = unknown>(schema: JsonSchema): SchemaLike<T> {
  return schema
}
