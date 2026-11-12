import { describe, expect, it } from 'vitest'
import type { WorkbenchMessage } from './model-test-workbench'
import {
  applyAssistantMessageEvent,
  beginAssistantMessage,
  resumeAssistantMessage,
} from './model-test-workbench-message-state'

function message(): WorkbenchMessage {
  return {
    id: 'message-1',
    role: 'assistant',
    content: 'old content',
    reasoningContent: 'old reasoning',
    reasoningState: 'streaming',
    toolEvents: [{ id: 'event-1', type: 'tool-call', summary: 'stale event' }],
    toolInputStreamDiagnostics: [],
    transientData: { stale: true },
    warnings: [{ message: 'old warning' }],
  }
}

describe('model test workbench message state', () => {
  it('resets a message into a streaming request', () => {
    const target = message()

    beginAssistantMessage(target, 'request-1')

    expect(target).toMatchObject({
      content: '',
      reasoningContent: undefined,
      reasoningState: undefined,
      toolEvents: undefined,
      toolInputStreamDiagnostics: undefined,
      transientData: undefined,
      warnings: undefined,
      state: 'streaming',
      requestId: 'request-1',
    })
  })

  it('resumes a completed message without clearing content or tool state', () => {
    const target = { ...message(), state: 'done' as const, requestId: 'request-1' }

    resumeAssistantMessage(target, 'request-2')

    expect(target).toMatchObject({
      content: 'old content',
      toolEvents: [{ id: 'event-1', type: 'tool-call', summary: 'stale event' }],
      state: 'streaming',
      requestId: 'request-2',
    })
  })

  it('finishes a streaming message and closes reasoning', () => {
    const target = { ...message(), state: 'streaming' as const }

    expect(applyAssistantMessageEvent(target, { type: 'finish' })).toBe(true)
    expect(target.state).toBe('done')
    expect(target.reasoningState).toBe('done')
  })

  it('does not finish a message twice', () => {
    const target = { ...message(), state: 'done' as const }

    expect(applyAssistantMessageEvent(target, { type: 'finish' })).toBe(false)
    expect(target.state).toBe('done')
  })

  it('stops a streaming message', () => {
    const target = { ...message(), state: 'streaming' as const }

    expect(applyAssistantMessageEvent(target, { type: 'stop' })).toBe(true)
    expect(target.state).toBe('stopped')
    expect(target.reasoningState).toBe('done')
  })

  it('does not stop a completed message', () => {
    const target = { ...message(), state: 'done' as const }

    expect(applyAssistantMessageEvent(target, { type: 'stop' })).toBe(false)
    expect(target.state).toBe('done')
  })

  it('appends an error and closes reasoning', () => {
    const target = { ...message(), content: 'partial', state: 'streaming' as const }

    expect(applyAssistantMessageEvent(target, { type: 'fail', content: '请求失败' })).toBe(true)
    expect(target.content).toBe('partial\n\n请求失败')
    expect(target.state).toBe('error')
    expect(target.reasoningState).toBe('done')
  })

  it('ignores duplicate and empty errors', () => {
    const target = { ...message(), content: '请求失败', state: 'error' as const }

    expect(applyAssistantMessageEvent(target, { type: 'fail', content: '请求失败' })).toBe(false)
    expect(applyAssistantMessageEvent(target, { type: 'fail', content: '  ' })).toBe(false)
    expect(target.content).toBe('请求失败')
  })

  it('allows a late error after the message completed', () => {
    const target = { ...message(), content: 'answer', state: 'done' as const }

    expect(applyAssistantMessageEvent(target, { type: 'fail', content: '请求失败' })).toBe(true)
    expect(target.content).toBe('answer\n\n请求失败')
    expect(target.state).toBe('error')
  })
})
