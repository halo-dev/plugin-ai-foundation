import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { axiosInstance } from '@halo-dev/api-client'
import type { ProviderModelListResponse, TestChatRequest, TestChatResponse } from '@/types'
import {
  ConsoleApiAifoundationHaloRunV1alpha1ModelApi,
  ConsoleApiAifoundationHaloRunV1alpha1ProviderApi,
  type AiModel,
} from '@/api/generated'

const modelApi = new ConsoleApiAifoundationHaloRunV1alpha1ModelApi(undefined, '', axiosInstance)
const providerApi = new ConsoleApiAifoundationHaloRunV1alpha1ProviderApi(
  undefined,
  '',
  axiosInstance,
)

export function useModels() {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models'],
    queryFn: async () => {
      const { data } = await modelApi.listModels()
      return data
    },
  })
}

export function useModel(name: string) {
  return useQuery<AiModel>({
    queryKey: ['ai-model', name],
    queryFn: async () => {
      const { data } = await modelApi.getModel({ name })
      return data
    },
    enabled: !!name,
  })
}

export function useModelsByProvider(providerName: string) {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models', 'provider', providerName],
    queryFn: async () => {
      const { data } = await modelApi.listModels()
      return data.filter((m) => m.spec.providerName === providerName)
    },
    enabled: !!providerName,
  })
}

export function useCreateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (model: AiModel) => {
      const { data } = await modelApi.createModel({
        aiModel: model,
      })
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
      const { data } = await modelApi.updateModel({
        name,
        aiModel: model,
      })
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
      await modelApi.deleteModel({ name })
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
      const { data } = await providerApi.listProviders({})
      return data
    },
    enabled: !!providerName,
  })
}

export function useTestChat() {
  return useMutation({
    mutationFn: async ({ modelName, request }: { modelName: string; request: TestChatRequest }) => {
      const { data } = await axiosInstance.post<TestChatResponse>(
        `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${modelName}/test-chat`,
        request,
      )
      return data
    },
  })
}
