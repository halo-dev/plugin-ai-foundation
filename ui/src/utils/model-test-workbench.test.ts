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
        'data: {"type":"unknown-provider-part"}',
        'data: {"type":"text-delta","delta":"**Hi**"}',
        'data: {"type":"finish"}',
        '',
      ].join('\n'),
    )

    expect(result.chunks.map((chunk) => chunk.type)).toEqual([
      'start-step',
      'raw',
      'unknown-provider-part',
      'text-delta',
      'finish',
    ])
    expect(result.chunks.filter(isRenderableTextDelta).map((chunk) => chunk.delta)).toEqual([
      '**Hi**',
    ])
    expect(result.chunks.filter(isTerminalTextStreamPart).map((chunk) => chunk.type)).toEqual([
      'finish',
    ])
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
