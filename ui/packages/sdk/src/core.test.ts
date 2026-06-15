import { describe, expect, it } from '@rstest/core'
import { Chat, createPlainChatState, lastAssistantMessageIsCompleteWithApprovalResponses } from './chat'
import { AIUISchemaValidationError } from './errors'
import { applyUIMessageChunk, createUIMessageReducer, messageText, validateUIMessageChunk } from './message-reducer'
import { AIUIMessageValidationError, assertValidUIMessages, pruneMessages, validateUIMessages } from './persistence'
import { readUIMessageSSEStream } from './stream'
import { readUIMessageStream } from './stream-reader'
import type { SchemaLike } from './schema'
import type { ChatTransport, SendMessagesOptions, UIMessage, UIMessageChunk } from './types'

describe('Halo UI message stream parser', () => {
  it('reads SSE JSON chunks and skips done marker', async () => {
    const stream = streamFromText(
      'data: {"type":"start","messageId":"a"}\n\n'
        + 'data: {"type":"text-delta","id":"t","delta":"Hi"}\n\n'
        + 'data: [DONE]\n\n'
    )

    const chunks = []
    for await (const chunk of readUIMessageSSEStream(stream)) {
      chunks.push(chunk)
    }

    expect(chunks).toEqual([
      { type: 'start', messageId: 'a' },
      { type: 'text-delta', id: 't', delta: 'Hi' },
    ])
  })

  it('throws on invalid chunk JSON', async () => {
    const stream = streamFromText('data: {"type"\n\n')
    await expect(async () => {
      for await (const _chunk of readUIMessageSSEStream(stream)) {
        // consume
      }
    }).rejects.toThrow('Failed to parse Halo UI message stream chunk.')
  })
})

describe('UI message reducer', () => {
  it('aggregates text, reasoning, data, tool and terminal chunks', () => {
    const state = createUIMessageReducer({ messageId: 'assistant-1' })

    for (const chunk of [
      { type: 'text-start', id: 'text-1' },
      { type: 'text-delta', id: 'text-1', delta: 'Hello' },
      { type: 'reasoning-delta', id: 'reasoning-1', delta: 'Thinking' },
      { type: 'data-status', id: 'status', name: 'status', data: 'done' },
      { type: 'tool-search', toolCallId: 'call-1', toolName: 'search', state: 'input-available', input: { q: 'Halo' } },
      { type: 'finish', finishReason: 'stop' },
    ] satisfies UIMessageChunk[]) {
      applyUIMessageChunk(state, chunk)
    }

    expect(messageText(state.message)).toBe('Hello')
    expect(state.message.parts).toContainEqual({ type: 'reasoning', id: 'reasoning-1', text: 'Thinking' })
    expect(state.message.parts).toContainEqual({ type: 'data-status', id: 'status', name: 'status', data: 'done', transientData: false })
    expect(state.terminal.finishReason).toBe('stop')
  })

  it('reduces canonical tool chunks and keeps start-step lifecycle-only', () => {
    const state = createUIMessageReducer({ messageId: 'assistant-1' })

    for (const chunk of [
      { type: 'start-step', stepIndex: 0 },
      { type: 'tool-input-start', toolCallId: 'call-1', toolName: 'search' },
      { type: 'tool-input-delta', toolCallId: 'call-1', toolName: 'search', inputTextDelta: '{"q"' },
      { type: 'tool-input-delta', toolCallId: 'call-1', toolName: 'search', inputTextDelta: ':"Halo"}' },
      { type: 'tool-input-available', toolCallId: 'call-1', toolName: 'search', input: { q: 'Halo' }, providerMetadata: { provider: 'test' } },
      { type: 'tool-output-available', toolCallId: 'call-1', toolName: 'search', output: { ok: true } },
      { type: 'tool-approval-request', approvalId: 'approval-1', toolCallId: 'call-2', toolName: 'pay', input: { amount: 100 } },
      { type: 'tool-approval-response', approvalId: 'approval-1', toolCallId: 'call-2', toolName: 'pay', approved: false, reason: 'Denied' },
      { type: 'tool-output-error', toolCallId: 'call-3', toolName: 'lookup', errorText: 'failed' },
    ] satisfies UIMessageChunk[]) {
      applyUIMessageChunk(state, chunk)
    }

    expect(state.message.parts).toEqual([
      {
        type: 'tool-search',
        toolCallId: 'call-1',
        toolName: 'search',
        state: 'output-available',
        input: { q: 'Halo' },
        inputText: undefined,
        output: { ok: true },
        errorText: undefined,
        approval: undefined,
        providerMetadata: { provider: 'test' },
      },
      {
        type: 'tool-pay',
        toolCallId: 'call-2',
        toolName: 'pay',
        state: 'approval-responded',
        input: { amount: 100 },
        inputText: undefined,
        output: undefined,
        errorText: undefined,
        approval: { id: 'approval-1', approved: false, reason: 'Denied' },
        providerMetadata: undefined,
      },
      {
        type: 'tool-lookup',
        toolCallId: 'call-3',
        toolName: 'lookup',
        state: 'output-error',
        input: undefined,
        inputText: undefined,
        output: undefined,
        errorText: 'failed',
        approval: undefined,
        providerMetadata: undefined,
      },
    ])
  })

  it('validates dynamic data and tool chunk protocol', () => {
    expect(() =>
      validateUIMessageChunk({
        type: 'tool-delete-file',
        toolCallId: 'call-1',
        toolName: 'delete-file',
        state: 'input-available',
      })
    ).not.toThrow()
    expect(() =>
      validateUIMessageChunk({
        type: 'data-post-draft',
        id: 'data-1',
        name: 'post-draft',
        data: {},
      })
    ).not.toThrow()
    expect(() =>
      validateUIMessageChunk({
        type: 'tool-search',
        toolCallId: 'call-1',
        toolName: 'delete-file',
        state: 'input-available',
      })
    ).toThrow('Tool chunk type must be tool-delete-file')
    expect(() =>
      validateUIMessageChunk({
        type: 'tool-search',
        toolCallId: 'call-1',
        toolName: 'search',
        state: 'output-error',
      })
    ).toThrow('Tool output-error chunk errorText is required')
    expect(() =>
      validateUIMessageChunk({
        type: 'tool-input-available',
        toolCallId: 'call-1',
        toolName: 'search',
        input: { q: 'Halo' },
      })
    ).not.toThrow()
    expect(() =>
      validateUIMessageChunk({
        type: 'tool-approval-response',
        approvalId: 'approval-1',
        toolCallId: 'call-1',
        toolName: 'search',
      } as unknown as UIMessageChunk)
    ).toThrow('Tool approval-response chunk approved is required')
  })

  it('parses streamed metadata and persistent data with configured schemas', () => {
    const state = createUIMessageReducer<{ stage: string; seen: boolean }>({
      messageId: 'assistant-1',
      messageMetadataSchema: {
        safeParse: (value) => ({
          success: true,
          data: { ...(value as Record<string, unknown>), seen: true } as { stage: string; seen: boolean },
        }),
      },
      dataPartSchemas: {
        status: {
          '~standard': {
            validate: (value) => ({
              value: { ...(value as Record<string, unknown>), parsed: true },
            }),
          },
        },
      },
    })

    applyUIMessageChunk(state, { type: 'start', messageMetadata: { stage: 'start' } })
    applyUIMessageChunk(state, { type: 'message-metadata', messageMetadata: { stage: 'streaming' } })
    applyUIMessageChunk(state, {
      type: 'data-status',
      id: 'status-1',
      name: 'status',
      data: { value: 'loading' },
    })
    applyUIMessageChunk(state, {
      type: 'data-status',
      id: 'status-2',
      name: 'status',
      data: { value: 'transient' },
      transient: true,
    })

    expect(state.message.metadata).toEqual({ stage: 'streaming', seen: true })
    expect(state.message.parts).toContainEqual({
      type: 'data-status',
      id: 'status-1',
      name: 'status',
      data: { value: 'loading', parsed: true },
      transientData: false,
    })
    expect(state.message.parts).not.toContainEqual(expect.objectContaining({ id: 'status-2' }))
  })

  it('rejects async schemas and includes data part error details', () => {
    const state = createUIMessageReducer({
      dataPartSchemas: {
        status: {
          '~standard': {
            validate: async () => ({ value: { ok: true } }),
          },
        } as SchemaLike,
      },
    })

    expect(() =>
      applyUIMessageChunk(state, {
        type: 'data-status',
        id: 'status-1',
        name: 'status',
        data: { value: 'loading' },
      })
    ).toThrow(AIUISchemaValidationError)
    try {
      applyUIMessageChunk(state, {
        type: 'data-status',
        id: 'status-1',
        name: 'status',
        data: { value: 'loading' },
      })
    } catch (error) {
      expect(error).toBeInstanceOf(AIUISchemaValidationError)
      expect(error).toMatchObject({
        target: 'data-part',
        partType: 'data-status',
        partName: 'status',
        partId: 'status-1',
      })
      expect((error as Error).message).toContain('Async schemas are not supported')
    }
  })
})

describe('UI message stream reader', () => {
  it('reads async chunks into a message result and visible message snapshots', async () => {
    const chunks: UIMessageChunk[] = [
      { type: 'start', messageId: 'assistant-1', messageMetadata: { traceId: 'trace-1' } },
      { type: 'message-metadata', messageMetadata: { stage: 'streaming' } },
      { type: 'text-delta', id: 'text-1', delta: 'Hi' },
      { type: 'finish', finishReason: 'stop' },
    ]
    const rawChunks: UIMessageChunk[] = []
    const messages: UIMessage[] = []

    const result = await readUIMessageStream({
      stream: chunksToAsyncIterable(chunks),
      metadata: { initial: true },
      onChunk: (chunk) => {
        rawChunks.push(chunk)
      },
      onMessage: (message) => {
        messages.push(message)
        message.parts.push({ type: 'text', id: 'mutated', text: 'ignored' })
      },
    })

    expect(rawChunks).toEqual(chunks)
    expect(messages).toHaveLength(2)
    expect(result.status).toBe('ready')
    expect(result.isError).toBe(false)
    expect(result.message).toMatchObject({
      id: 'assistant-1',
      role: 'assistant',
      metadata: { initial: true, traceId: 'trace-1', stage: 'streaming' },
    })
    expect(messageText(result.message)).toBe('Hi')
    expect(result.message.parts).not.toContainEqual(expect.objectContaining({ id: 'mutated' }))
    expect(result.terminal.finishReason).toBe('stop')
  })

  it('reads readable SSE streams and response inputs', async () => {
    const readableResult = await readUIMessageStream({
      readableStream: streamFromText(
        'data: {"type":"text-delta","id":"text-1","delta":"Readable"}\n\n'
          + 'data: [DONE]\n\n'
      ),
      messageId: 'assistant-readable',
    })

    const response = new Response(
      streamFromText(
        'data: {"type":"text-delta","id":"text-1","delta":"Response"}\n\n'
          + 'data: [DONE]\n\n'
      ),
      { headers: { 'X-Halo-AI-UI-Message-Stream': 'v1' } }
    )
    const responseResult = await readUIMessageStream({
      response,
      messageId: 'assistant-response',
    })

    expect(readableResult.message.id).toBe('assistant-readable')
    expect(messageText(readableResult.message)).toBe('Readable')
    expect(responseResult.message.id).toBe('assistant-response')
    expect(messageText(responseResult.message)).toBe('Response')
  })

  it('uses existing messages and generates default message ids', async () => {
    const existing: UIMessage = {
      id: 'assistant-existing',
      role: 'assistant',
      metadata: { existing: true },
      parts: [{ type: 'text', id: 'text-1', text: 'Old ' }],
    }

    const existingResult = await readUIMessageStream({
      stream: chunksToAsyncIterable([{ type: 'text-delta', id: 'text-1', delta: 'New' }]),
      message: existing,
      messageId: 'ignored',
      metadata: { ignored: true },
    })
    const generatedResult = await readUIMessageStream({
      stream: chunksToAsyncIterable([]),
      generateId: () => 'assistant-generated',
    })

    expect(existingResult.message.id).toBe('assistant-existing')
    expect(existingResult.message.metadata).toEqual({ existing: true })
    expect(messageText(existingResult.message)).toBe('Old New')
    expect(generatedResult.message).toEqual({
      id: 'assistant-generated',
      role: 'assistant',
      parts: [],
      metadata: undefined,
    })
    expect(generatedResult.status).toBe('ready')
  })

  it('applies schemas, data callbacks, transient data and one-time tool callbacks', async () => {
    const dataEvents: unknown[] = []
    const toolCalls: unknown[] = []
    const chunks: UIMessageChunk[] = [
      { type: 'data-status', id: 'status-1', name: 'status', data: { value: 'loading' } },
      { type: 'data-status', id: 'status-2', name: 'status', data: { value: 'transient' }, transient: true },
      { type: 'start-step', stepIndex: 0 },
      { type: 'tool-input-start', toolCallId: 'call-1', toolName: 'search' },
      { type: 'tool-input-delta', toolCallId: 'call-1', toolName: 'search', inputTextDelta: '{"q"' },
      { type: 'tool-input-available', toolCallId: 'call-1', toolName: 'search', input: { q: 'Halo' } },
      { type: 'tool-input-available', toolCallId: 'call-1', toolName: 'search', input: { q: 'Halo' } },
    ]

    const result = await readUIMessageStream({
      stream: chunksToAsyncIterable(chunks),
      dataPartSchemas: {
        status: {
          safeParse: (value) => ({
            success: true,
            data: { ...(value as Record<string, unknown>), parsed: true },
          }),
        },
      },
      onData: (part) => {
        dataEvents.push({ ...part })
        part.data = 'mutated'
      },
      onToolCall: (part) => {
        toolCalls.push({ ...part })
        part.input = { mutated: true }
      },
    })

    expect(dataEvents).toEqual([
      {
        type: 'data-status',
        id: 'status-1',
        name: 'status',
        data: { value: 'loading', parsed: true },
        transientData: false,
      },
      {
        type: 'data-status',
        id: 'status-2',
        name: 'status',
        data: { value: 'transient' },
        transientData: true,
      },
    ])
    expect(toolCalls).toEqual([
      expect.objectContaining({
        type: 'tool-search',
        toolCallId: 'call-1',
        toolName: 'search',
        state: 'input-available',
        input: { q: 'Halo' },
      }),
    ])
    expect(result.message.parts).toContainEqual({
      type: 'data-status',
      id: 'status-1',
      name: 'status',
      data: { value: 'loading', parsed: true },
      transientData: false,
    })
    expect(result.message.parts).not.toContainEqual(expect.objectContaining({ id: 'status-2' }))
    expect(result.message.parts).toContainEqual(expect.objectContaining({
      type: 'tool-search',
      toolCallId: 'call-1',
      input: { q: 'Halo' },
    }))
  })

  it('returns error lifecycle results for schema failures without committing failing chunks', async () => {
    const errors: Error[] = []
    const finishes: unknown[] = []

    const result = await readUIMessageStream({
      stream: chunksToAsyncIterable([
        { type: 'text-delta', id: 'text-1', delta: 'Partial' },
        { type: 'data-status', id: 'status-1', name: 'status', data: undefined },
      ]),
      dataPartSchemas: {
        status: {
          safeParse: () => ({ success: false, error: { message: 'status is required' } }),
        },
      },
      onError: (error) => {
        errors.push(error)
      },
      onFinish: (event) => {
        finishes.push({ status: event.status, isError: event.isError })
      },
    })

    expect(result.status).toBe('error')
    expect(result.isError).toBe(true)
    expect(result.error).toBeInstanceOf(AIUISchemaValidationError)
    expect(errors).toEqual([result.error])
    expect(finishes).toEqual([{ status: 'error', isError: true }])
    expect(messageText(result.message)).toBe('Partial')
    expect(result.message.parts).not.toContainEqual(expect.objectContaining({ id: 'status-1' }))
  })

  it('returns disconnected after accepted chunks fail with non-protocol errors', async () => {
    const expected = new Error('network interrupted')
    const result = await readUIMessageStream({
      stream: asyncGeneratorWithFailure([
        { type: 'text-delta', id: 'text-1', delta: 'Partial' },
      ], expected),
    })

    expect(result.status).toBe('disconnected')
    expect(result.isError).toBe(false)
    expect(result.error).toBe(expected)
    expect(messageText(result.message)).toBe('Partial')
  })

  it('returns aborted when the caller aborts the signal', async () => {
    const controller = new AbortController()
    const promise = readUIMessageStream({
      stream: asyncGeneratorUntilAbort(controller.signal),
      abortSignal: controller.signal,
      messageId: 'assistant-1',
    })
    controller.abort()

    const result = await promise

    expect(result.status).toBe('aborted')
    expect(result.isAbort).toBe(true)
  })

  it('calls finish before throwing when throwOnError is enabled', async () => {
    const finishes: unknown[] = []
    await expect(
      readUIMessageStream({
        stream: chunksToAsyncIterable([
          {
            type: 'tool-search',
            toolCallId: 'call-1',
            toolName: 'search',
          } as unknown as UIMessageChunk,
        ]),
        throwOnError: true,
        onFinish: (event) => {
          finishes.push({ status: event.status, isError: event.isError })
        },
      })
    ).rejects.toThrow('Tool chunk state is required')

    expect(finishes).toEqual([{ status: 'error', isError: true }])
  })

  it('treats callback failures as reader errors', async () => {
    const expected = new Error('consumer failed')
    const result = await readUIMessageStream({
      stream: chunksToAsyncIterable([
        { type: 'text-delta', id: 'text-1', delta: 'Partial' },
      ]),
      onMessage: () => {
        throw expected
      },
    })

    expect(result.status).toBe('error')
    expect(result.error).toBe(expected)
    expect(messageText(result.message)).toBe('Partial')
  })

  it('throws onError and onFinish failures after lifecycle callbacks', async () => {
    const onErrorFailure = new Error('onError failed')
    await expect(
      readUIMessageStream({
        stream: chunksToAsyncIterable([
          {
            type: 'tool-search',
            toolCallId: 'call-1',
            toolName: 'search',
          } as unknown as UIMessageChunk,
        ]),
        onError: () => {
          throw onErrorFailure
        },
      })
    ).rejects.toThrow(onErrorFailure)

    const onFinishFailure = new Error('onFinish failed')
    await expect(
      readUIMessageStream({
        stream: chunksToAsyncIterable([]),
        onFinish: () => {
          throw onFinishFailure
        },
      })
    ).rejects.toThrow(onFinishFailure)
  })

  it('stores error chunks in terminal state without throwing', async () => {
    const result = await readUIMessageStream({
      stream: chunksToAsyncIterable([
        { type: 'error', errorText: 'model failed' },
      ]),
    })

    expect(result.status).toBe('ready')
    expect(result.terminal.errorText).toBe('model failed')
    expect(result.error).toBeUndefined()
  })

  it('rejects response inputs without bodies and unsupported protocol versions', async () => {
    await expect(
      readUIMessageStream({
        response: new Response(null, {
          headers: { 'X-Halo-AI-UI-Message-Stream': 'v1' },
        }),
        throwOnError: true,
      })
    ).rejects.toThrow('The response body is empty')

    await expect(
      readUIMessageStream({
        response: new Response(streamFromText(''), {
          headers: { 'X-Halo-AI-UI-Message-Stream': 'v2' },
        }),
        throwOnError: true,
      })
    ).rejects.toThrow('Unsupported Halo UI message stream version: v2')
  })
})

describe('UI message persistence helpers', () => {
  it('prunes newest messages and removes pending tool parts by default', () => {
    const messages: UIMessage[] = [
      {
        id: 'old',
        role: 'user',
        parts: [{ type: 'text', id: 'text-old', text: 'old' }],
      },
      {
        id: 'assistant-1',
        role: 'assistant',
        parts: [
          { type: 'tool-search', toolCallId: 'pending', toolName: 'search', state: 'input-available', input: { q: 'Halo' } },
          { type: 'tool-pay', toolCallId: 'approval', toolName: 'pay', state: 'approval-requested', approval: { id: 'approval-1' } },
        ],
      },
      {
        id: 'assistant-2',
        role: 'assistant',
        parts: [
          { type: 'text', id: 'text-1', text: 'answer' },
          { type: 'tool-search', toolCallId: 'done', toolName: 'search', state: 'output-available', output: { ok: true } },
          { type: 'tool-pay', toolCallId: 'denied', toolName: 'pay', state: 'approval-responded', approval: { id: 'approval-2', approved: false } },
        ],
      },
    ]

    expect(pruneMessages(messages, { maxMessages: 2 })).toEqual([
      {
        id: 'assistant-2',
        role: 'assistant',
        parts: [
          { type: 'text', id: 'text-1', text: 'answer' },
          { type: 'tool-search', toolCallId: 'done', toolName: 'search', state: 'output-available', output: { ok: true } },
          { type: 'tool-pay', toolCallId: 'denied', toolName: 'pay', state: 'approval-responded', approval: { id: 'approval-2', approved: false } },
        ],
      },
    ])
  })

  it('validates message shape and returns stable issue paths and codes', () => {
    const issues = validateUIMessages([
      {
        id: '',
        role: 'assistant',
        parts: [
          { type: 'data-status', id: '', name: 'other', data: 'bad' },
          { type: 'tool-search', toolCallId: 'call-1', toolName: 'lookup', state: 'output-error' },
        ],
      } as UIMessage,
    ])

    expect(issues).toEqual([
      { path: '$[0].id', code: 'message.id.required', message: 'UI message id is required.' },
      { path: '$[0].parts[0].id', code: 'part.data.id.required', message: 'Data part id is required.' },
      { path: '$[0].parts[0].type', code: 'part.data.type.invalid', message: 'Data part type must be data-other.' },
      { path: '$[0].parts[1].type', code: 'part.tool.type.invalid', message: 'Tool part type must be tool-lookup.' },
      { path: '$[0].parts[1].errorText', code: 'part.tool.error.required', message: 'Tool output-error part errorText is required.' },
    ])
  })

  it('asserts validation failures with a public error type', () => {
    expect(() =>
      assertValidUIMessages([
        { id: 'assistant-1', role: 'assistant', parts: [{ type: 'unknown' } as never] },
      ])
    ).toThrow(AIUIMessageValidationError)
  })

  it('uses metadata and data schemas during validation', () => {
    const valid = validateUIMessages(
      [
        {
          id: 'assistant-1',
          role: 'assistant',
          metadata: { stage: 'done' },
          parts: [{ type: 'data-status', id: 'status-1', name: 'status', data: { value: 'ok' } }],
        },
      ],
      {
        messageMetadataSchema: {
          safeParse: () => ({ success: true, data: { stage: 'done' } }),
        },
        dataPartSchemas: {
          status: {
            safeParse: () => ({ success: true, data: { value: 'ok' } }),
          },
        },
      }
    )
    const invalid = validateUIMessages(
      [
        {
          id: 'assistant-1',
          role: 'assistant',
          metadata: { stage: 'done' },
          parts: [{ type: 'data-status', id: 'status-1', name: 'status', data: { value: 'bad' } }],
        },
      ],
      {
        messageMetadataSchema: {
          safeParse: () => ({ success: false, error: { message: 'bad metadata' } }),
        },
        dataPartSchemas: {
          status: {
            safeParse: () => ({ success: false, error: { message: 'bad data' } }),
          },
        },
      }
    )

    expect(valid).toEqual([])
    expect(invalid).toEqual([
      {
        path: '$[0].metadata',
        code: 'message.metadata.schema',
        message: 'UI message message-metadata validation failed: bad metadata',
      },
      {
        path: '$[0].parts[0].data',
        code: 'part.data.schema',
        message: 'UI message data-part validation failed: bad data',
      },
    ])
  })
})

describe('Chat', () => {
  it('sends Halo chat request shape and appends streamed assistant response', async () => {
    const transport = new RecordingTransport([
      { type: 'start', messageId: 'assistant-1' },
      { type: 'text-delta', id: 'text-1', delta: 'Hi' },
      { type: 'finish', finishReason: 'stop' },
    ])
    const state = createPlainChatState()
    const chat = new Chat({ id: 'chat-1', state, transport, generateId: () => 'generated-id' })

    await chat.sendMessage({ text: 'Hello' })

    expect(transport.options?.chatId).toBe('chat-1')
    expect(transport.options?.trigger).toBe('submit-message')
    expect(transport.options?.messages).toHaveLength(1)
    expect(chat.messages).toHaveLength(2)
    expect(messageText(chat.messages[1])).toBe('Hi')
  })

  it('creates user text and file parts from sendMessage convenience input', async () => {
    const transport = new RecordingTransport([])
    const chat = new Chat({ id: 'chat-1', transport })

    await chat.sendMessage({
      text: 'Read this',
      files: [
        { id: 'file-1', url: 'https://example.com/report.pdf', mediaType: 'application/pdf', title: 'Report' },
      ],
    })

    expect(transport.options?.messages[0].parts).toEqual([
      expect.objectContaining({ type: 'text', text: 'Read this' }),
      {
        type: 'file',
        id: 'file-1',
        url: 'https://example.com/report.pdf',
        mediaType: 'application/pdf',
        title: 'Report',
        data: undefined,
        providerMetadata: undefined,
      },
    ])
  })

  it('uses explicit message parts instead of synthesizing text and file parts', async () => {
    const transport = new RecordingTransport([])
    const chat = new Chat({ id: 'chat-1', transport })

    await chat.sendMessage({
      text: 'Ignored',
      files: [{ id: 'file-1', url: 'https://example.com/report.pdf' }],
      parts: [{ type: 'text', id: 'explicit-text', text: 'Explicit' }],
    })

    expect(transport.options?.messages[0].parts).toEqual([
      { type: 'text', id: 'explicit-text', text: 'Explicit' },
    ])
  })

  it('fires data and tool callbacks from streamed chunks', async () => {
    const dataEvents: unknown[] = []
    const toolCalls: unknown[] = []
    const transport = new RecordingTransport([
      { type: 'data-status', id: 'status-1', name: 'status', data: { value: 'loading' }, transient: true },
      { type: 'tool-input-start', toolCallId: 'call-1', toolName: 'search' },
      { type: 'tool-input-delta', toolCallId: 'call-1', toolName: 'search', inputTextDelta: '{"q":"Halo"}' },
      { type: 'tool-input-available', toolCallId: 'call-1', toolName: 'search', input: { q: 'Halo' } },
    ])
    const chat = new Chat({
      id: 'chat-1',
      transport,
      onData: (part) => dataEvents.push(part),
      onToolCall: (part) => toolCalls.push(part),
    })

    await chat.sendMessage({ text: 'Hello' })

    expect(dataEvents).toEqual([
      { type: 'data-status', id: 'status-1', name: 'status', data: { value: 'loading' }, transientData: true },
    ])
    expect(toolCalls).toEqual([
      expect.objectContaining({ type: 'tool-search', toolCallId: 'call-1', toolName: 'search', state: 'input-available' }),
    ])
  })

  it('stores parsed persistent data and sends parsed data to onData', async () => {
    const dataEvents: unknown[] = []
    const transport = new RecordingTransport([
      { type: 'data-status', id: 'status-1', name: 'status', data: { value: 'loading' } },
    ])
    const chat = new Chat({
      id: 'chat-1',
      transport,
      dataPartSchemas: {
        status: {
          safeParse: () => ({ success: true, data: { value: 'parsed' } }),
        },
      },
      onData: (part) => dataEvents.push(part),
    })

    await chat.sendMessage({ text: 'Hello' })

    expect(chat.messages[1].parts).toContainEqual({
      type: 'data-status',
      id: 'status-1',
      name: 'status',
      data: { value: 'parsed' },
      transientData: false,
    })
    expect(dataEvents).toEqual([
      {
        type: 'data-status',
        id: 'status-1',
        name: 'status',
        data: { value: 'parsed' },
        transientData: false,
      },
    ])
  })

  it('regenerates by truncating the target assistant message', async () => {
    const transport = new RecordingTransport([{ type: 'text-delta', id: 'new', delta: 'New' }])
    const state = createPlainChatState({
      messages: [
        { id: 'user-1', role: 'user', parts: [{ type: 'text', id: 'text-1', text: 'Hello' }] },
        { id: 'assistant-1', role: 'assistant', parts: [{ type: 'text', id: 'text-2', text: 'Old' }] },
      ],
    })
    const chat = new Chat({ id: 'chat-1', state, transport, generateId: () => 'assistant-2' })

    await chat.regenerate({ messageId: 'assistant-1' })

    expect(transport.options?.trigger).toBe('regenerate-message')
    expect(transport.options?.messages.map((message) => message.id)).toEqual([
      'user-1',
      'assistant-1',
    ])
    expect(messageText(chat.messages[1])).toBe('New')
  })

  it('appends tool result and auto submits when configured', async () => {
    const transport = new RecordingTransport([])
    const chat = new Chat({
      id: 'chat-1',
      state: createPlainChatState({
        messages: [
          { id: 'assistant-1', role: 'assistant', parts: [{ type: 'tool-search-web', toolCallId: 'call-1', toolName: 'search-web', state: 'input-available' }] },
        ],
      }),
      transport,
      sendAutomaticallyWhen: () => true,
    })

    await chat.addToolOutput({ toolCallId: 'call-1', toolName: 'search-web', output: { ok: true } })

    expect(chat.messages[0].parts).toContainEqual(expect.objectContaining({
      type: 'tool-search-web',
      toolCallId: 'call-1',
      toolName: 'search-web',
      state: 'output-available',
      output: { ok: true },
    }))
    expect(transport.options?.trigger).toBe('submit-message')
  })

  it('adds tool output and approval response by resolving tool metadata from messages', async () => {
    const chat = new Chat({
      id: 'chat-1',
      state: createPlainChatState({
        messages: [
          {
            id: 'assistant-1',
            role: 'assistant',
            parts: [
              { type: 'tool-search-web', toolCallId: 'call-1', toolName: 'search-web', state: 'input-available' },
              {
                type: 'tool-delete_file',
                toolCallId: 'call-2',
                toolName: 'delete_file',
                state: 'approval-requested',
                approval: { id: 'approval-1' },
              },
            ],
          },
        ],
      }),
    })

    await chat.addToolOutput({ toolCallId: 'call-1', output: { ok: true } })
    await chat.rejectToolCall({ id: 'approval-1', reason: 'Denied' })

    expect(chat.messages[0].parts).toContainEqual(expect.objectContaining({
      type: 'tool-search-web',
      toolCallId: 'call-1',
      toolName: 'search-web',
      state: 'output-available',
      output: { ok: true },
    }))
    expect(chat.messages[0].parts).toContainEqual(expect.objectContaining({
      type: 'tool-delete_file',
      toolCallId: 'call-2',
      toolName: 'delete_file',
      state: 'approval-responded',
      approval: { id: 'approval-1', approved: false, reason: 'Denied' },
    }))
  })

  it('adds approved tool approval response and auto submits from existing assistant state', async () => {
    const transport = new RecordingTransport([
      { type: 'tool-delete_file', toolCallId: 'call-2', toolName: 'delete_file', state: 'output-available', output: { ok: true } },
    ])
    const chat = new Chat({
      id: 'chat-1',
      state: createPlainChatState({
        messages: [
          {
            id: 'assistant-1',
            role: 'assistant',
            parts: [
              {
                type: 'tool-delete_file',
                toolCallId: 'call-2',
                toolName: 'delete_file',
                state: 'approval-requested',
                input: { path: '/tmp/a.txt' },
                approval: { id: 'approval-1' },
              },
            ],
          },
        ],
      }),
      transport,
      sendAutomaticallyWhen: lastAssistantMessageIsCompleteWithApprovalResponses,
    })

    await chat.addToolApprovalResponse({ id: 'approval-1', approved: true, reason: 'OK' })

    expect(transport.options?.trigger).toBe('submit-message')
    expect(chat.messages[0].parts).toContainEqual(expect.objectContaining({
      type: 'tool-delete_file',
      toolCallId: 'call-2',
      toolName: 'delete_file',
      state: 'output-available',
      input: { path: '/tmp/a.txt' },
      output: { ok: true },
      approval: { id: 'approval-1', approved: true, reason: 'OK' },
    }))
  })

  it('detects approval response completion separately from tool output completion', () => {
    expect(lastAssistantMessageIsCompleteWithApprovalResponses({
      messages: [
        {
          id: 'assistant-1',
          role: 'assistant',
          parts: [
            { type: 'tool-delete_file', toolCallId: 'call-1', toolName: 'delete_file', state: 'approval-responded', approval: { id: 'approval-1', approved: false } },
          ],
        },
      ],
    })).toBe(true)

    expect(lastAssistantMessageIsCompleteWithApprovalResponses({
      messages: [
        {
          id: 'assistant-1',
          role: 'assistant',
          parts: [
            { type: 'tool-delete_file', toolCallId: 'call-1', toolName: 'delete_file', state: 'approval-requested', approval: { id: 'approval-1' } },
          ],
        },
      ],
    })).toBe(false)
  })

  it('reports whether the last assistant tool parts are complete', async () => {
    const chat = new Chat({
      messages: [
        {
          id: 'assistant-1',
          role: 'assistant',
          parts: [{ type: 'tool-search', toolCallId: 'call-1', toolName: 'search', state: 'input-available' }],
        },
      ],
    })

    expect(chat.isLastAssistantMessageToolComplete()).toBe(false)
    await chat.addToolOutput({ toolCallId: 'call-1', output: { ok: true } })
    expect(chat.isLastAssistantMessageToolComplete()).toBe(true)
  })

  it('aborts active response and keeps partial message', async () => {
    const transport: ChatTransport = {
      sendMessages: async (options) => asyncGeneratorUntilAbort(options.abortSignal),
    }
    const chat = new Chat({ id: 'chat-1', transport, generateId: () => 'assistant-1' })
    const promise = chat.sendMessage({ text: 'Hello' })
    await waitFor(() => chat.messages.length === 2)
    chat.stop()
    await promise

    expect(chat.status).toBe('ready')
    expect(chat.messages).toHaveLength(2)
  })

  it('marks pre-stream failures as errors', async () => {
    const expected = new Error('request failed')
    const transport: ChatTransport = {
      sendMessages: async () => {
        throw expected
      },
    }
    const errors: Error[] = []
    const chat = new Chat({ id: 'chat-1', transport, onError: (error) => errors.push(error) })

    await chat.sendMessage({ text: 'Hello' })

    expect(chat.status).toBe('error')
    expect(chat.error).toBe(expected)
    expect(errors).toEqual([expected])
  })

  it('marks readable stream interruptions after valid chunks as disconnected', async () => {
    const transport: ChatTransport = {
      sendMessages: async () => asyncGeneratorWithFailure([
        { type: 'text-delta', id: 'text-1', delta: 'Partial' },
      ], new Error('network interrupted')),
    }
    const chat = new Chat({ id: 'chat-1', transport, generateId: () => 'assistant-1' })

    await chat.sendMessage({ text: 'Hello' })

    expect(chat.status).toBe('disconnected')
    expect(chat.error?.message).toBe('network interrupted')
    expect(messageText(chat.messages[1])).toBe('Partial')
  })

  it('keeps protocol failures as errors after the stream started', async () => {
    const transport: ChatTransport = {
      sendMessages: async () => chunksToAsyncIterable([
        { type: 'text-delta', id: 'text-1', delta: 'Partial' },
        {
          type: 'tool-search',
          toolCallId: 'call-1',
          toolName: 'search',
        } as unknown as UIMessageChunk,
      ]),
    }
    const chat = new Chat({ id: 'chat-1', transport, generateId: () => 'assistant-1' })

    await chat.sendMessage({ text: 'Hello' })

    expect(chat.status).toBe('error')
    expect(chat.error?.name).toBe('AIUIProtocolError')
    expect(chat.error?.message).toBe('Tool chunk state is required.')
  })

  it('aborts active request and reports schema failures through error callbacks and finish', async () => {
    let signal: AbortSignal | undefined
    const errors: Error[] = []
    const finishes: Array<{ isError: boolean; messageParts: number }> = []
    const transport: ChatTransport = {
      sendMessages: async (options) => {
        signal = options.abortSignal
        return chunksToAsyncIterable([
          { type: 'text-delta', id: 'text-1', delta: 'Partial' },
          { type: 'data-status', id: 'status-1', name: 'status', data: undefined },
        ])
      },
    }
    const chat = new Chat({
      id: 'chat-1',
      transport,
      generateId: () => 'assistant-1',
      dataPartSchemas: {
        status: {
          safeParse: () => ({ success: false, error: { message: 'status is required' } }),
        },
      },
      onError: (error) => errors.push(error),
      onFinish: ({ isError, message }) => {
        finishes.push({ isError, messageParts: message.parts.length })
      },
    })

    await chat.sendMessage({ text: 'Hello' })

    expect(signal?.aborted).toBe(true)
    expect(chat.status).toBe('error')
    expect(chat.error).toBeInstanceOf(AIUISchemaValidationError)
    expect(chat.error).toMatchObject({ target: 'data-part', partName: 'status' })
    expect(errors).toEqual([chat.error])
    expect(finishes).toEqual([{ isError: true, messageParts: 1 }])
    expect(chat.messages[1].parts).toEqual([{ type: 'text', id: 'text-1', text: 'Partial' }])
  })

  it('reports terminal error chunks through chat error state and lifecycle callbacks', async () => {
    const errors: Error[] = []
    const finishes: Array<{ isError: boolean; errorText?: string; messageParts: number }> = []
    const chat = new Chat({
      id: 'chat-1',
      transport: new RecordingTransport([{ type: 'error', errorText: 'model failed' }]),
      generateId: () => 'assistant-1',
      onError: (error) => errors.push(error),
      onFinish: ({ isError, terminal, message }) => {
        finishes.push({
          isError,
          errorText: terminal.errorText,
          messageParts: message.parts.length,
        })
      },
    })

    await chat.sendMessage({ text: 'Hello' })

    expect(chat.status).toBe('error')
    expect(chat.error?.message).toBe('model failed')
    expect(errors).toEqual([chat.error])
    expect(finishes).toEqual([{ isError: true, errorText: 'model failed', messageParts: 0 }])
    expect(chat.messages).toContainEqual({ id: 'assistant-1', role: 'assistant', parts: [] })
  })

})

class RecordingTransport implements ChatTransport {
  options?: SendMessagesOptions

  constructor(private readonly chunks: UIMessageChunk[]) {}

  async sendMessages(options: SendMessagesOptions): Promise<AsyncIterable<UIMessageChunk>> {
    this.options = options
    return chunksToAsyncIterable(this.chunks)
  }
}

function streamFromText(text: string): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    },
  })
}

async function* asyncGeneratorUntilAbort(signal?: AbortSignal): AsyncIterable<UIMessageChunk> {
  yield { type: 'text-delta', id: 'text-1', delta: 'Partial' }
  await new Promise<void>((_resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Aborted', 'AbortError')),
      { once: true }
    )
  })
}

async function* chunksToAsyncIterable(chunks: UIMessageChunk[]): AsyncIterable<UIMessageChunk> {
  yield* chunks
}

async function* asyncGeneratorWithFailure(
  chunks: UIMessageChunk[],
  error: Error
): AsyncIterable<UIMessageChunk> {
  yield* chunks
  throw error
}

async function waitFor(predicate: () => boolean): Promise<void> {
  const started = Date.now()
  while (!predicate()) {
    if (Date.now() - started > 1000) {
      throw new Error('timed out waiting for condition')
    }
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}
