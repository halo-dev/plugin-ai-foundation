import { describe, expect, it } from '@rstest/core'
import { Chat, createPlainChatState, lastAssistantMessageIsCompleteWithApprovalResponses } from './chat'
import { applyUIMessageChunk, createUIMessageReducer, messageText, validateUIMessageChunk } from './message-reducer'
import { readUIMessageSSEStream } from './stream'
import type { ChatTransport, SendMessagesOptions, UIMessageChunk } from './types'

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
      { type: 'tool-search', toolCallId: 'call-1', toolName: 'search', state: 'input-available', input: { q: 'Halo' } },
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
