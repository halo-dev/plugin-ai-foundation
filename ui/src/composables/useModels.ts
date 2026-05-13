import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { axiosInstance } from '@halo-dev/api-client'
import { ModelConsoleEndpointApi } from '@/api/generated/api'
import type { ProviderModelListResponse, TestChatRequest, TestChatResponse } from '@/types'

const modelApi = new ModelConsoleEndpointApi(undefined, '', axiosInstance)

export function useModels() {
  return useQuery({
    queryKey: ['ai-models'],
    queryFn: async () => {
      const { data } = await modelApi.list1()
      return data as import('@/types').AiModel[]
    },
  })
}

export function useModel(name: string) {
  return useQuery({
    queryKey: ['ai-model', name],
    queryFn: async () => {
      const { data } = await modelApi.get1({ name })
      return data as import('@/types').AiModel
    },
    enabled: !!name,
  })
}

export function useModelsByProvider(providerName: string) {
  return useQuery({
    queryKey: ['ai-models', 'provider', providerName],
    queryFn: async () => {
      const { data } = await modelApi.list1()
      return (data as import('@/types').AiModel[]).filter((m) => m.spec.providerName === providerName)
    },
    enabled: !!providerName,
  })
}

export function useCreateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (model: import('@/types').AiModel) => {
      const { data } = await modelApi.create1({ aiModel: model as unknown as Parameters<typeof modelApi.create1>[0]['aiModel'] })
      return data as import('@/types').AiModel
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
    },
  })
}

export function useUpdateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ name, model }: { name: string; model: import('@/types').AiModel }) => {
      const { data } = await modelApi.update1({ name, aiModel: model as unknown as Parameters<typeof modelApi.update1>[0]['aiModel'] })
      return data as import('@/types').AiModel
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
      queryClient.invalidateQueries({ queryKey: ['ai-model', variables.name] })
      queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider'] })
    },
  })
}

export function useDeleteModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      await modelApi.delete1({ name })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
      queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider'] })
    },
  })
}

export function useProviderModels(providerName: string) {
  return useQuery({
    queryKey: ['provider-models', providerName],
    queryFn: async () => {
      const { data } = await axiosInstance.get<ProviderModelListResponse>(
        `/apis/console.api.aifoundation.halo.run/v1alpha1/providers/${providerName}/models`
      )
      return data
    },
    enabled: !!providerName,
  })
}

export function useTestChat() {
  return useMutation({
    mutationFn: async ({
      providerName,
      modelId,
      request,
    }: {
      providerName: string
      modelId: string
      request: TestChatRequest
    }) => {
      const { data } = await axiosInstance.post<TestChatResponse>(
        `/apis/console.api.aifoundation.halo.run/v1alpha1/providers/${providerName}/models/${encodeURIComponent(modelId)}/test-chat`,
        request
      )
      return data
    },
  })
}
