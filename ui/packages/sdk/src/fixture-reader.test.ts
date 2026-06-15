import { describe, expect, it } from '@rstest/core'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { applyUIMessageChunk, createUIMessageReducer } from './message-reducer'
import type { UIMessageChunk } from './types'

export interface UIMessageFixture {
  name: string
  description: string
  chunks: Array<Record<string, unknown>>
  expectedMessage: Record<string, unknown>
  expectedTransientEvents?: Array<Record<string, unknown>>
  expectedTerminal?: Record<string, unknown>
}

const fixtureNames = ['data-parts', 'tool-lifecycle', 'terminal-states'] as const

export async function readUIMessageFixture(
  name: (typeof fixtureNames)[number],
): Promise<UIMessageFixture> {
  const sourceDir = dirname(fileURLToPath(import.meta.url))
  const fixturePath = resolve(sourceDir, '../../../../test-fixtures/ui-message', `${name}.json`)
  return JSON.parse(await readFile(fixturePath, 'utf8')) as UIMessageFixture
}

describe('shared UI message fixtures', () => {
  it('loads the minimum shared fixture set', async () => {
    const fixtures = await Promise.all(fixtureNames.map(readUIMessageFixture))

    expect(fixtures.map((fixture) => fixture.name)).toEqual([...fixtureNames])
    for (const fixture of fixtures) {
      expect(fixture.chunks.length).toBeGreaterThan(0)
      expect(fixture.expectedMessage).toBeTruthy()
    }
  })

  it('reduces shared fixture chunks to expected messages and terminal state', async () => {
    for (const fixture of await Promise.all(fixtureNames.map(readUIMessageFixture))) {
      const reducer = createUIMessageReducer({ metadata: {} })
      for (const chunk of fixture.chunks) {
        applyUIMessageChunk(reducer, chunk as unknown as UIMessageChunk)
      }

      expect(normalize(reducer.message)).toEqual(fixture.expectedMessage)
      expect(normalize(reducer.terminal)).toEqual(fixture.expectedTerminal ?? {})
    }
  })
})

function normalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalize)
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.fromEntries(
    Object.entries(value)
      .filter(
        ([key, entry]) => entry !== undefined && !(key === 'transientData' && entry === false),
      )
      .map(([key, entry]) => [key, normalize(entry)]),
  )
}
