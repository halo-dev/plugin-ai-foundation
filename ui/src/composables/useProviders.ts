import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { axiosInstance } from '@halo-dev/api-client'
import { ProviderConsoleEndpointApi } from '@/api/generated/api'
import type { ConnectivityResult } from '@/types'

const providerApi = new ProviderConsoleEndpointApi(undefined, '', axiosInstance)

export function useProviders() {
  return useQuery({
    queryKey: ['ai-providers'],
    queryFn: async () => {
      const { data } = await providerApi.list()
      return data as import('@/types').AiProvider[]
    },
  })
}

export function useProvider(name: string) {
  return useQuery({
    queryKey: ['ai-provider', name],
    queryFn: async () => {
      const { data } = await providerApi.get({ name })
      return data as import('@/types').AiProvider
    },
    enabled: !!name,
  })
}

export function useCreateProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (provider: import('@/types').AiProvider) => {
      const { data } = await providerApi.create({ aiProvider: provider as unknown as Parameters<typeof providerApi.create>[0]['aiProvider'] })
      return data as import('@/types').AiProvider
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
    },
  })
}

export function useUpdateProvider() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ name, provider }: { name: string; provider: import('@/types').AiProvider }) => {
      const { data } = await providerApi.update({ name, aiProvider: provider as unknown as Parameters<typeof providerApi.update>[0]['aiProvider'] })
      return data as import('@/types').AiProvider
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
      await providerApi._delete({ name })
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
      const { data } = await axiosInstance.post<ConnectivityResult>(
        `/apis/console.api.aifoundation.halo.run/v1alpha1/providers/${name}/connectivity`
      )
      return data
    },
    onSuccess: (_, name) => {
      queryClient.invalidateQueries({ queryKey: ['ai-providers'] })
      queryClient.invalidateQueries({ queryKey: ['ai-provider', name] })
    },
  })
}
