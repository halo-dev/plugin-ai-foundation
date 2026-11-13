import { events } from '@halo-dev/ui-shared'
import { useQuery } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useToolsFetch } from './use-tools-fetch'

vi.mock('@/api', () => ({ aiConsoleApiClient: { tool: { listTools: vi.fn() } } }))
vi.mock('@tanstack/vue-query', () => ({ useQuery: vi.fn() }))

describe('useToolsFetch', () => {
  it('refreshes discovery after provider configuration changes and removes its listener on disposal', () => {
    const refetch = vi.fn()
    vi.mocked(useQuery).mockReturnValue({ refetch } as unknown as ReturnType<typeof useQuery>)
    const scope = effectScope()
    scope.run(() => useToolsFetch())
    events.emit('core:plugin:configMap:updated', { pluginName: 'ai-foundation', group: 'tools' })
    expect(refetch).toHaveBeenCalledOnce()
    scope.stop()
    events.emit('core:plugin:configMap:updated', { pluginName: 'ai-foundation', group: 'tools' })
    expect(refetch).toHaveBeenCalledOnce()
  })
})
