import { describe, expect, it, vi } from 'vitest'
import { AIUIError, AIUIProtocolError } from './errors'
import { listTools } from './tools'
import type { FetchFunction } from './types'

const descriptor = {
  id: 'ai-foundation/search_site',
  pluginName: 'ai-foundation',
  localName: 'search_site',
  name: 'ai_foundation_search_site',
  displayName: '站内搜索',
  enabled: true,
  configured: true,
  status: 'ENABLED',
}

describe('listTools', () => {
  it('uses the Console catalog with injectable transport, authentication, and cancellation', async () => {
    const fetch = vi.fn<FetchFunction>(async () => new Response(JSON.stringify([descriptor])))
    const controller = new AbortController()
    const headers = { Authorization: 'Bearer test' }
    expect(
      await listTools({ fetch, headers, credentials: 'include', abortSignal: controller.signal }),
    ).toEqual([descriptor])
    expect(fetch).toHaveBeenCalledWith('/apis/console.api.aifoundation.halo.run/v1alpha1/tools', {
      method: 'GET',
      headers,
      credentials: 'include',
      signal: controller.signal,
    })
  })

  it('supports a consumer-provided catalog endpoint', async () => {
    const fetch = vi.fn<FetchFunction>(async () => new Response('[]'))
    await listTools({ api: '/apis/my-plugin/tools', fetch })
    expect(fetch.mock.calls[0]?.[0]).toBe('/apis/my-plugin/tools')
  })

  it('preserves additive tool availability and configuration metadata', async () => {
    const tool = {
      ...descriptor,
      tags: ['联网', '搜索'],
      configurationSchema: [{ $formkit: 'secret', name: 'apiKeySecretName' }],
      enabled: true,
      configured: false,
      status: 'UNCONFIGURED',
    }
    expect(await listTools({ fetch: async () => new Response(JSON.stringify([tool])) })).toEqual([
      tool,
    ])
  })

  it('reports API failures with their HTTP status', async () => {
    const fetch = vi.fn<FetchFunction>(async () => new Response('Forbidden', { status: 403 }))
    await expect(listTools({ fetch })).rejects.toMatchObject({
      name: 'AIUIError',
      status: 403,
      message: 'Forbidden',
    })
    await expect(listTools({ fetch })).rejects.toBeInstanceOf(AIUIError)
  })

  it('preserves source-owned groups and explicit preset membership', async () => {
    const tool = {
      ...descriptor,
      group: {
        id: 'ai-foundation/posts',
        displayName: '文章',
        presets: [
          {
            id: 'ai-foundation/posts/read',
            displayName: '文章检索',
            toolIds: [descriptor.id],
          },
        ],
      },
    }
    expect(await listTools({ fetch: async () => new Response(JSON.stringify([tool])) })).toEqual([
      tool,
    ])
  })

  it.each([{}, [null], [{ ...descriptor, id: '' }], [{ ...descriptor, name: 12 }]])(
    'rejects invalid catalog identities (%j)',
    async (data) => {
      await expect(
        listTools({ fetch: async () => new Response(JSON.stringify(data)) }),
      ).rejects.toBeInstanceOf(AIUIProtocolError)
    },
  )

  it.each([
    { enabled: 'true' },
    { enabled: undefined },
    { configured: 0 },
    { configured: undefined },
    { status: 7 },
    { status: undefined },
    { status: 'UNKNOWN' },
    { tags: ['valid', false] },
    { configurationSchema: [null] },
    { group: { id: '', presets: [] } },
    { group: { id: 'demo/posts', presets: 'read' } },
    { group: { id: 'demo/posts', displayName: 3, presets: [] } },
    { group: { id: 'demo/posts', presets: [null] } },
    { group: { id: 'demo/posts', presets: [{ id: 'demo/posts/read', toolIds: [false] }] } },
  ])('rejects invalid optional descriptor metadata (%j)', async (metadata) => {
    await expect(
      listTools({
        fetch: async () => new Response(JSON.stringify([{ ...descriptor, ...metadata }])),
      }),
    ).rejects.toBeInstanceOf(AIUIProtocolError)
  })
})
