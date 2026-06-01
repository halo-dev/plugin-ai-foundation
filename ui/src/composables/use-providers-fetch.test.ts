import { describe, expect, it, rstest } from '@rstest/core'
import type { QueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import { QK_PROVIDER, QK_PROVIDERS, reloadProviderQueries } from './use-providers-fetch'

describe('reloadProviderQueries', () => {
  it('refreshes the provider list and selected provider detail', () => {
    const providerName = ref('openai')
    const refetchQueries = rstest.fn()
    const queryClient = { refetchQueries } as unknown as QueryClient

    reloadProviderQueries(queryClient, providerName)

    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDERS] })
    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDER, providerName] })
  })
})
