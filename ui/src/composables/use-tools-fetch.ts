import { aiConsoleApiClient } from '@/api'
import type { ToolDescriptor } from '@/api/generated'
import { events } from '@halo-dev/ui-shared'
import { useQuery } from '@tanstack/vue-query'
import { onScopeDispose } from 'vue'

export const QK_TOOLS = 'plugin:ai-foundation:tools'

export function useToolsFetch() {
  const query = useQuery<ToolDescriptor[]>({
    queryKey: [QK_TOOLS],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.tool.listTools()
      return data
    },
    refetchOnWindowFocus: 'always',
  })

  // A provider can expose different tools after its plugin settings change.
  const refresh = () => {
    void query.refetch()
  }
  events.on('core:plugin:configMap:updated', refresh)
  onScopeDispose(() => events.off('core:plugin:configMap:updated', refresh))
  return query
}
