import { AiModelSpecFeaturesEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  buildTestChatRequest,
  filterEnabledChatModels,
  flushSseJsonBuffer,
  parseProviderOptionsJson,
  parseSseJsonLines,
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
    expect(parseProviderOptionsJson('{ "seed": 42 }').value).toEqual({ seed: 42 })
  })

  it('rejects invalid or non-object values', () => {
    expect(parseProviderOptionsJson('{').error).toBeTruthy()
    expect(parseProviderOptionsJson('[]').error).toBeTruthy()
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
        maxTokens: 128,
        providerOptions: { seed: 42 },
      }),
    ).toMatchObject({
      messages: [
        { role: 'system', content: 'You are concise.' },
        { role: 'user', content: 'Hello' },
        { role: 'assistant', content: 'Hi' },
      ],
      temperature: 0.2,
      topP: 0.9,
      maxTokens: 128,
      providerOptions: { seed: 42 },
    })
  })
})

describe('parseSseJsonLines', () => {
  it('parses complete data lines and preserves partial buffer', () => {
    const result = parseSseJsonLines<{ content: string }>(
      '',
      'data: {"content":"Hel"}\n\ndata: {"content":"lo"}',
    )

    expect(result.chunks).toEqual([{ content: 'Hel' }])
    expect(flushSseJsonBuffer<{ content: string }>(result.buffer)).toEqual([{ content: 'lo' }])
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
