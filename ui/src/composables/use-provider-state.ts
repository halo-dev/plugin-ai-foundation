import { useRouteQuery } from '@vueuse/router'

export function useProviderQueryState() {
  const selectedProvider = useRouteQuery<string | undefined>('provider')

  return {
    selectedProvider,
  }
}
