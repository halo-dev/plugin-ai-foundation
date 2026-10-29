import { AIUIError, AIUIProtocolError, isAbortError } from './errors'
import type { FetchFunction } from './types'

export type DecisionJsonValue =
  | string
  | number
  | boolean
  | null
  | DecisionJsonValue[]
  | { [key: string]: DecisionJsonValue }
export type DecisionState = string | DecisionJsonValue[] | { [key: string]: DecisionJsonValue }

export type DecisionQuestion =
  | { type: 'YES_NO'; instructions: string; yesCriteria?: string; noCriteria?: string }
  | { type: 'CHOICE'; instructions: string; options: Record<string, string> }
  | { type: 'SCORE'; instructions: string; levels: string[] }

export type DecisionAnswer =
  | { type: 'YES_NO'; probability: number }
  | {
      type: 'CHOICE'
      choice: string
      probabilities?: Record<string, number> | null
      confidence?: number | null
    }
  | {
      type: 'SCORE'
      score: number
      probabilities?: Record<string, number> | null
      confidence?: number | null
      legend?: Record<string, string> | null
    }

export interface DecisionRequest {
  state: DecisionState
  questions: Record<string, DecisionQuestion>
}

export interface DecisionResponse {
  model: string
  answers: Record<string, DecisionAnswer>
  usage?: { inputTokens?: number | null; outputTokens?: number | null } | null
}

export interface DecisionClientOptions {
  /** A consumer-plugin endpoint that returns AI Foundation's DecisionResponse. */
  api: string
  headers?: HeadersInit
  credentials?: RequestCredentials
  fetch?: FetchFunction
}

/** Calls your plugin's decision endpoint. Provider API keys remain on the server. */
export function createDecisionClient(options: DecisionClientOptions) {
  return {
    async decide(
      request: DecisionRequest,
      callOptions: { signal?: AbortSignal } = {},
    ): Promise<DecisionResponse> {
      const headers = new Headers(options.headers)
      headers.set('Content-Type', 'application/json')
      const response = await (options.fetch ?? globalThis.fetch)(options.api, {
        method: 'POST',
        headers,
        credentials: options.credentials,
        signal: callOptions.signal,
        body: JSON.stringify(request),
      })
      if (!response.ok) {
        throw new AIUIError(`Decision request failed (${response.status})`, {
          status: response.status,
          response,
        })
      }
      let result: unknown
      try {
        result = await response.json()
      } catch (cause) {
        if (isAbortError(cause)) throw cause
        throw new AIUIProtocolError('Decision endpoint returned invalid JSON', { cause })
      }
      validateDecisionResponse(result, request)
      return result
    },
  }
}

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new AIUIProtocolError(message)
}

function probability(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 1
}

function sameKeys(left: Record<string, unknown>, keys: string[]) {
  return (
    Object.keys(left).length === keys.length &&
    keys.every((key) => Object.prototype.hasOwnProperty.call(left, key))
  )
}

function validateDecisionResponse(
  value: unknown,
  request: DecisionRequest,
): asserts value is DecisionResponse {
  assert(
    object(value) && typeof value.model === 'string' && value.model.trim(),
    'Missing decision response model',
  )
  assert(
    object(value.answers) && sameKeys(value.answers, Object.keys(request.questions)),
    'Decision question IDs do not match',
  )
  for (const [id, question] of Object.entries(request.questions)) {
    const answer = value.answers[id]
    assert(object(answer) && answer.type === question.type, `Invalid answer type for ${id}`)
    if (question.type === 'YES_NO') {
      assert(probability(answer.probability), `Invalid yes/no probability for ${id}`)
      continue
    }
    const keys =
      question.type === 'CHOICE'
        ? Object.keys(question.options)
        : question.levels.map((_, index) => String(index))
    if (answer.probabilities != null) {
      assert(
        object(answer.probabilities) && sameKeys(answer.probabilities, keys),
        `Invalid distribution for ${id}`,
      )
      const values = Object.values(answer.probabilities)
      assert(values.every(probability), `Invalid probabilities for ${id}`)
      assert(
        Math.abs(values.reduce((sum, item) => sum + item, 0) - 1) <= 0.001,
        `Probabilities must sum to one for ${id}`,
      )
    }
    if (answer.confidence != null) {
      assert(probability(answer.confidence), `Invalid confidence for ${id}`)
    }
    if (question.type === 'CHOICE') {
      assert(
        typeof answer.choice === 'string' && keys.includes(answer.choice),
        `Unknown choice for ${id}`,
      )
    } else {
      assert(
        typeof answer.score === 'number' &&
          Number.isFinite(answer.score) &&
          answer.score >= 0 &&
          answer.score <= question.levels.length - 1,
        `Invalid score for ${id}`,
      )
      if (answer.legend != null) {
        assert(
          object(answer.legend) &&
            sameKeys(answer.legend, keys) &&
            Object.values(answer.legend).every((item) => typeof item === 'string'),
          `Invalid score legend for ${id}`,
        )
      }
    }
  }
  if (value.usage != null) {
    assert(object(value.usage), 'Invalid decision token usage')
    for (const key of ['inputTokens', 'outputTokens']) {
      const count = value.usage[key]
      assert(
        count == null || (typeof count === 'number' && Number.isSafeInteger(count) && count >= 0),
        'Invalid decision token count',
      )
    }
  }
}
