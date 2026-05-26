import { AiModelSpecFeaturesEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  buildTestChatRequest,
  filterEnabledChatModels,
  flushSseJsonBuffer,
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
      { id: '2', role: 'assistant', content: 'Hi', state: 'done' },
      { id: '3', role: 'assistant', content: 'Failed', state: 'error' },
    ]

    expect(
      buildTestChatRequest(messages, {
        systemPrompt: 'You are concise.',
        temperature: 0.2,
        topP: 0.9,
        maxOutputTokens: 128,
        maxSteps: 2,
        providerOptions: { openai: { seed: 42 } },
      }),
    ).toMatchObject({
      system: 'You are concise.',
      messages: [
        { role: 'USER', content: [{ type: 'text', text: 'Hello' }] },
        { role: 'ASSISTANT', content: [{ type: 'text', text: 'Hi' }] },
      ],
      temperature: 0.2,
      topP: 0.9,
      maxOutputTokens: 128,
      maxSteps: 2,
      providerOptions: { openai: { seed: 42 } },
    })
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
        'data: {"type":"tool-result","toolCallId":"call_1","toolName":"weather","result":{"temperature":22}}',
        'data: {"type":"tool-error","toolCallId":"call_2","toolName":"search","errorText":"failed"}',
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
      'tool-result',
      'tool-error',
      'unknown-provider-part',
      'text-delta',
      'finish',
    ])
    expect(result.chunks[2]).toMatchObject({
      toolCallId: 'call_1',
      toolName: 'weather',
      input: { location: 'SF' },
    })
    expect(result.chunks[3]).toMatchObject({
      toolCallId: 'call_1',
      toolName: 'weather',
      result: { temperature: 22 },
    })
    expect(result.chunks[4]).toMatchObject({
      toolCallId: 'call_2',
      toolName: 'search',
      errorText: 'failed',
    })
    expect(result.chunks.filter(isRenderableTextDelta).map((chunk) => chunk.delta)).toEqual([
      '**Hi**',
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
