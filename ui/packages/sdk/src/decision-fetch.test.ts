// @vitest-environment node
import { once } from 'node:events'
import { createServer } from 'node:http'
import { expect, it } from 'vitest'
import { createDecisionClient } from './decision'
import { AIUIProtocolError, isAbortError } from './errors'

it('preserves cancellation while reading a real fetch response body', async () => {
  const server = createServer((_request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.flushHeaders()
    response.write('{"model":"jev-1.14",')
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const controller = new AbortController()
  try {
    const address = server.address()
    if (!address || typeof address === 'string') throw new Error('Missing server address')
    let headersReceived!: () => void
    const received = new Promise<void>((resolve) => {
      headersReceived = resolve
    })
    const client = createDecisionClient({
      api: `http://127.0.0.1:${address.port}/decision`,
      fetch: async (input, init) => {
        const response = await fetch(input, init)
        headersReceived()
        return response
      },
    })
    const result = client
      .decide(
        { state: 'test', questions: { spam: { type: 'YES_NO', instructions: 'Spam?' } } },
        { signal: controller.signal },
      )
      .catch((error: unknown) => error)
    await received
    controller.abort()
    const error = await result
    expect(isAbortError(error)).toBe(true)
    expect(error).toMatchObject({ name: 'AbortError' })
    expect(error).not.toBeInstanceOf(AIUIProtocolError)
  } finally {
    controller.abort()
    server.closeAllConnections()
    await new Promise<void>((resolve) => server.close(() => resolve()))
  }
})
