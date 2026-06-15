import { AIUIError } from './errors'
import type { UIMessageChunk } from './types'

export const HALO_UI_MESSAGE_STREAM_HEADER = 'X-Halo-AI-UI-Message-Stream'
export const HALO_UI_MESSAGE_STREAM_VERSION = 'v1'
export const DONE_MARKER = '[DONE]'

export function assertHaloUIMessageStreamResponse(response: Response): void {
  const version = response.headers.get(HALO_UI_MESSAGE_STREAM_HEADER)
  if (version && version !== HALO_UI_MESSAGE_STREAM_VERSION) {
    throw new AIUIError(`Unsupported Halo UI message stream version: ${version}`, {
      response,
      status: response.status,
    })
  }
}

export async function* readTextStream(stream: ReadableStream<Uint8Array>): AsyncIterable<string> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      if (value) {
        const text = decoder.decode(value, { stream: true })
        if (text) {
          yield text
        }
      }
    }
    const tail = decoder.decode()
    if (tail) {
      yield tail
    }
  } finally {
    reader.releaseLock()
  }
}

export async function* readUIMessageSSEStream(
  stream: ReadableStream<Uint8Array>,
): AsyncIterable<UIMessageChunk> {
  let buffer = ''
  for await (const text of readTextStream(stream)) {
    buffer += text
    const normalized = buffer.replace(/\r\n/g, '\n')
    const frames = normalized.split('\n\n')
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      const chunk = parseSSEFrame(frame)
      if (chunk === DONE_MARKER || chunk == null) {
        continue
      }
      yield chunk
    }
  }

  const trailing = buffer.trim()
  if (trailing) {
    const chunk = parseSSEFrame(trailing)
    if (chunk !== DONE_MARKER && chunk != null) {
      yield chunk
    }
  }
}

function parseSSEFrame(frame: string): UIMessageChunk | typeof DONE_MARKER | undefined {
  const dataLines = frame
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())

  if (!dataLines.length) {
    return undefined
  }

  const payload = dataLines.join('\n').trim()
  if (!payload || payload === DONE_MARKER) {
    return DONE_MARKER
  }

  try {
    return JSON.parse(payload) as UIMessageChunk
  } catch (error) {
    throw new AIUIError('Failed to parse Halo UI message stream chunk.', { cause: error })
  }
}

export async function collectText(stream: AsyncIterable<string>): Promise<string> {
  let result = ''
  for await (const chunk of stream) {
    result += chunk
  }
  return result
}
