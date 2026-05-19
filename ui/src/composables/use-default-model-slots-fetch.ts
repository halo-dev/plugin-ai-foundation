import { aiConsoleApiClient } from '@/api'
import type { DefaultModelSlots } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'

export const QK_DEFAULT_MODEL_SLOTS = 'plugin:ai-foundation:default-model-slots'

export function useDefaultModelSlotsFetch() {
  return useQuery<DefaultModelSlots>({
    queryKey: [QK_DEFAULT_MODEL_SLOTS],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.defaultModelSlot.getDefaultModelSlots()
      return data
    },
  })
}
