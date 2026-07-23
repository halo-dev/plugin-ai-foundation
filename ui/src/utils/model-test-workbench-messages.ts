import type {
  UIMessagePart,
  WorkbenchFileReference,
  WorkbenchMessage,
} from '@/utils/model-test-workbench'
import type {
  FilePart as HaloFilePart,
  UIMessage as HaloUIMessage,
  UIMessagePart as HaloUIMessagePart,
} from '@halo-dev/ai-foundation-sdk'
import { utils } from '@halo-dev/ui-shared'

export function workbenchMessagesToHalo(
  items: WorkbenchMessage[],
): HaloUIMessage<Record<string, unknown>>[] {
  return items
    .map(workbenchMessageToHalo)
    .filter((message): message is HaloUIMessage<Record<string, unknown>> => !!message)
}

export function workbenchMessageToHalo(
  message: WorkbenchMessage,
): HaloUIMessage<Record<string, unknown>> | undefined {
  if (message.uiMessage?.parts.length) {
    return {
      id: message.uiMessage.id,
      role: message.uiMessage.role === 'ASSISTANT' ? 'assistant' : 'user',
      parts: message.uiMessage.parts.map(workbenchPartToHalo) as HaloUIMessagePart[],
      metadata: message.uiMessage.metadata,
    }
  }
  const content = message.content.trim()
  if (!content || (message.role === 'assistant' && message.state === 'error')) return undefined
  return {
    id: message.id,
    role: message.role,
    parts: [{ type: 'text', id: `${message.id}-text`, text: content }],
  }
}

function workbenchPartToHalo(part: UIMessagePart): HaloUIMessagePart {
  if (part.type === 'file') {
    const id = String(part.id || part.fileId || utils.id.uuid())
    return { ...part, id, fileId: String(part.fileId || id) } as HaloUIMessagePart
  }
  return { ...part } as HaloUIMessagePart
}

export function haloMessageToWorkbench(
  message: HaloUIMessage<Record<string, unknown>>,
): NonNullable<WorkbenchMessage['uiMessage']> {
  return {
    id: message.id,
    role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
    parts: message.parts.map(haloPartToWorkbench),
    metadata: message.metadata,
  }
}

function haloPartToWorkbench(part: HaloUIMessagePart): UIMessagePart {
  if (part.type === 'file') {
    return { ...part, fileId: part.fileId || part.id } as UIMessagePart
  }
  return { ...part } as UIMessagePart
}

export function workbenchFilesFromParts(files: HaloFilePart[]): WorkbenchFileReference[] {
  return files.map((file) => ({
    id: file.id,
    fileId: file.fileId || file.id,
    title: file.title,
    url: file.url,
    mediaType: file.mediaType,
    data: file.data,
    providerMetadata: file.providerMetadata,
  }))
}

export function cloneWorkbenchMessage(message: WorkbenchMessage): WorkbenchMessage {
  return {
    ...message,
    uiMessage: message.uiMessage
      ? {
          ...message.uiMessage,
          metadata: message.uiMessage.metadata ? { ...message.uiMessage.metadata } : undefined,
          parts: message.uiMessage.parts.map((part) => ({ ...part })),
        }
      : undefined,
    toolEvents: message.toolEvents?.map((event) => ({ ...event })),
    toolInputStreamDiagnostics: message.toolInputStreamDiagnostics?.map((diagnostic) => ({
      ...diagnostic,
      events: diagnostic.events.map((event) => ({ ...event })),
      protocolIssues: [...diagnostic.protocolIssues],
    })),
    warnings: message.warnings?.map((warning) => ({ ...warning })),
    files: message.files?.map((file) => ({ ...file })),
    transientData: message.transientData ? { ...message.transientData } : undefined,
  }
}
