import { aiConsoleApiClient } from '@/api'
import type { AiProvider } from '@/api/generated'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

export function useProviders() {
  return useQuery<AiProvider[]>({
    queryKey: ['ai-providers'],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.provider.listProviders()
      return data
    },
  })
}

export function useProvider(name: string) {
  return useQuery<AiProvider>({
    queryKey: ['ai-provider', name],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.provider.getProvider({ name })
      return data
    },
    enabled: !!name,
  })
}

export function useCreateProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (provider: AiProvider) => {
      const { data } = await aiConsoleApiClient.provider.createProvider({
        aiProvider: provider,
      })
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
    },
  })
}

export function useUpdateProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ name, provider }: { name: string; provider: AiProvider }) => {
      const { data } = await aiConsoleApiClient.provider.updateProvider({
        name,
        aiProvider: provider,
      })
      return data
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
      queryClient.invalidateQueries({ queryKey: ['ai-provider', variables.name] })
    },
  })
}

export function useDeleteProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      await aiConsoleApiClient.provider.deleteProvider({ name })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
    },
  })
}

export function useTestConnectivity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      const { data } = await aiConsoleApiClient.provider.testProviderConnectivity({ name })
      return data
    },
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
      queryClient.invalidateQueries({ queryKey: ['ai-provider', name] })
    },
  })
}
