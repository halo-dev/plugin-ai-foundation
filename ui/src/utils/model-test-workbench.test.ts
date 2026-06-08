import type { AiModel, OutputSpec } from '@/api/generated'
import { AiModelSpecFeaturesEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  applyWorkbenchUIMessageChunk,
  applyWorkbenchStreamPart,
  buildOutputSpec,
  buildReasoningOptions,
  buildTestChatRequest,
  buildTestUiMessageChatRequest,
  createUserUIMessage,
  filterEnabledChatModels,
  flushSseJsonBuffer,
  isRenderableReasoningDelta,
  isRenderableTextDelta,
  isTerminalTextStreamPart,
  parseProviderOptionsJson,
  parseSseJsonLines,
  testChatStreamUrl,
  testUiMessageChatStreamUrl,
  toToolEvent,
  type TextStreamPart,
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

describe('buildTestChatRequest', () => {
  it('builds messages and parameters for streaming test requests', () => {
    const messages: WorkbenchMessage[] = [
      { id: '1', role: 'user', content: 'Hello' },
      { id: '2', role: 'assistant', content: 'Hi', reasoningContent: 'Think', state: 'done' },
      { id: '3', role: 'assistant', content: 'Failed', state: 'error' },
    ]

    expect(
      buildTestChatRequest(messages, {
        systemPrompt: 'You are concise.',
        temperature: 0.2,
        topP: 0.9,
        maxOutputTokens: 128,
        reasoning: buildReasoningOptions({ mode: 'DISABLED' }),
        providerOptions: { openai: { seed: 42 } },
        output: { type: 'OBJECT', schema: { type: 'object' } } as unknown as OutputSpec,
      }),
    ).toMatchObject({
      system: 'You are concise.',
      messages: [
        { role: 'USER', content: [{ type: 'text', text: 'Hello' }] },
        {
          role: 'ASSISTANT',
          content: [{ type: 'text', text: 'Hi' }],
        },
      ],
      temperature: 0.2,
      topP: 0.9,
      maxOutputTokens: 128,
      reasoning: { mode: 'DISABLED' },
      providerOptions: { openai: { seed: 42 } },
      output: { type: 'OBJECT', schema: { type: 'object' } },
    })
  })

  it('does not send displayed reasoning back as chat history', () => {
    expect(
      buildTestChatRequest(
        [
          { id: '1', role: 'user', content: 'Hello' },
          { id: '2', role: 'assistant', content: 'Hi', reasoningContent: 'Think', state: 'done' },
          { id: '3', role: 'user', content: 'Continue' },
        ],
        {},
      ),
    ).toMatchObject({
      messages: [
        { role: 'USER', content: [{ type: 'text', text: 'Hello' }] },
        { role: 'ASSISTANT', content: [{ type: 'text', text: 'Hi' }] },
        { role: 'USER', content: [{ type: 'text', text: 'Continue' }] },
      ],
    })
  })

  it('uses returned response messages before approval continuation messages', () => {
    expect(
      buildTestChatRequest(
        [
          { id: '1', role: 'user', content: 'Remove file' },
          {
            id: '2',
            role: 'assistant',
            content: '',
            state: 'done',
            responseMessages: [
              {
                role: 'ASSISTANT',
                content: [
                  {
                    type: 'tool-call',
                    toolCallId: 'call_1',
                    toolName: 'run',
                    input: { command: 'rm file' },
                  },
                  {
                    type: 'tool-approval-request',
                    approvalId: 'approval_call_1',
                    toolCallId: 'call_1',
                    toolName: 'run',
                    stepIndex: 1,
                    input: { command: 'rm file' },
                  },
                ],
              },
            ],
            followingMessages: [
              {
                role: 'TOOL',
                content: [
                  {
                    type: 'tool-approval-response',
                    approvalId: 'approval_call_1',
                    toolCallId: 'call_1',
                    toolName: 'run',
                    approved: false,
                    reason: 'Denied from console test page',
                  },
                ],
              },
            ],
          },
          {
            id: '3',
            role: 'assistant',
            content: '工具执行被拒绝',
            state: 'done',
            responseMessages: [
              {
                role: 'TOOL',
                content: [
                  {
                    type: 'tool-error',
                    toolCallId: 'call_1',
                    toolName: 'run',
                    errorText: 'Tool execution denied: Denied from console test page',
                  },
                ],
              },
              { role: 'ASSISTANT', content: [{ type: 'text', text: '工具执行被拒绝' }] },
            ],
          },
        ],
        {},
      ).messages,
    ).toEqual([
      { role: 'USER', content: [{ type: 'text', text: 'Remove file' }] },
      {
        role: 'ASSISTANT',
        content: [
          {
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'run',
            input: { command: 'rm file' },
          },
          {
            type: 'tool-approval-request',
            approvalId: 'approval_call_1',
            toolCallId: 'call_1',
            toolName: 'run',
            stepIndex: 1,
            input: { command: 'rm file' },
          },
        ],
      },
      {
        role: 'TOOL',
        content: [
          {
            type: 'tool-approval-response',
            approvalId: 'approval_call_1',
            toolCallId: 'call_1',
            toolName: 'run',
            approved: false,
            reason: 'Denied from console test page',
          },
        ],
      },
      {
        role: 'TOOL',
        content: [
          {
            type: 'tool-error',
            toolCallId: 'call_1',
            toolName: 'run',
            errorText: 'Tool execution denied: Denied from console test page',
          },
        ],
      },
      { role: 'ASSISTANT', content: [{ type: 'text', text: '工具执行被拒绝' }] },
    ])
  })

  it('uses returned external tool-call messages before external results', () => {
    expect(
      buildTestChatRequest(
        [
          { id: '1', role: 'user', content: 'Get external info' },
          {
            id: '2',
            role: 'assistant',
            content: '',
            state: 'done',
            responseMessages: [
              {
                role: 'ASSISTANT',
                content: [
                  {
                    type: 'tool-call',
                    toolCallId: 'call_1',
                    toolName: 'halo_external_test_info',
                    input: { query: 'Halo' },
                  },
                ],
              },
            ],
            followingMessages: [
              {
                role: 'TOOL',
                content: [
                  {
                    type: 'tool-result',
                    toolCallId: 'call_1',
                    toolName: 'halo_external_test_info',
                    result: { ok: true },
                  },
                ],
              },
            ],
          },
        ],
        {},
      ).messages,
    ).toEqual([
      { role: 'USER', content: [{ type: 'text', text: 'Get external info' }] },
      {
        role: 'ASSISTANT',
        content: [
          {
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'halo_external_test_info',
            input: { query: 'Halo' },
          },
        ],
      },
      {
        role: 'TOOL',
        content: [
          {
            type: 'tool-result',
            toolCallId: 'call_1',
            toolName: 'halo_external_test_info',
            result: { ok: true },
          },
        ],
      },
    ])
  })

  it('uses returned external tool-call messages before external errors', () => {
    expect(
      buildTestChatRequest(
        [
          { id: '1', role: 'user', content: 'Get external info' },
          {
            id: '2',
            role: 'assistant',
            content: '',
            state: 'done',
            responseMessages: [
              {
                role: 'ASSISTANT',
                content: [
                  {
                    type: 'tool-call',
                    toolCallId: 'call_1',
                    toolName: 'halo_external_test_info',
                    input: { query: 'Halo' },
                  },
                ],
              },
            ],
            followingMessages: [
              {
                role: 'TOOL',
                content: [
                  {
                    type: 'tool-error',
                    toolCallId: 'call_1',
                    toolName: 'halo_external_test_info',
                    errorText: 'External timeout',
                  },
                ],
              },
            ],
          },
          {
            id: '3',
            role: 'assistant',
            content: '外部工具超时',
            state: 'done',
            responseMessages: [
              { role: 'ASSISTANT', content: [{ type: 'text', text: '外部工具超时' }] },
            ],
          },
        ],
        {},
      ).messages,
    ).toEqual([
      { role: 'USER', content: [{ type: 'text', text: 'Get external info' }] },
      {
        role: 'ASSISTANT',
        content: [
          {
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'halo_external_test_info',
            input: { query: 'Halo' },
          },
        ],
      },
      {
        role: 'TOOL',
        content: [
          {
            type: 'tool-error',
            toolCallId: 'call_1',
            toolName: 'halo_external_test_info',
            errorText: 'External timeout',
          },
        ],
      },
      { role: 'ASSISTANT', content: [{ type: 'text', text: '外部工具超时' }] },
    ])
  })

  it('uses repaired response messages once for continued requests', () => {
    expect(
      buildTestChatRequest(
        [
          { id: '1', role: 'user', content: 'Repair tool input' },
          {
            id: '2',
            role: 'assistant',
            content: '',
            state: 'done',
            responseMessages: [
              {
                role: 'ASSISTANT',
                content: [
                  {
                    type: 'tool-call',
                    toolCallId: 'call_1',
                    toolName: 'halo_repair_test_info',
                    input: { query: 'repair me', repairSource: 'console-test' },
                  },
                ],
              },
              {
                role: 'TOOL',
                content: [
                  {
                    type: 'tool-result',
                    toolCallId: 'call_1',
                    toolName: 'halo_repair_test_info',
                    result: { ok: true },
                  },
                ],
              },
            ],
            historyParts: [
              {
                type: 'tool-call',
                toolCallId: 'call_1',
                toolName: 'halo_repair_test_info',
                input: { message: 'repair me' },
              },
            ],
          },
          { id: '3', role: 'user', content: 'Continue' },
        ],
        {},
      ).messages,
    ).toEqual([
      { role: 'USER', content: [{ type: 'text', text: 'Repair tool input' }] },
      {
        role: 'ASSISTANT',
        content: [
          {
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'halo_repair_test_info',
            input: { query: 'repair me', repairSource: 'console-test' },
          },
        ],
      },
      {
        role: 'TOOL',
        content: [
          {
            type: 'tool-result',
            toolCallId: 'call_1',
            toolName: 'halo_repair_test_info',
            result: { ok: true },
          },
        ],
      },
      { role: 'USER', content: [{ type: 'text', text: 'Continue' }] },
    ])
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
                  type: 'tool-approval-request',
                  approvalId: 'approval_1',
                  toolCallId: 'call_1',
                  toolName: 'pay',
                },
                {
                  type: 'tool-approval-response',
                  approvalId: 'approval_1',
                  approved: true,
                  reason: 'Approved from console test page',
                },
                {
                  type: 'tool-result',
                  toolCallId: 'call_1',
                  toolName: 'pay',
                  result: { ok: true },
                },
                {
                  type: 'tool-error',
                  toolCallId: 'call_2',
                  toolName: 'search',
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
            type: 'tool-approval-request',
            approvalId: 'approval_1',
            toolCallId: 'call_1',
            toolName: 'pay',
          },
          {
            type: 'tool-approval-response',
            approvalId: 'approval_1',
            approved: true,
            reason: 'Approved from console test page',
          },
          {
            type: 'tool-result',
            toolCallId: 'call_1',
            toolName: 'pay',
            result: { ok: true },
          },
          {
            type: 'tool-error',
            toolCallId: 'call_2',
            toolName: 'search',
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

describe('parseSseJsonLines', () => {
  it('parses complete data lines and preserves partial buffer', () => {
    const result = parseSseJsonLines<{ content: string }>(
      '',
      'data: {"delta":"Hel"}\n\ndata: [DONE]\n\ndata: {"delta":"lo"}',
    )

    expect(result.chunks).toEqual([{ delta: 'Hel' }])
    expect(flushSseJsonBuffer<{ delta: string }>(result.buffer)).toEqual([{ delta: 'lo' }])
  })

  it('accepts nested data prefixes from double-encoded SSE lines', () => {
    const result = parseSseJsonLines<{ type: string; messageId: string }>(
      '',
      'data: data: {"type":"start","messageId":"msg_1"}\n\ndata: data: [DONE]\n\n',
    )

    expect(result.chunks).toEqual([{ type: 'start', messageId: 'msg_1' }])
  })

  it('keeps rich stream parts for the caller to classify', () => {
    const result = parseSseJsonLines<TextStreamPart>(
      '',
      [
        'data: {"type":"start-step","stepIndex":0}',
        'data: {"type":"raw","metadata":{"safe":"ok"}}',
        'data: {"type":"tool-call","toolCallId":"call_1","toolName":"weather","input":{"location":"SF"}}',
        'data: {"type":"tool-input-start","toolCallId":"call_3","toolName":"weather","id":"input_1"}',
        'data: {"type":"tool-input-delta","toolCallId":"call_3","toolName":"weather","id":"input_1","delta":"{\\"location\\""}',
        'data: {"type":"tool-result","toolCallId":"call_1","toolName":"weather","result":{"temperature":22}}',
        'data: {"type":"tool-error","toolCallId":"call_2","toolName":"search","errorText":"failed"}',
        'data: {"type":"source","id":"src_1","url":"https://example.com","title":"Example"}',
        'data: {"type":"file","id":"file_1","title":"answer.txt","mediaType":"text/plain"}',
        'data: {"type":"reasoning-delta","delta":"Thinking","providerMetadata":{"deepseek":{}}}',
        'data: {"type":"unknown-provider-part"}',
        'data: {"type":"text-delta","delta":"**Hi**"}',
        'data: {"type":"finish"}',
        '',
      ].join('\n'),
    )

    expect(result.chunks.map((chunk) => chunk.type)).toEqual([
      'start-step',
      'raw',
      'tool-call',
      'tool-input-start',
      'tool-input-delta',
      'tool-result',
      'tool-error',
      'source',
      'file',
      'reasoning-delta',
      'unknown-provider-part',
      'text-delta',
      'finish',
    ])
    expect(result.chunks[2]).toMatchObject({
      toolCallId: 'call_1',
      toolName: 'weather',
      input: { location: 'SF' },
    })
    expect(result.chunks[5]).toMatchObject({
      toolCallId: 'call_1',
      toolName: 'weather',
      result: { temperature: 22 },
    })
    expect(result.chunks[6]).toMatchObject({
      toolCallId: 'call_2',
      toolName: 'search',
      errorText: 'failed',
    })
    expect(result.chunks[7]).toMatchObject({
      type: 'source',
      url: 'https://example.com',
      title: 'Example',
    })
    expect(result.chunks[8]).toMatchObject({
      type: 'file',
      title: 'answer.txt',
      mediaType: 'text/plain',
    })
    expect(result.chunks.filter(isRenderableTextDelta).map((chunk) => chunk.delta)).toEqual([
      '**Hi**',
    ])
    expect(result.chunks.filter(isRenderableReasoningDelta).map((chunk) => chunk.delta)).toEqual([
      'Thinking',
    ])
    expect(result.chunks.filter(isTerminalTextStreamPart).map((chunk) => chunk.type)).toEqual([
      'finish',
    ])
  })
})

describe('testChatStreamUrl', () => {
  it('adds enabled console test flags as query parameters', () => {
    expect(
      testChatStreamUrl('model/name', {
        testToolEnabled: true,
        externalTestToolEnabled: true,
        toolCallRepairEnabled: false,
      }),
    ).toBe(
      '/apis/console.api.aifoundation.halo.run/v1alpha1/models/model%2Fname/test-chat/stream?enableTestTool=true&enableExternalTestTool=true',
    )
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

describe('applyWorkbenchStreamPart', () => {
  it('accumulates text, reasoning, response messages, warnings, and finish state', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
      leadingMessages: [{ role: 'USER', content: [{ type: 'text', text: 'hello' }] }],
      historyParts: [{ type: 'text', text: 'old' }],
    }

    applyWorkbenchStreamPart(message, { type: 'reasoning-start' })
    applyWorkbenchStreamPart(message, { type: 'reasoning-delta', delta: 'think' })
    applyWorkbenchStreamPart(message, { type: 'reasoning-end' })
    applyWorkbenchStreamPart(message, { type: 'text-delta', delta: 'answer' })
    applyWorkbenchStreamPart(message, {
      type: 'finish-step',
      warnings: [{ code: 'w', message: 'warn' }],
      response: {
        messages: [{ role: 'ASSISTANT', content: [{ type: 'text', text: 'answer' }] }],
      },
    })
    applyWorkbenchStreamPart(message, { type: 'finish' })

    expect(message).toMatchObject({
      content: 'answer',
      reasoningContent: 'think',
      reasoningState: 'done',
      state: 'done',
      warnings: [{ code: 'w', message: 'warn' }],
      responseMessages: [{ role: 'ASSISTANT', content: [{ type: 'text', text: 'answer' }] }],
      leadingMessages: undefined,
      historyParts: undefined,
    })
  })

  it('updates pending tool-call status when a result arrives', () => {
    const message: WorkbenchMessage = {
      id: 'assistant',
      role: 'assistant',
      content: '',
      state: 'streaming',
    }

    applyWorkbenchStreamPart(message, {
      type: 'tool-call',
      toolCallId: 'call_1',
      toolName: 'search',
      input: { q: 'Halo' },
    })
    applyWorkbenchStreamPart(message, {
      type: 'tool-result',
      toolCallId: 'call_1',
      toolName: 'search',
      result: { ok: true },
    })

    expect(message.toolEvents).toMatchObject([
      { type: 'tool-call', toolCallId: 'call_1', externalStatus: 'completed' },
      { type: 'tool-result', toolCallId: 'call_1' },
    ])
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
      type: 'data',
      name: 'weather',
      data: { temp: 22 },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'data',
      name: 'progress',
      data: { percent: 50 },
      transientData: true,
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-call',
      toolCallId: 'call_1',
      toolName: 'search',
      input: { q: 'Halo' },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-result',
      toolCallId: 'call_1',
      toolName: 'search',
      result: { ok: true },
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
          { type: 'data', name: 'weather', data: { temp: 22 } },
          {
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'search',
            input: { q: 'Halo' },
          },
          {
            type: 'tool-result',
            toolCallId: 'call_1',
            toolName: 'search',
            result: { ok: true },
          },
        ],
      },
      toolEvents: [
        {
          type: 'tool-call',
          toolCallId: 'call_1',
          externalStatus: 'completed',
        },
        { type: 'tool-result', toolCallId: 'call_1' },
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
      type: 'tool-call',
      toolCallId: 'call_1',
      toolName: 'search',
      input: { q: 'Halo' },
    })
    applyWorkbenchUIMessageChunk(message, {
      type: 'tool-error',
      toolCallId: 'call_1',
      toolName: 'search',
      errorText: 'failed',
    })

    expect(message.toolEvents).toMatchObject([
      {
        type: 'tool-call',
        toolCallId: 'call_1',
        externalStatus: 'failed',
      },
      { type: 'tool-error', toolCallId: 'call_1' },
    ])
  })

  it('projects UI Message approval responses onto approval requests', () => {
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
            type: 'tool-approval-request',
            approvalId: 'approval_1',
            toolCallId: 'call_1',
            toolName: 'pay',
          },
          {
            type: 'tool-approval-response',
            approvalId: 'approval_1',
            approved: false,
            reason: 'Denied',
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
            type: 'tool-call',
            toolCallId: 'call_1',
            toolName: 'halo_test_info',
            input: { query: '这是一个测试调用' },
          },
          {
            type: 'tool-approval-request',
            approvalId: 'approval_1',
            toolCallId: 'call_1',
            toolName: 'halo_test_info',
            input: { query: '这是一个测试调用' },
          },
        ],
      },
    }

    applyWorkbenchUIMessageChunk(message, { type: 'finish' })

    expect(message.toolEvents).toMatchObject([
      {
        type: 'tool-call',
        toolCallId: 'call_1',
        externalStatus: undefined,
      },
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

describe('toToolEvent', () => {
  it('converts tool stream parts to compact workbench events', () => {
    expect(
      toToolEvent({
        type: 'tool-call',
        toolCallId: 'call_1',
        toolName: 'halo_test_info',
        input: { query: 'hello' },
      }),
    ).toMatchObject({
      type: 'tool-call',
      toolCallId: 'call_1',
      toolName: 'halo_test_info',
      externalStatus: 'pending',
      summary: '{"query":"hello"}',
    })

    expect(toToolEvent({ type: 'text-delta', delta: 'hello' })).toBeUndefined()

    expect(
      toToolEvent({
        type: 'tool-approval-request',
        approvalId: 'approval_call_1',
        toolCallId: 'call_1',
        toolName: 'run',
        stepIndex: 1,
        input: { command: 'rm file' },
      }),
    ).toMatchObject({
      type: 'tool-approval-request',
      approvalId: 'approval_call_1',
      stepIndex: 1,
      approvalStatus: 'pending',
      summary: '{"command":"rm file"}',
    })
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
