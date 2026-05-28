import { AiModelSpecFeaturesEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel, OutputSpec } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  buildTestChatRequest,
  buildOutputSpec,
  buildReasoningOptions,
  filterEnabledChatModels,
  flushSseJsonBuffer,
  isRenderableReasoningDelta,
  isRenderableTextDelta,
  isTerminalTextStreamPart,
  parseProviderOptionsJson,
  parseSseJsonLines,
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
      buildTestChatRequest([
        { id: '1', role: 'user', content: 'Hello' },
        { id: '2', role: 'assistant', content: 'Hi', reasoningContent: 'Think', state: 'done' },
        { id: '3', role: 'user', content: 'Continue' },
      ], {}),
    ).toMatchObject({
      messages: [
        { role: 'USER', content: [{ type: 'text', text: 'Hello' }] },
        { role: 'ASSISTANT', content: [{ type: 'text', text: 'Hi' }] },
        { role: 'USER', content: [{ type: 'text', text: 'Continue' }] },
      ],
    })
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
      summary: '{"query":"hello"}',
    })

    expect(toToolEvent({ type: 'text-delta', delta: 'hello' })).toBeUndefined()
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
