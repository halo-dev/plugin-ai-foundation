import type { AiProvider } from '@/api/generated'
import { ConsoleApiAifoundationHaloRunV1alpha1ProviderApi } from '@/api/generated/api'
import { axiosInstance } from '@halo-dev/api-client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

const providerApi = new ConsoleApiAifoundationHaloRunV1alpha1ProviderApi(
  undefined,
  '',
  axiosInstance,
)

export function useProviders() {
  return useQuery<AiProvider[]>({
    queryKey: ['ai-providers'],
    queryFn: async () => {
      const { data } = await providerApi.listProviders()
      return data
    },
  })
}

export function useProvider(name: string) {
  return useQuery<AiProvider>({
    queryKey: ['ai-provider', name],
    queryFn: async () => {
      const { data } = await providerApi.getProvider({ name })
      return data
    },
    enabled: !!name,
  })
}

export function useCreateProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (provider: AiProvider) => {
      const { data } = await providerApi.createProvider({
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
      const { data } = await providerApi.updateProvider({
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
      await providerApi.deleteProvider({ name })
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
      const { data } = await axiosInstance.post(
        `/apis/console.api.aifoundation.halo.run/v1alpha1/providers/${name}/connectivity`,
      )
      return data
    },
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
      queryClient.invalidateQueries({ queryKey: ['ai-provider', name] })
    },
  })
}
