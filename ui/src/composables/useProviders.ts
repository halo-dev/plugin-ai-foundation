import { aiConsoleApiClient } from '@/api'
import { useMutation, useQueryClient } from '@tanstack/vue-query'

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
