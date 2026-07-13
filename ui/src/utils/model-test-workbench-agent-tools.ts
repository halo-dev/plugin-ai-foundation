import type { ModelOption } from '@/api/generated'
import type { WorkbenchTestMode } from '@/composables/workbench/use-workbench-models'
import type { WorkbenchMessage } from '@/utils/model-test-workbench'
import type { ToolPart } from '@halo-dev/ai-foundation-sdk'

interface WorkbenchPageContext {
  selectedModel?: ModelOption
  testMode: WorkbenchTestMode
  messages: WorkbenchMessage[]
}

export function isWorkbenchAgentTool(part: ToolPart) {
  return part.toolName === 'get_current_page_context' || part.toolName === 'halo_agent_test_action'
}

export async function executeWorkbenchAgentTool(
  part: ToolPart,
  context: WorkbenchPageContext,
): Promise<Record<string, unknown>> {
  switch (part.toolName) {
    case 'get_current_page_context':
      return currentWorkbenchPageContext(context)
    case 'halo_agent_test_action':
      return executeWorkbenchAgentTestAction(part.input ?? {})
    default:
      throw new Error(`未知 Agent 测试工具：${part.toolName}`)
  }
}

function currentWorkbenchPageContext(context: WorkbenchPageContext): Record<string, unknown> {
  const lastUserMessage = [...context.messages].reverse().find((message) => message.role === 'user')
  const selected = context.selectedModel
  return {
    ok: true,
    url: window.location.href,
    title: document.title,
    selectedText: window.getSelection()?.toString() || '',
    channel: 'console-model-test-workbench',
    page: {
      name: 'AI Foundation 模型测试工作台',
      mode: context.testMode,
      capabilities: [
        'model-selection',
        'chat-stream-test',
        'rag-stream-test',
        'image-generation-test',
      ],
    },
    model: selected
      ? {
          name: selected.name,
          displayName: selected.displayName,
          modelId: selected.modelId,
          provider: selected.provider?.displayName || selected.provider?.name,
        }
      : undefined,
    conversation: {
      messages: context.messages.length,
      lastUserText: lastUserMessage?.content || '',
    },
    nextAction:
      '如果需要验证客户端工具续跑，可以调用 halo_agent_test_action；否则直接根据当前上下文回答用户。',
  }
}

function executeWorkbenchAgentTestAction(input: Record<string, unknown>): Record<string, unknown> {
  const message = typeof input.message === 'string' ? input.message.trim() : ''
  if (!message) {
    return {
      ok: false,
      code: 'EMPTY_AGENT_TEST_MESSAGE',
      message: '缺少需要回显的测试内容',
    }
  }
  return {
    ok: true,
    echo: message,
    target: 'console-model-test-workbench',
    message: '后台测试工作台前端 Agent 工具执行成功。',
  }
}
