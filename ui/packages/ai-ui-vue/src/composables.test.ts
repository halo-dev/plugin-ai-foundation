import { describe, expect, it } from '@rstest/core'
import { effectScope, nextTick } from 'vue'
import { Chat } from './chat'
import { fromOpenAPIRequestArgs } from './openapi'
import { DefaultChatTransport } from './transports'
import { useChat } from './use-chat'
import { useCompletion } from './use-completion'
import { experimental_useObject } from './use-object'

describe('useChat', () => {
  it('shares state by id', async () => {
    const fetch = async () =>
      new Response(streamFromText('data: {"type":"text-delta","id":"t","delta":"Hello"}\n\ndata: [DONE]\n\n'), {
        headers: { 'X-Halo-AI-UI-Message-Stream': 'v1' },
      })

    const scope = effectScope()
    await scope.run(async () => {
      const first = useChat({ id: 'shared', transport: new DefaultChatTransport({ fetch }) })
      const second = useChat({ id: 'shared' })

      await first.sendMessage({ text: 'Hi' })
      await nextTick()

      expect(second.messages.value).toHaveLength(2)
    })
    scope.stop()
  })

  it('bridges an existing Chat instance', async () => {
    const chat = new Chat({
      id: 'external-chat',
      transport: new DefaultChatTransport({
        fetch: async () =>
          new Response(streamFromText('data: {"type":"text-delta","id":"t","delta":"Hello"}\n\ndata: [DONE]\n\n'), {
            headers: { 'X-Halo-AI-UI-Message-Stream': 'v1' },
          }),
      }),
    })

    const scope = effectScope()
    await scope.run(async () => {
      const composable = useChat({ chat })
      await composable.sendMessage({ text: 'Hi' })
      await nextTick()

      expect(composable.id).toBe('external-chat')
      expect(composable.messages.value).toHaveLength(2)
      expect(composable.status.value).toBe('ready')
    })
    scope.stop()
  })

  it('exposes tool approval response helper', async () => {
    const chat = new Chat({
      id: 'approval-chat',
      messages: [
        {
          id: 'assistant-1',
          role: 'assistant',
          parts: [
            {
              type: 'tool-delete_file',
              toolCallId: 'call-1',
              toolName: 'delete_file',
              state: 'approval-requested',
              approval: { id: 'approval-1' },
            },
          ],
        },
      ],
    })

    const scope = effectScope()
    await scope.run(async () => {
      const composable = useChat({ chat })
      await composable.addToolApprovalResponse({ id: 'approval-1', approved: false })
      await nextTick()

      expect(composable.messages.value![0].parts[0]).toMatchObject({
        state: 'approval-responded',
        approval: { id: 'approval-1', approved: false },
      })
    })
    scope.stop()
  })

  it('fails fast when an existing Chat is mixed with creation options', () => {
    const chat = new Chat({ id: 'external-chat' })
    const scope = effectScope()
    expect(() =>
      scope.run(() => {
        useChat({ chat, id: 'other-chat' })
      })
    ).toThrow('useChat({ chat }) cannot be mixed with creation options: id.')
    scope.stop()
  })
})

describe('useCompletion', () => {
  it('posts prompt and appends streamed text', async () => {
    let body: unknown
    const fetch = async (_input: RequestInfo | URL, init?: RequestInit) => {
      body = JSON.parse(String(init?.body))
      return new Response(streamFromText('Hello'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const completion = useCompletion({ id: 'completion-test', fetch })
      await completion.complete('Say hello')

      expect(body).toEqual({ prompt: 'Say hello' })
      expect(completion.completion.value).toBe('Hello')
    })
    scope.stop()
  })

  it('can prepare a streaming request from OpenAPI request args', async () => {
    let url = ''
    let body: unknown
    let header: string | undefined
    const fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      url = String(input)
      body = JSON.parse(String(init?.body))
      header = new Headers(init?.headers).get('X-Test') ?? undefined
      return new Response(streamFromText('Hello'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const completion = useCompletion({
        id: 'completion-openapi-test',
        fetch,
        prepareRequest: ({ body }) =>
          fromOpenAPIRequestArgs({
            url: '/generated/completion/stream',
            options: {
              headers: { 'X-Test': 'yes' },
              data: body,
            },
          }),
      })
      await completion.complete('Say hello')

      expect(url).toBe('/generated/completion/stream')
      expect(body).toEqual({ prompt: 'Say hello' })
      expect(header).toBe('yes')
    })
    scope.stop()
  })

  it('supports per-call completion request options', async () => {
    let body: unknown
    let header: string | undefined
    let credentials: RequestCredentials | undefined
    const fetch = async (_input: RequestInfo | URL, init?: RequestInit) => {
      body = JSON.parse(String(init?.body))
      header = new Headers(init?.headers).get('X-Call') ?? undefined
      credentials = init?.credentials
      return new Response(streamFromText('Hello'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const completion = useCompletion({
        id: 'completion-call-options-test',
        fetch,
        body: { global: true },
        headers: { 'X-Base': 'yes' },
      })
      await completion.complete('Say hello', {
        body: { requestId: 'call-1' },
        headers: { 'X-Call': 'yes' },
        credentials: 'include',
      })

      expect(body).toEqual({ global: true, requestId: 'call-1', prompt: 'Say hello' })
      expect(header).toBe('yes')
      expect(credentials).toBe('include')
    })
    scope.stop()
  })
})

describe('experimental_useObject', () => {
  it('streams partial JSON text and validates the final object', async () => {
    let body: unknown
    const fetch = async (_input: RequestInfo | URL, init?: RequestInit) => {
      body = JSON.parse(String(init?.body))
      return new Response(streamFromText('{"title":"Halo","count":1}'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const object = experimental_useObject<{ title: string; count: number }>({
        id: 'object-test',
        fetch,
        schema: {
          type: 'object',
          required: ['title', 'count'],
          properties: {
            title: { type: 'string' },
            count: { type: 'number' },
          },
        },
      })

      const result = await object.submit('Summarize')

      expect(body).toMatchObject({
        input: 'Summarize',
        output: {
          type: 'object',
        },
      })
      expect(result).toEqual({ title: 'Halo', count: 1 })
      expect(object.object.value).toEqual({ title: 'Halo', count: 1 })
    })
    scope.stop()
  })

  it('can prepare an object stream request from OpenAPI request args', async () => {
    let url = ''
    let body: unknown
    const fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      url = String(input)
      body = JSON.parse(String(init?.body))
      return new Response(streamFromText('{"title":"Halo"}'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const object = experimental_useObject<{ title: string }>({
        id: 'object-openapi-test',
        fetch,
        schema: {
          type: 'object',
          required: ['title'],
          properties: { title: { type: 'string' } },
        },
        prepareRequest: ({ body }) =>
          fromOpenAPIRequestArgs({
            url: '/generated/object/stream',
            options: { data: body },
          }),
      })

      await object.submit('Summarize')

      expect(url).toBe('/generated/object/stream')
      expect(body).toMatchObject({ input: 'Summarize', output: { type: 'object' } })
    })
    scope.stop()
  })

  it('uses initialValue and accepts generic submit input with per-call request options', async () => {
    let body: unknown
    let header: string | undefined
    const fetch = async (_input: RequestInfo | URL, init?: RequestInit) => {
      body = JSON.parse(String(init?.body))
      header = new Headers(init?.headers).get('X-Object-Call') ?? undefined
      return new Response(streamFromText('{"title":"Halo"}'))
    }

    const scope = effectScope()
    await scope.run(async () => {
      const object = experimental_useObject<{ title: string }, { prompt: string }>({
        id: 'object-generic-input-test',
        fetch,
        initialValue: { title: 'Draft' },
        schema: {
          type: 'object',
          required: ['title'],
          properties: { title: { type: 'string' } },
        },
      })

      expect(object.object.value).toEqual({ title: 'Draft' })
      await object.submit(
        { prompt: 'Summarize' },
        { body: { requestId: 'object-1' }, headers: { 'X-Object-Call': 'yes' } }
      )

      expect(body).toMatchObject({
        input: { prompt: 'Summarize' },
        requestId: 'object-1',
        output: { type: 'object' },
      })
      expect(header).toBe('yes')
      expect(object.object.value).toEqual({ title: 'Halo' })
    })
    scope.stop()
  })
})

function streamFromText(text: string): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    },
  })
}
