import { describe, expect, it } from '@rstest/core'
import { effectScope, nextTick } from 'vue'
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
})

function streamFromText(text: string): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    },
  })
}
