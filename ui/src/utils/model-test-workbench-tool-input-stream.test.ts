import type { UIMessageChunk } from '@halo-dev/ai-foundation-sdk'
import { describe, expect, it } from '@rstest/core'
import {
  classifyToolInputStream,
  recordToolInputStreamChunk,
  type ToolInputStreamDiagnostic,
} from './model-test-workbench-tool-input-stream'

describe('recordToolInputStreamChunk', () => {
  it('records provider-native tool input deltas in protocol order', () => {
    const chunks: UIMessageChunk[] = [
      { type: 'tool-input-start', toolCallId: 'call-1', toolName: 'stream_test' },
      { type: 'tool-input-delta', toolCallId: 'call-1', inputTextDelta: '{"message":' },
      { type: 'tool-input-delta', toolCallId: 'call-1', inputTextDelta: '"hello"}' },
      {
        type: 'tool-input-available',
        toolCallId: 'call-1',
        toolName: 'stream_test',
        input: { message: 'hello' },
      },
    ]

    const diagnostics = chunks.reduce(recordToolInputStreamChunk, [])

    expect(diagnostics).toEqual([
      expect.objectContaining({
        toolCallId: 'call-1',
        toolName: 'stream_test',
        startCount: 1,
        deltaCount: 2,
        availableCount: 1,
        inputText: '{"message":"hello"}',
        input: { message: 'hello' },
        protocolIssues: [],
      }),
    ])
    expect(diagnostics[0]?.events.map((event) => event.type)).toEqual([
      'tool-input-start',
      'tool-input-delta',
      'tool-input-delta',
      'tool-input-available',
    ])
  })

  it('recognizes final-only input without fabricating start or delta', () => {
    const diagnostics = recordToolInputStreamChunk([], {
      type: 'tool-input-available',
      toolCallId: 'call-final',
      toolName: 'stream_test',
      input: { message: 'complete' },
    })

    expect(diagnostics[0]).toMatchObject({
      startCount: 0,
      deltaCount: 0,
      availableCount: 1,
      protocolIssues: [],
    })
  })

  it('reports invalid ordering instead of hiding it', () => {
    const chunks: UIMessageChunk[] = [
      { type: 'tool-input-delta', toolCallId: 'call-1', inputTextDelta: '{}' },
      {
        type: 'tool-input-available',
        toolCallId: 'call-1',
        toolName: 'stream_test',
        input: {},
      },
      { type: 'tool-input-delta', toolCallId: 'call-1', inputTextDelta: 'late' },
    ]
    const diagnostics = chunks.reduce(recordToolInputStreamChunk, [])

    expect(diagnostics[0]?.protocolIssues).toEqual([
      'tool-input-delta 缺少前置 start',
      '流式入参完成时仍缺少 start',
      'tool-input-delta 出现在终态之后',
    ])
  })
})

describe('classifyToolInputStream', () => {
  const diagnostic = (
    counts: Partial<
      Pick<ToolInputStreamDiagnostic, 'startCount' | 'deltaCount' | 'availableCount' | 'errorCount'>
    >,
  ): ToolInputStreamDiagnostic => ({
    toolCallId: 'call-1',
    startCount: 0,
    deltaCount: 0,
    availableCount: 0,
    errorCount: 0,
    inputText: '',
    events: [],
    droppedEventCount: 0,
    protocolIssues: [],
    ...counts,
  })

  it('classifies each observable input lifecycle outcome', () => {
    expect(classifyToolInputStream(diagnostic({ deltaCount: 1, errorCount: 1 }))).toBe(
      'provider-native-delta',
    )
    expect(classifyToolInputStream(diagnostic({ availableCount: 1 }))).toBe('final-only')
    expect(classifyToolInputStream(diagnostic({ errorCount: 1 }))).toBe('input-error')
    expect(classifyToolInputStream(diagnostic({}))).toBe('pending')
  })
})
