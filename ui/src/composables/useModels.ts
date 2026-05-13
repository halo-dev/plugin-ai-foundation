import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { axiosInstance } from '@halo-dev/api-client'
import { ModelConsoleEndpointApi, ProviderConsoleEndpointApi } from '@/api/generated/api'
import type { ProviderModelListResponse, TestChatRequest, TestChatResponse } from '@/types'
import type { AiModel } from '@/api/generated'

const modelApi = new ModelConsoleEndpointApi(undefined, '', axiosInstance)
const providerApi = new ProviderConsoleEndpointApi(undefined, '', axiosInstance)

export function useModels() {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models'],
    queryFn: async () => {
      const { data } = await modelApi.list1()
      return data
    },
  })
}

export function useModel(name: string) {
  return useQuery<AiModel>({
    queryKey: ['ai-model', name],
    queryFn: async () => {
      const { data } = await modelApi.get1({ name })
      return data
    },
    enabled: !!name,
  })
}

export function useModelsByProvider(providerName: string) {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models', 'provider', providerName],
    queryFn: async () => {
      const { data } = await modelApi.list1()
      return data.filter((m) => m.spec.providerName === providerName)
    },
    enabled: !!providerName,
  })
}

export function useCreateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (model: AiModel) => {
      const { data } = await modelApi.create1({ aiModel: model as unknown as Parameters<typeof modelApi.create1>[0]['aiModel'] })
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
    },
  })
}

export function useUpdateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ name, model }: { name: string; model: AiModel }) => {
      const { data } = await modelApi.update1({ name, aiModel: model as unknown as Parameters<typeof modelApi.update1>[0]['aiModel'] })
      return data
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
      const { data } = await providerApi.list({  })
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
