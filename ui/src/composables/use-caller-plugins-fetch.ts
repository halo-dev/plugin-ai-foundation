import { aiConsoleApiClient } from '@/api'
import type { CallerPluginInfo } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'

export const QK_CALLER_PLUGINS = 'plugin:ai-foundation:caller-plugins'

export function useCallerPluginsFetch() {
  return useQuery<CallerPluginInfo[]>({
    queryKey: [QK_CALLER_PLUGINS],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.callerPlugin.listObservedCallerPlugins()
      return data
    },
  })
}
