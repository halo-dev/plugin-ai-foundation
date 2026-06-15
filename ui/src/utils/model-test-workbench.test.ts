import type { AiModel, OutputSpec } from '@/api/generated'
import { AiModelSpecFeaturesEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import { AIUISchemaValidationError, validateRuntimeSchema } from '@halo-dev/ai-foundation-sdk'
import {
  applyWorkbenchUIMessageChunk,
  buildOutputSpec,
  buildReasoningOptions,
  buildTestUiMessageChatRequest,
  createUserUIMessage,
  filterEnabledChatModels,
  parseProviderOptionsJson,
  readTestUiMessageChatStream,
  testUiMessageChatStreamUrl,
  workbenchDataPartSchemas,
  workbenchMessageMetadataSchema,
  type WorkbenchMessage,
} from './model-test-workbench'

describe('filterEnabledChatModels', () => {
  it('keeps only enabled chat-capable models', () => {
    expect(
      filterEnabledChatModels([
        model('chat-enabled', true),
        model('chat-disabled', false),
        model('embedding', true, AiModelSpecModelTypeEnum.Embedding),
      ]).map((item) => item.metadata.name),
    ).toEqual(['chat-enabled'])
  })
})

describe('parseProviderOptionsJson', () => {
  it('parses JSON objects', () => {
    expect(parseProviderOptionsJson('{ "openai": { "seed": 42 } }').value).toEqual({
      openai: { seed: 42 },
    })
  })

  it('rejects invalid or non-object values', () => {
    expect(parseProviderOptionsJson('{').error).toBeTruthy()
    expect(parseProviderOptionsJson('[]').error).toBeTruthy()
    expect(parseProviderOptionsJson('{ "seed": 42 }').error).toBeTruthy()
  })
})

describe('workbench runtime schemas', () => {
  it('accepts object metadata and known data part values', () => {
    expect(
      validateRuntimeSchema({ traceId: 'trace-1' }, workbenchMessageMetadataSchema, {
        target: 'message-metadata',
      }),
    ).toEqual({ traceId: 'trace-1' })
    expect(
      validateRuntimeSchema({ value: 'loading' }, workbenchDataPartSchemas.status, {
        target: 'data-part',
        partName: 'status',
        partType: 'data-status',
        partId: 'status-1',
      }),
    ).toEqual({ value: 'loading' })
  })

  it('rejects invalid workbench metadata and missing known data payloads', () => {
    expect(() =>
      validateRuntimeSchema('bad', workbenchMessageMetadataSchema, {
        target: 'message-metadata',
      })
    ).toThrow(AIUISchemaValidationError)
    expect(() =>
      validateRuntimeSchema(undefined, workbenchDataPartSchemas.status, {
        target: 'data-part',
        partName: 'status',
        partType: 'data-status',
        partId: 'status-1',
      })
    ).toThrow(AIUISchemaValidationError)
  })
})


describe('buildTestUiMessageChatRequest', () => {
  it('preserves UI messages and generation parameters for submit requests', () => {
    const userMessage: WorkbenchMessage = {
      id: 'user-1',
      role: 'user',
      content: 'Hello',
      uiMessage: createUserUIMessage('user-1', 'Hello'),
    }
    const assistantMessage: WorkbenchMessage = {
      id: 'assistant-1',
      role: 'assistant',
      content: 'Hi',
      state: 'done',
      uiMessage: {
        id: 'assistant-1',
        role: 'ASSISTANT',
        parts: [{ type: 'text', id: 'answer', text: 'Hi' }],
        metadata: { traceId: 'trace-1' },
      },
    }

    expect(
      buildTestUiMessageChatRequest([userMessage, assistantMessage], {
        systemPrompt: 'You are concise.',
        temperature: 0.2,
        maxOutputTokens: 128,
        reasoning: buildReasoningOptions({ mode: 'ENABLED' }),
        providerOptions: { openai: { seed: 42 } },
        output: { type: 'JSON' } as OutputSpec,
      }),
    ).toMatchObject({
      trigger: 'submit-message',
      system: 'You are concise.',
      temperature: 0.2,
      maxOutputTokens: 128,
      reasoning: { mode: 'ENABLED' },
      providerOptions: { openai: { seed: 42 } },
      output: { type: 'JSON' },
      messages: [
        {
          id: 'user-1',
          role: 'USER',
          parts: [{ type: 'text', id: 'user-1-text', text: 'Hello' }],
        },
        {
          id: 'assistant-1',
          role: 'ASSISTANT',
          parts: [{ type: 'text', id: 'answer', text: 'Hi' }],
          metadata: { traceId: 'trace-1' },
        },
      ],
    })
  })

  it('builds regenerate requests with the target UI message id', () => {
    expect(
      buildTestUiMessageChatRequest(
        [
          {
            id: 'user-1',
            role: 'user',
            content: 'Hello',
            uiMessage: createUserUIMessage('user-1', 'Hello'),
          },
          {
            id: 'assistant-1',
            role: 'assistant',
            content: 'Old',
            uiMessage: {
              id: 'assistant-ui-1',
              role: 'ASSISTANT',
              parts: [{ type: 'text', id: 'answer', text: 'Old' }],
            },
          },
        ],
        {},
        { trigger: 'regenerate-message', messageId: 'assistant-ui-1' },
      ),
    ).toMatchObject({
      trigger: 'regenerate-message',
      messageId: 'assistant-ui-1',
      messages: [
        { id: 'user-1', role: 'USER' },
        { id: 'assistant-ui-1', role: 'ASSISTANT' },
      ],
    })
  })

  it('preserves UI Message tool continuation parts in request history', () => {
    expect(
      buildTestUiMessageChatRequest(
        [
          {
            id: 'assistant-1',
            role: 'assistant',
            content: '',
            uiMessage: {
              id: 'assistant-1',
              role: 'ASSISTANT',
              parts: [
                {
                  type: 'tool-pay',
                  toolCallId: 'call_1',
                  toolName: 'pay',
                  state: 'output-available',
                  input: { amount: 1 },
                  output: { ok: true },
                  approval: {
                    id: 'approval_1',
                    approved: true,
                    reason: 'Approved from console test page',
                  },
                },
                {
                  type: 'tool-search',
                  toolCallId: 'call_2',
                  toolName: 'search',
                  state: 'output-error',
                  input: { q: 'Halo' },
                  errorText: 'failed',
                },
              ],
            },
          },
        ],
        {},
      ).messages,
    ).toEqual([
      {
        id: 'assistant-1',
        role: 'ASSISTANT',
        parts: [
          {
            type: 'tool-pay',
            toolCallId: 'call_1',
            toolName: 'pay',
            state: 'output-available',
            input: { amount: 1 },
            output: { ok: true },
            approval: {
              id: 'approval_1',
              approved: true,
              reason: 'Approved from console test page',
            },
          },
          {
            type: 'tool-search',
            toolCallId: 'call_2',
            toolName: 'search',
            state: 'output-error',
            input: { q: 'Halo' },
            errorText: 'failed',
          },
        ],
      },
    ])
  })
})

describe('buildReasoningOptions', () => {
  it('builds typed reasoning payloads', () => {
    expect(buildReasoningOptions({ mode: 'DEFAULT' })).toBeUndefined()
    expect(buildReasoningOptions({ mode: 'ENABLED' })).toEqual({ mode: 'ENABLED' })
    expect(buildReasoningOptions({ mode: 'DISABLED' })).toEqual({ mode: 'DISABLED' })
    expect(buildReasoningOptions({ mode: 'EFFORT', effort: 'HIGH' })).toEqual({
      mode: 'ENABLED',
      effort: 'HIGH',
    })
  })
})

describe('buildOutputSpec', () => {
  it('builds structured output specs from workbench controls', () => {
    expect(
      buildOutputSpec({
        mode: 'OBJECT',
        schemaText: '{ "type": "object", "properties": { "name": { "type": "string" } } }',
      }).value,
    ).toMatchObject({
      type: 'OBJECT',
      schema: { type: 'object' },
    })

    expect(buildOutputSpec({ mode: 'CHOICE', choicesText: 'yes\nno' }).value).toEqual({
      type: 'CHOICE',
      choices: ['yes', 'no'],
    })
    expect(buildOutputSpec({ mode: 'TEXT' }).value).toBeUndefined()
  })

  it('rejects invalid structured output control values', () => {
    expect(buildOutputSpec({ mode: 'OBJECT', schemaText: '[' }).error).toBeTruthy()
    expect(buildOutputSpec({ mode: 'CHOICE', choicesText: '' }).error).toBeTruthy()
  })
})


describe('testUiMessageChatStreamUrl', () => {
  it('uses the UI Message stream path with the shared console flags', () => {
    expect(
      testUiMessageChatStreamUrl('model/name', {
        testToolEnabled: true,
        externalTestToolEnabled: true,
        toolCallRepairEnabled: true,
      }),
    ).toBe(
      '/apis/console.api.aifoundation.halo.run/v1alpha1/models/model%2Fname/test-chat/ui-message/stream?enableTestTool=true&enableExternalTestTool=true&enableToolCallRepair=true',
    )
  })
})

describe('readTestUiMessageChatStream', () => {
  it('reads Halo UI Message SSE chunks through the shared package transport', async () => {
    const originalFetch = globalThis.fetch
    const calls: Array<{ input: RequestInfo | URL; init?: RequestInit }> = []
    globalThis.fetch = (async (input, init) => {
      calls.push({ input, init })
      return new Response(
        streamFromText(
          'data: {"type":"start","messageId":"assistant-ui"}\n\n'
            + 'data: {"type":"text-delta","id":"answer","delta":"Hi"}\n\n'
            + 'data: [DONE]\n\n',
        ),
        {
          headers: {
            'Content-Type': 'text/event-stream',
            'X-Halo-AI-UI-Message-Stream': 'v1',
          },
        },
      )
    }) as typeof fetch

    try {
      const chunks: unknown[] = []
      await readTestUiMessageChatStream({
        modelName: 'model/name',
        requestBody: {
          id: 'chat-1',
          messages: [],
          trigger: 'submit-message',
        },
        streamOptions: { testToolEnabled: true },
        signal: new AbortController().signal,
        onChunks: (nextChunks) => chunks.push(...nextChunks),
      })

      expect(String(calls[0]?.input)).toBe(
        '/apis/console.api.aifoundation.halo.run/v1alpha1/models/model%2Fname/test-chat/ui-message/stream?enableTestTool=true',
      )
      expect(JSON.parse(String(calls[0]?.init?.body))).toMatchObject({
        id: 'chat-1',
        messages: [],
        trigger: 'submit-message',
      })
      expect(chunks).toEqual([
        { type: 'start', messageId: 'assistant-ui' },
        { type: 'text-delta', id: 'answer', delta: 'Hi' },
      ])
    } finally {
      globalThis.fetch = originalFetch
    }
  })
})


describe('applyWorkbenchUIMessageChunk', () => {
  it('aggregates text, reasoning, metadata, data, tool parts, warnings, and finish state', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
    }

    applyWorkbenchUIMessageChunk(message, {
      type: 'start',
      messageId: 'assistant-ui',
      messageMetadata: { traceId: 'trace-1' },
    })
    applyWorkbenchUIMessageChunk(message, { type: 'reasoning-start', id: 'reasoning' })
    applyWorkbenchUIMessageChunk(message, {
      type: 'reasoning-delta',
      id: 'reasoning',
      delta: 'think',
      providerMetadata: { provider: { id: 'reasoning-id' } },
    })
    applyWorkbenchUIMessageChunk(message, { type: 'reasoning-end', id: 'reasoning' })
    applyWorkbenchUIMessageChunk(message, { type: 'text-start', id: 'answer' })
    applyWorkbenchUIMessageChunk(message, { type: 'text-delta', id: 'answer', delta: 'Hi' })
    applyWorkbenchUIMessageChunk(message, {
      type: 'data-weather',
      id: 'weather',
      name: 'weather',
      data: { temp: 22 },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'data-progress',
      id: 'progress',
      name: 'progress',
      data: { percent: 50 },
      transient: true,
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-search',
      toolCallId: 'call_1',
      toolName: 'search',
      state: 'input-available',
      input: { q: 'Halo' },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-search',
      toolCallId: 'call_1',
      toolName: 'search',
      state: 'output-available',
      output: { ok: true },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'finish-step',
      warnings: [{ code: 'w', message: 'warn' }],
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'finish',
      messageMetadata: { finished: true },
    })

    expect(message).toMatchObject({
      content: 'Hi',
      reasoningContent: 'think',
      reasoningState: 'done',
      state: 'done',
      transientData: { progress: { percent: 50 } },
      warnings: [{ code: 'w', message: 'warn' }],
      uiMessage: {
        id: 'assistant-ui',
        role: 'ASSISTANT',
        metadata: { traceId: 'trace-1', finished: true },
        parts: [
          {
            type: 'reasoning',
            id: 'reasoning',
            text: 'think',
            providerMetadata: { provider: { id: 'reasoning-id' } },
          },
          { type: 'text', id: 'answer', text: 'Hi' },
          { type: 'data-weather', id: 'weather', name: 'weather', data: { temp: 22 } },
          {
            type: 'tool-search',
            toolCallId: 'call_1',
            toolName: 'search',
            state: 'output-available',
            input: { q: 'Halo' },
            output: { ok: true },
          },
        ],
      },
      toolEvents: [
        {
          type: 'tool-result',
          toolCallId: 'call_1',
          result: { ok: true },
        },
      ],
    })
  })

  it('marks UI Message tool calls as failed when a matching tool error arrives', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
    }

    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-search',
      toolCallId: 'call_1',
      toolName: 'search',
      state: 'input-available',
      input: { q: 'Halo' },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-search',
      toolCallId: 'call_1',
      toolName: 'search',
      state: 'output-error',
      errorText: 'failed',
    })

    expect(message.toolEvents).toMatchObject([
      {
        type: 'tool-error',
        toolCallId: 'call_1',
        errorText: 'failed',
      },
    ])
  })

  it('projects UI Message approval decisions onto dynamic tool parts', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
      uiMessage: {
        id: 'assistant',
        role: 'ASSISTANT',
        parts: [
          {
            type: 'tool-pay',
            toolCallId: 'call_1',
            toolName: 'pay',
            state: 'approval-responded',
            approval: {
              id: 'approval_1',
              approved: false,
              reason: 'Denied',
            },
          },
        ],
      },
    }

    applyWorkbenchUIMessageChunk(message, { type: 'finish' })

    expect(message.toolEvents).toMatchObject([
      {
        type: 'tool-approval-request',
        approvalId: 'approval_1',
        approvalStatus: 'denied',
      },
    ])
  })

  it('does not ask for external tool output when a UI Message tool call needs approval', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
      uiMessage: {
        id: 'assistant',
        role: 'ASSISTANT',
        parts: [
          {
            type: 'tool-halo_test_info',
            toolCallId: 'call_1',
            toolName: 'halo_test_info',
            state: 'approval-requested',
            input: { query: '这是一个测试调用' },
            approval: { id: 'approval_1' },
          },
        ],
      },
    }

    applyWorkbenchUIMessageChunk(message, { type: 'finish' })

    expect(message.toolEvents).toMatchObject([
      {
        type: 'tool-approval-request',
        approvalId: 'approval_1',
        approvalStatus: 'pending',
      },
    ])
  })

  it('appends repeated text and reasoning deltas with the same part id', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
    }

    applyWorkbenchUIMessageChunk(message, { type: 'text-delta', id: 'answer', delta: 'Hel' })
    applyWorkbenchUIMessageChunk(message, { type: 'text-delta', id: 'answer', delta: 'lo' })
    applyWorkbenchUIMessageChunk(message, {
      type: 'reasoning-delta',
      id: 'reasoning',
      delta: 'think ',
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'reasoning-delta',
      id: 'reasoning',
      delta: 'more',
    })

    expect(message.content).toBe('Hello')
    expect(message.reasoningContent).toBe('think more')
    expect(message.uiMessage?.parts).toMatchObject([
      { type: 'text', id: 'answer', text: 'Hello' },
      { type: 'reasoning', id: 'reasoning', text: 'think more' },
    ])
  })

  it('marks abort chunks as stopped without appending an error', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: 'partial',
      state: 'streaming',
      uiMessage: {
        id: 'assistant-ui',
        role: 'ASSISTANT',
        parts: [{ type: 'text', id: 'answer', text: 'partial' }],
      },
    }

    applyWorkbenchUIMessageChunk(message, { type: 'abort' })

    expect(message.state).toBe('stopped')
    expect(message.content).toBe('partial')
  })
})


function model(
  name: string,
  enabled: boolean,
  modelType: AiModel['spec']['modelType'] = AiModelSpecModelTypeEnum.Language,
): AiModel {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name },
    spec: {
      providerName: 'provider',
      modelId: name,
      displayName: name,
      enabled,
      modelType,
      features: [AiModelSpecFeaturesEnum.Streaming],
      adapterType: 'openai-chat',
    },
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
