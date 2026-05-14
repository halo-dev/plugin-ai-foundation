import { aiConsoleApiClient } from '@/api'
import { type AiModel, type TestChatRequest } from '@/api/generated'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

export interface DiscoveredModel {
  modelId: string
  displayName: string
  name: string
  capabilities: string[]
}

export function useModels() {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models'],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.model.listModels()
      return data
    },
  })
}

export function useModel(name: string) {
  return useQuery<AiModel>({
    queryKey: ['ai-model', name],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.model.getModel({ name })
      return data
    },
    enabled: !!name,
  })
}

export function useModelsByProvider(providerName: string) {
  return useQuery<AiModel[]>({
    queryKey: ['ai-models', 'provider', providerName],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.model.listModels()
      return data.filter((m) => m.spec.providerName === providerName)
    },
    enabled: !!providerName,
  })
}

export function useCreateModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (model: AiModel) => {
      const { data } = await aiConsoleApiClient.model.createModel({
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
      const { data } = await aiConsoleApiClient.model.updateModel({
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
      await aiConsoleApiClient.model.deleteModel({ name })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-models'] })
      queryClient.invalidateQueries({ queryKey: ['ai-models', 'provider'] })
    },
  })
}

export function useProviderModels(providerName: string) {
  return useQuery<{ models: DiscoveredModel[]; providerName: string }>({
    queryKey: ['provider-models', providerName],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.provider.discoverProviderModels({
        name: providerName,
      })
      return data as unknown as { models: DiscoveredModel[]; providerName: string }
    },
    enabled: !!providerName,
  })
}

export function useTestChat() {
  return useMutation({
    mutationFn: async ({ modelName, request }: { modelName: string; request: TestChatRequest }) => {
      const { data } = await aiConsoleApiClient.model.testModelChat({
        name: modelName,
        testChatRequest: request,
      })
      return data
    },
  })
}
