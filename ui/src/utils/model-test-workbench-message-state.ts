import type { WorkbenchMessage } from './model-test-workbench'

export type AssistantMessageEvent =
  | { type: 'finish' }
  | { type: 'stop' }
  | { type: 'fail'; content: string }

export function beginAssistantMessage(message: WorkbenchMessage, requestId?: string) {
  message.content = ''
  message.reasoningContent = undefined
  message.reasoningState = undefined
  message.toolEvents = undefined
  message.toolInputStreamDiagnostics = undefined
  message.transientData = undefined
  message.warnings = undefined
  message.state = 'streaming'
  message.requestId = requestId
}

export function resumeAssistantMessage(message: WorkbenchMessage, requestId: string) {
  message.requestId = requestId
  message.state = 'streaming'
}

export function applyAssistantMessageEvent(
  message: WorkbenchMessage,
  event: AssistantMessageEvent,
): boolean {
  if (event.type === 'fail') {
    const content = event.content.trim()
    if (!content) return false
    const current = message.content.trim()
    if (message.state === 'error' && current.includes(content)) return false
    message.content = current ? `${current}\n\n${content}` : content
    message.state = 'error'
    endReasoning(message)
    return true
  }

  if (message.state !== 'streaming') return false
  message.state = event.type === 'finish' ? 'done' : 'stopped'
  endReasoning(message)
  return true
}

function endReasoning(message: WorkbenchMessage) {
  if (message.reasoningState === 'streaming') message.reasoningState = 'done'
}
