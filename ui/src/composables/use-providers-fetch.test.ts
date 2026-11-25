import type { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { QK_PROVIDER, QK_PROVIDERS, reloadProviderQueries } from './use-providers-fetch'

describe('reloadProviderQueries', () => {
  it('refreshes the provider list and selected provider detail with the unwrapped name', () => {
    const providerName = ref('openai')
    const refetchQueries = vi.fn()
    const queryClient = { refetchQueries } as unknown as QueryClient

    reloadProviderQueries(queryClient, providerName)

    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDERS] })
    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDER, 'openai'] })
  })

  it('accepts a plain provider name', () => {
    const refetchQueries = vi.fn()
    const queryClient = { refetchQueries } as unknown as QueryClient

    reloadProviderQueries(queryClient, 'openai')

    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDER, 'openai'] })
  })

  it('refreshes all provider details when no selection is given', () => {
    const refetchQueries = vi.fn()
    const queryClient = { refetchQueries } as unknown as QueryClient

    reloadProviderQueries(queryClient)

    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDER] })
  })
})
