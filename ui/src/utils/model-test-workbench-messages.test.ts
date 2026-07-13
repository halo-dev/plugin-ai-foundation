import type { WorkbenchMessage } from '@/utils/model-test-workbench'
import { describe, expect, it } from '@rstest/core'
import {
  cloneWorkbenchMessage,
  haloMessageToWorkbench,
  workbenchMessageToHalo,
} from './model-test-workbench-messages'

describe('model test workbench message adapters', () => {
  it('omits empty and terminal error assistant messages from request history', () => {
    expect(
      workbenchMessageToHalo({ id: 'a-1', role: 'assistant', content: '', state: 'done' }),
    ).toBeUndefined()
    expect(
      workbenchMessageToHalo({
        id: 'a-2',
        role: 'assistant',
        content: 'request failed',
        state: 'error',
      }),
    ).toBeUndefined()
  })

  it('preserves file identifiers while converting SDK messages', () => {
    expect(
      haloMessageToWorkbench({
        id: 'assistant-1',
        role: 'assistant',
        parts: [{ type: 'file', id: 'file-1', mediaType: 'image/png' }],
      }),
    ).toEqual({
      id: 'assistant-1',
      role: 'ASSISTANT',
      parts: [{ type: 'file', id: 'file-1', fileId: 'file-1', mediaType: 'image/png' }],
      metadata: undefined,
    })
  })

  it('clones nested mutable message state for safe replacement', () => {
    const message: WorkbenchMessage = {
      id: 'a-1',
      role: 'assistant',
      content: 'answer',
      warnings: [{ code: 'warning' }],
      toolEvents: [{ id: 'tool-1', type: 'tool-call', summary: 'tool' }],
    }
    const cloned = cloneWorkbenchMessage(message)
    cloned.warnings?.push({ code: 'next' })
    cloned.toolEvents![0].summary = 'changed'

    expect(message.warnings).toHaveLength(1)
    expect(message.toolEvents?.[0].summary).toBe('tool')
  })
})
