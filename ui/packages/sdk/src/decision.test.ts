import { describe, expect, it, vi } from 'vitest'
import { createDecisionClient, type DecisionRequest } from './decision'
import { AIUIError, AIUIProtocolError } from './errors'

const request: DecisionRequest = {
  state: { comment: 'hello' },
  questions: { spam: { type: 'YES_NO', instructions: 'Spam?' } },
}
const result = {
  model: 'jev-version',
  answers: { spam: { type: 'YES_NO', probability: 0.12 } },
  usage: { inputTokens: 10, outputTokens: null },
}

describe('decision client', () => {
  it('posts to the consumer endpoint and preserves probability and unknown token counts', async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json(result))
    const controller = new AbortController()
    const client = createDecisionClient({
      api: '/apis/my-plugin/decide',
      fetch,
      headers: { 'X-Request': 'example' },
      credentials: 'same-origin',
    })
    expect(await client.decide(request, { signal: controller.signal })).toEqual(result)
    expect(fetch).toHaveBeenCalledWith(
      '/apis/my-plugin/decide',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(request),
        signal: controller.signal,
        credentials: 'same-origin',
      }),
    )
    expect(fetch.mock.calls[0]?.[1].headers.get('X-Request')).toBe('example')
  })

  it.each([
    { model: 'jev', answers: {} },
    { model: 'jev', answers: { spam: { type: 'YES_NO', probability: 2 } } },
    { model: 'jev', answers: { spam: { type: 'CHOICE', choice: 'no' } } },
    { ...result, usage: { inputTokens: -1 } },
  ])('rejects invalid decision payloads', async (payload) => {
    const client = createDecisionClient({
      api: '/decision',
      fetch: vi.fn().mockResolvedValue(Response.json(payload)),
    })
    await expect(client.decide(request)).rejects.toBeInstanceOf(AIUIProtocolError)
  })

  it('reports HTTP errors with status', async () => {
    const client = createDecisionClient({
      api: '/decision',
      fetch: vi.fn().mockResolvedValue(new Response('', { status: 429 })),
    })
    await expect(client.decide(request)).rejects.toMatchObject<Partial<AIUIError>>({ status: 429 })
  })

  it('preserves choice distributions and fractional scores, rejecting incomplete distributions', async () => {
    const mixed: DecisionRequest = {
      state: ['comment'],
      questions: {
        intent: { type: 'CHOICE', instructions: 'Intent?', options: { a: 'A', b: 'B' } },
        rating: { type: 'SCORE', instructions: 'Rating?', levels: ['low', 'high'] },
      },
    }
    const payload = {
      model: 'jev-version',
      answers: {
        intent: { type: 'CHOICE', choice: 'a', confidence: 0.6, probabilities: { a: 0.8, b: 0.2 } },
        rating: {
          type: 'SCORE',
          score: 0.8,
          confidence: 0.6,
          probabilities: { '0': 0.2, '1': 0.8 },
          legend: { '0': 'low', '1': 'high' },
        },
      },
    }
    const fetch = vi.fn().mockResolvedValueOnce(Response.json(payload))
    const client = createDecisionClient({ api: '/decision', fetch })
    expect(await client.decide(mixed)).toEqual(payload)
    fetch.mockResolvedValueOnce(
      Response.json({
        ...payload,
        answers: {
          ...payload.answers,
          intent: { ...payload.answers.intent, probabilities: { a: 1 } },
        },
      }),
    )
    await expect(client.decide(mixed)).rejects.toBeInstanceOf(AIUIProtocolError)
  })
})
