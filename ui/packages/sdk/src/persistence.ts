import { AIUIProtocolError } from './errors'
import { validateRuntimeSchema, type DataPartSchemas, type MessageMetadataSchema } from './schema'
import type { DataPart, ToolPart, UIMessage, UIMessagePart, UIMessageRole } from './types'

export interface PruneMessagesOptions {
  maxMessages?: number
  removePendingToolParts?: boolean
}

export interface UIMessageValidationIssue {
  path: string
  code: string
  message: string
}

export interface ValidateUIMessagesOptions<METADATA = unknown> {
  messageMetadataSchema?: MessageMetadataSchema<METADATA>
  dataPartSchemas?: DataPartSchemas
}

export class AIUIMessageValidationError extends AIUIProtocolError {
  readonly issues: UIMessageValidationIssue[]

  constructor(issues: UIMessageValidationIssue[]) {
    super(`Invalid UI messages: ${issues.map((issue) => issue.message).join('; ')}`)
    this.name = 'AIUIMessageValidationError'
    this.issues = issues
  }
}

export function pruneMessages<METADATA = unknown>(
  messages: UIMessage<METADATA>[],
  options: PruneMessagesOptions = {},
): UIMessage<METADATA>[] {
  const maxMessages = options.maxMessages
  const retained =
    typeof maxMessages === 'number' && maxMessages >= 0
      ? messages.slice(Math.max(0, messages.length - maxMessages))
      : [...messages]
  const removePendingToolParts = options.removePendingToolParts !== false
  const pruned: UIMessage<METADATA>[] = []

  for (const message of retained) {
    const parts = removePendingToolParts
      ? message.parts.filter((part) => !isPendingToolPart(part))
      : [...message.parts]
    if (parts.length === 0) {
      continue
    }
    pruned.push({ ...message, parts: parts.map((part) => ({ ...part }) as UIMessagePart) })
  }

  return pruned
}

export function validateUIMessages<METADATA = unknown>(
  messages: UIMessage<METADATA>[],
  options: ValidateUIMessagesOptions<METADATA> = {},
): UIMessageValidationIssue[] {
  const issues: UIMessageValidationIssue[] = []
  if (!Array.isArray(messages)) {
    return [issue('$', 'messages.type', 'UI messages must be an array.')]
  }

  messages.forEach((message, messageIndex) => {
    const messagePath = `$[${messageIndex}]`
    validateMessage(message, messagePath, options, issues)
  })
  return issues
}

export function assertValidUIMessages<METADATA = unknown>(
  messages: UIMessage<METADATA>[],
  options: ValidateUIMessagesOptions<METADATA> = {},
): void {
  const issues = validateUIMessages(messages, options)
  if (issues.length > 0) {
    throw new AIUIMessageValidationError(issues)
  }
}

function validateMessage<METADATA>(
  message: UIMessage<METADATA>,
  path: string,
  options: ValidateUIMessagesOptions<METADATA>,
  issues: UIMessageValidationIssue[],
) {
  if (!isRecord(message)) {
    issues.push(issue(path, 'message.type', 'UI message must be an object.'))
    return
  }
  if (!isNonEmptyString(message.id)) {
    issues.push(issue(`${path}.id`, 'message.id.required', 'UI message id is required.'))
  }
  if (!isRole(message.role)) {
    issues.push(issue(`${path}.role`, 'message.role.invalid', 'UI message role is invalid.'))
  }
  if (!Array.isArray(message.parts)) {
    issues.push(issue(`${path}.parts`, 'message.parts.type', 'UI message parts must be an array.'))
    return
  }
  if (message.metadata !== undefined && options.messageMetadataSchema) {
    try {
      validateRuntimeSchema(message.metadata, options.messageMetadataSchema, {
        target: 'message-metadata',
      })
    } catch (error) {
      issues.push(issue(`${path}.metadata`, 'message.metadata.schema', errorMessage(error)))
    }
  }
  message.parts.forEach((part, partIndex) => {
    validatePart(part, `${path}.parts[${partIndex}]`, options, issues)
  })
}

function validatePart(
  part: UIMessagePart,
  path: string,
  options: ValidateUIMessagesOptions,
  issues: UIMessageValidationIssue[],
) {
  if (!isRecord(part)) {
    issues.push(issue(path, 'part.type', 'UI message part must be an object.'))
    return
  }
  if (!isNonEmptyString(part.type)) {
    issues.push(issue(`${path}.type`, 'part.type.required', 'UI message part type is required.'))
    return
  }
  if (isDataPart(part)) {
    validateDataPart(part, path, options, issues)
    return
  }
  if (isToolPart(part)) {
    validateToolPart(part, path, issues)
    return
  }
  switch (part.type) {
    case 'text':
      requireString(
        part.id,
        `${path}.id`,
        'part.text.id.required',
        'Text part id is required.',
        issues,
      )
      requireString(
        part.text,
        `${path}.text`,
        'part.text.text.required',
        'Text part text is required.',
        issues,
      )
      break
    case 'reasoning':
      requireString(
        part.id,
        `${path}.id`,
        'part.reasoning.id.required',
        'Reasoning part id is required.',
        issues,
      )
      requireString(
        part.text,
        `${path}.text`,
        'part.reasoning.text.required',
        'Reasoning part text is required.',
        issues,
      )
      break
    case 'source-url':
      requireString(
        part.sourceId,
        `${path}.sourceId`,
        'part.source-url.id.required',
        'Source URL part sourceId is required.',
        issues,
      )
      requireString(
        part.url,
        `${path}.url`,
        'part.source-url.url.required',
        'Source URL part url is required.',
        issues,
      )
      break
    case 'source-document':
      requireString(
        part.sourceId,
        `${path}.sourceId`,
        'part.source-document.id.required',
        'Document source part sourceId is required.',
        issues,
      )
      requireString(
        part.mediaType,
        `${path}.mediaType`,
        'part.source-document.media-type.required',
        'Document source part mediaType is required.',
        issues,
      )
      requireString(
        part.title,
        `${path}.title`,
        'part.source-document.title.required',
        'Document source part title is required.',
        issues,
      )
      break
    case 'file':
      requireString(
        part.id,
        `${path}.id`,
        'part.file.id.required',
        'File part id is required.',
        issues,
      )
      break
    default:
      issues.push(
        issue(
          `${path}.type`,
          'part.type.unsupported',
          `Unsupported UI message part type: ${(part as { type: string }).type}.`,
        ),
      )
  }
}

function validateDataPart(
  part: DataPart,
  path: string,
  options: ValidateUIMessagesOptions,
  issues: UIMessageValidationIssue[],
) {
  const expectedType = `data-${part.name}`
  requireString(part.id, `${path}.id`, 'part.data.id.required', 'Data part id is required.', issues)
  requireString(
    part.name,
    `${path}.name`,
    'part.data.name.required',
    'Data part name is required.',
    issues,
  )
  if (part.type !== expectedType) {
    issues.push(
      issue(`${path}.type`, 'part.data.type.invalid', `Data part type must be ${expectedType}.`),
    )
  }
  const schema = options.dataPartSchemas?.[part.name]
  if (schema) {
    try {
      validateRuntimeSchema(part.data, schema, {
        target: 'data-part',
        partType: part.type,
        partName: part.name,
        partId: part.id,
      })
    } catch (error) {
      issues.push(issue(`${path}.data`, 'part.data.schema', errorMessage(error)))
    }
  }
}

function validateToolPart(part: ToolPart, path: string, issues: UIMessageValidationIssue[]) {
  const expectedType = `tool-${part.toolName}`
  requireString(
    part.toolCallId,
    `${path}.toolCallId`,
    'part.tool.id.required',
    'Tool part toolCallId is required.',
    issues,
  )
  requireString(
    part.toolName,
    `${path}.toolName`,
    'part.tool.name.required',
    'Tool part toolName is required.',
    issues,
  )
  if (part.type !== expectedType) {
    issues.push(
      issue(`${path}.type`, 'part.tool.type.invalid', `Tool part type must be ${expectedType}.`),
    )
  }
  if (!isNonEmptyString(part.state)) {
    issues.push(issue(`${path}.state`, 'part.tool.state.required', 'Tool part state is required.'))
  }
  if (part.state === 'output-error' && !isNonEmptyString(part.errorText)) {
    issues.push(
      issue(
        `${path}.errorText`,
        'part.tool.error.required',
        'Tool output-error part errorText is required.',
      ),
    )
  }
  if (
    (part.state === 'approval-requested' || part.state === 'approval-responded') &&
    !part.approval?.id
  ) {
    issues.push(
      issue(
        `${path}.approval.id`,
        'part.tool.approval.id.required',
        'Tool approval id is required.',
      ),
    )
  }
  if (part.state === 'approval-responded' && typeof part.approval?.approved !== 'boolean') {
    issues.push(
      issue(
        `${path}.approval.approved`,
        'part.tool.approval.approved.required',
        'Tool approval response approved value is required.',
      ),
    )
  }
}

function isPendingToolPart(part: UIMessagePart): boolean {
  return (
    isToolPart(part) &&
    (part.state === 'input-streaming' ||
      part.state === 'input-available' ||
      part.state === 'approval-requested')
  )
}

function isDataPart(part: UIMessagePart): part is DataPart {
  return part.type.startsWith('data-')
}

function isToolPart(part: UIMessagePart): part is ToolPart {
  return part.type.startsWith('tool-')
}

function isRole(value: unknown): value is UIMessageRole {
  return value === 'system' || value === 'user' || value === 'assistant'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

function requireString(
  value: unknown,
  path: string,
  code: string,
  message: string,
  issues: UIMessageValidationIssue[],
) {
  if (!isNonEmptyString(value)) {
    issues.push(issue(path, code, message))
  }
}

function issue(path: string, code: string, message: string): UIMessageValidationIssue {
  return { path, code, message }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
