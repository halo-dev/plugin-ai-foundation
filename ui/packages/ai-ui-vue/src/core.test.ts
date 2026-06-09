import { describe, expect, it } from '@rstest/core'
import { Chat, createPlainChatState } from './chat'
import { applyUIMessageChunk, createUIMessageReducer, messageText } from './message-reducer'
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
      { type: 'data', name: 'status', data: 'done' },
      { type: 'tool-call', toolCallId: 'call-1', toolName: 'search', input: { q: 'Halo' } },
      { type: 'finish', finishReason: 'stop' },
    ] satisfies UIMessageChunk[]) {
      applyUIMessageChunk(state, chunk)
    }

    expect(messageText(state.message)).toBe('Hello')
    expect(state.message.parts).toContainEqual({ type: 'reasoning', id: 'reasoning-1', text: 'Thinking' })
    expect(state.message.parts).toContainEqual({ type: 'data', name: 'status', data: 'done', transientData: false })
    expect(state.terminal.finishReason).toBe('stop')
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
          { id: 'assistant-1', role: 'assistant', parts: [{ type: 'tool-call', toolCallId: 'call-1', toolName: 'search' }] },
        ],
      }),
      transport,
      sendAutomaticallyWhen: () => true,
    })

    await chat.addToolResult({ toolCallId: 'call-1', toolName: 'search', result: { ok: true } })

    expect(chat.messages[0].parts).toContainEqual({
      type: 'tool-result',
      toolCallId: 'call-1',
      toolName: 'search',
      result: { ok: true },
    })
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
              { type: 'tool-call', toolCallId: 'call-1', toolName: 'search' },
              {
                type: 'tool-approval-request',
                approvalId: 'approval-1',
                toolCallId: 'call-2',
                toolName: 'delete_file',
              },
            ],
          },
        ],
      }),
    })

    await chat.addToolOutput({ toolCallId: 'call-1', output: { ok: true } })
    await chat.addToolApprovalResponse({ id: 'approval-1', approved: false, reason: 'Denied' })

    expect(chat.messages[0].parts).toContainEqual({
      type: 'tool-result',
      toolCallId: 'call-1',
      toolName: 'search',
      result: { ok: true },
    })
    expect(chat.messages[0].parts).toContainEqual({
      type: 'tool-approval-response',
      approvalId: 'approval-1',
      toolCallId: 'call-2',
      toolName: 'delete_file',
      approved: false,
      reason: 'Denied',
    })
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

async function waitFor(predicate: () => boolean): Promise<void> {
  const started = Date.now()
  while (!predicate()) {
    if (Date.now() - started > 1000) {
      throw new Error('timed out waiting for condition')
    }
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}
