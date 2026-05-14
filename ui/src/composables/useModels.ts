import { aiConsoleApiClient } from '@/api'
import { type AiModel, type TestChatRequest } from '@/api/generated'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

export interface DiscoveredModel {
  modelId: string
  displayName: string
  name: string
  capabilities: string[]
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
