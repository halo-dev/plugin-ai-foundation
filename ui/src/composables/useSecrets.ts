import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { axiosInstance } from '@halo-dev/api-client'
import type { Secret } from '@/types'

const API_PREFIX = '/apis/core.halo.run/v1alpha1/secrets'

export function useSecrets() {
  return useQuery({
    queryKey: ['secrets'],
    queryFn: async () => {
      const { data } = await axiosInstance.get<{ items: Secret[] }>(API_PREFIX)
      return data.items || []
    },
  })
}

export function useCreateSecret() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (secret: Secret) => {
      const { data } = await axiosInstance.post<Secret>(API_PREFIX, secret)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['secrets'] })
    },
  })
}
