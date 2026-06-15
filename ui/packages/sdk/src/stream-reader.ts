import { isAbortError, isProtocolError, toError } from './errors'
import { generateId } from './id'
import { applyUIMessageChunk, createUIMessageReducer } from './message-reducer'
import type { DataPartSchemas, MessageMetadataSchema } from './schema'
import { assertHaloUIMessageStreamResponse, readUIMessageSSEStream } from './stream'
import type {
  DataPart,
  IdGenerator,
  ToolPart,
  UIMessage,
  UIMessageChunk,
  UIMessagePart,
  UIMessageStreamTerminal,
} from './types'

export type UIMessageStreamReadStatus = 'ready' | 'error' | 'disconnected' | 'aborted'

export interface ReadUIMessageStreamOptions<METADATA = unknown> {
  stream?: AsyncIterable<UIMessageChunk>
  readableStream?: ReadableStream<Uint8Array>
  response?: Response
  message?: UIMessage<METADATA>
  messageId?: string
  metadata?: METADATA
  generateId?: IdGenerator
  messageMetadataSchema?: MessageMetadataSchema<METADATA>
  dataPartSchemas?: DataPartSchemas
  abortSignal?: AbortSignal
  throwOnError?: boolean
  onChunk?: (chunk: UIMessageChunk) => void | Promise<void>
  onMessage?: (message: UIMessage<METADATA>) => void | Promise<void>
  onData?: (part: DataPart) => void | Promise<void>
  onToolCall?: (part: ToolPart) => void | Promise<void>
  onError?: (error: Error) => void | Promise<void>
  onFinish?: (event: UIMessageStreamFinishEvent<METADATA>) => void | Promise<void>
}

export interface UIMessageStreamReadResult<METADATA = unknown> {
  message: UIMessage<METADATA>
  terminal: UIMessageStreamTerminal
  status: UIMessageStreamReadStatus
  isAbort: boolean
  isError: boolean
  error?: Error
}

export type UIMessageStreamFinishEvent<METADATA = unknown> = UIMessageStreamReadResult<METADATA>

export async function readUIMessageStream<METADATA = unknown>(
  options: ReadUIMessageStreamOptions<METADATA>,
): Promise<UIMessageStreamReadResult<METADATA>> {
  const reducer = createUIMessageReducer<METADATA>({
    message: options.message,
    messageId: options.message
      ? undefined
      : (options.messageId ?? options.generateId?.() ?? generateId('msg')),
    metadata: options.message ? undefined : options.metadata,
    messageMetadataSchema: options.messageMetadataSchema,
    dataPartSchemas: options.dataPartSchemas,
  })
  const notifiedToolCalls = new Set<string>()
  let status: UIMessageStreamReadStatus = 'ready'
  let error: Error | undefined
  let hasAcceptedChunk = false
  let onErrorFailure: Error | undefined

  try {
    const stream = streamFromOptions(options)
    for await (const chunk of streamWithAbort(stream, options.abortSignal)) {
      throwIfAborted(options.abortSignal)
      await callReaderCallback(options.onChunk, chunk)
      applyUIMessageChunk(reducer, chunk)
      hasAcceptedChunk = true
      if (isDataChunk(chunk)) {
        await callReaderCallback(
          options.onData,
          cloneDataPart(dataPartFromChunk(reducer.message, chunk)),
        )
      }
      const lastPart = reducer.message.parts[reducer.message.parts.length - 1]
      if (
        lastPart &&
        isToolPart(lastPart) &&
        lastPart.state === 'input-available' &&
        !notifiedToolCalls.has(lastPart.toolCallId)
      ) {
        notifiedToolCalls.add(lastPart.toolCallId)
        await callReaderCallback(options.onToolCall, cloneToolPart(lastPart))
      }
      if (reducer.visible && chunk.type !== 'error' && chunk.type !== 'abort') {
        await callReaderCallback(options.onMessage, cloneMessage(reducer.message))
      }
    }
  } catch (caught) {
    if (isAbortError(caught) || options.abortSignal?.aborted) {
      status = 'aborted'
    } else {
      error = caught instanceof ReaderCallbackError ? toError(caught.original) : toError(caught)
      status =
        caught instanceof ReaderCallbackError || !hasAcceptedChunk || isProtocolError(error)
          ? 'error'
          : 'disconnected'
      try {
        await options.onError?.(error)
      } catch (onErrorCaught) {
        onErrorFailure = toError(onErrorCaught)
      }
    }
  }

  const result: UIMessageStreamReadResult<METADATA> = {
    message: cloneMessage(reducer.message),
    terminal: { ...reducer.terminal },
    status,
    isAbort: status === 'aborted',
    isError: status === 'error',
    error,
  }

  await options.onFinish?.({
    ...result,
    message: cloneMessage(result.message),
    terminal: { ...result.terminal },
  })

  if (onErrorFailure) {
    throw onErrorFailure
  }
  if (options.throwOnError && result.error) {
    throw result.error
  }
  return result
}

class ReaderCallbackError extends Error {
  readonly original: unknown

  constructor(original: unknown) {
    super(toError(original).message)
    this.name = 'ReaderCallbackError'
    this.original = original
  }
}

async function callReaderCallback<T>(
  callback: ((value: T) => void | Promise<void>) | undefined,
  value: T,
): Promise<void> {
  if (!callback) {
    return
  }
  try {
    await callback(value)
  } catch (error) {
    throw new ReaderCallbackError(error)
  }
}

function streamFromOptions<METADATA>(
  options: ReadUIMessageStreamOptions<METADATA>,
): AsyncIterable<UIMessageChunk> {
  if (options.stream) {
    return options.stream
  }
  if (options.readableStream) {
    return readUIMessageSSEStream(options.readableStream)
  }
  if (options.response) {
    assertHaloUIMessageStreamResponse(options.response)
    if (!options.response.body) {
      throw new Error('The response body is empty.')
    }
    return readUIMessageSSEStream(options.response.body)
  }
  return emptyStream()
}

async function* streamWithAbort<T>(
  stream: AsyncIterable<T>,
  abortSignal?: AbortSignal,
): AsyncIterable<T> {
  const iterator = stream[Symbol.asyncIterator]()
  try {
    while (true) {
      throwIfAborted(abortSignal)
      const next = await nextWithAbort(iterator, abortSignal)
      if (next.done) {
        break
      }
      yield next.value
    }
  } finally {
    await iterator.return?.()
  }
}

function nextWithAbort<T>(
  iterator: AsyncIterator<T>,
  abortSignal?: AbortSignal,
): Promise<IteratorResult<T>> {
  if (!abortSignal) {
    return iterator.next()
  }
  return Promise.race([
    iterator.next(),
    new Promise<IteratorResult<T>>((_, reject) => {
      if (abortSignal.aborted) {
        reject(abortError())
        return
      }
      abortSignal.addEventListener('abort', () => reject(abortError()), { once: true })
    }),
  ])
}

function throwIfAborted(abortSignal?: AbortSignal): void {
  if (abortSignal?.aborted) {
    throw abortError()
  }
}

function abortError(): DOMException {
  return new DOMException('Aborted', 'AbortError')
}

async function* emptyStream(): AsyncIterable<UIMessageChunk> {
  // empty by design
}

function dataPartFromChunk<METADATA>(
  message: UIMessage<METADATA>,
  chunk: Extract<UIMessageChunk, { type: `data-${string}` }>,
): DataPart {
  if (chunk.transient) {
    return {
      type: chunk.type,
      id: chunk.id,
      name: chunk.name,
      data: chunk.data,
      transientData: true,
    }
  }
  const part = message.parts.find(
    (item): item is DataPart =>
      item.type === chunk.type && item.type.startsWith('data-') && item.id === chunk.id,
  )
  return (
    part ?? {
      type: chunk.type,
      id: chunk.id,
      name: chunk.name,
      data: chunk.data,
      transientData: false,
    }
  )
}

function cloneMessage<METADATA>(message: UIMessage<METADATA>): UIMessage<METADATA> {
  return {
    ...message,
    parts: message.parts.map(clonePart),
  }
}

function clonePart(part: UIMessagePart): UIMessagePart {
  return { ...part } as UIMessagePart
}

function cloneDataPart(part: DataPart): DataPart {
  return { ...part }
}

function cloneToolPart(part: ToolPart): ToolPart {
  return { ...part }
}

function isDataChunk(
  chunk: UIMessageChunk,
): chunk is Extract<UIMessageChunk, { type: `data-${string}` }> {
  return chunk.type.startsWith('data-')
}

function isToolPart(part: UIMessagePart): part is ToolPart {
  return part.type.startsWith('tool-')
}
