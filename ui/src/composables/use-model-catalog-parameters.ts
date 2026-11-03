import { aiConsoleApiClient } from '@/api'
import type { AiModel, ModelParameterProfile } from '@/api/generated'
import { computed, onScopeDispose, shallowReactive, shallowRef, watch, type Ref } from 'vue'

export function useModelCatalogParameters(
  model: Readonly<Ref<AiModel | undefined>>,
  providerType: Readonly<Ref<string>>,
  ready: Readonly<Ref<boolean>>,
  initialProfile: Readonly<Ref<ModelParameterProfile | undefined>>,
) {
  const snapshots = shallowReactive(new Map<string, ModelParameterProfile>())
  const initialSnapshot = shallowRef<{
    providerName?: string
    profile?: ModelParameterProfile
  }>()
  function snapshotKey(providerName: string, type: string, modelId: string, adapterType: string) {
    return JSON.stringify([providerName, type, modelId, 'language', adapterType])
  }
  function retainSnapshot(providerName: string | undefined, profile?: ModelParameterProfile) {
    if (providerName && profile?.providerType && profile.modelId && profile.adapterType) {
      snapshots.set(
        snapshotKey(providerName, profile.providerType, profile.modelId, profile.adapterType),
        profile,
      )
    }
  }
  watch(
    initialProfile,
    (profile) => {
      const providerName = model.value?.spec.providerName
      initialSnapshot.value = { providerName, profile }
      retainSnapshot(providerName, profile)
    },
    { immediate: true },
  )

  const data = computed(() => {
    const spec = model.value?.spec
    if (!spec?.providerName || spec.modelType !== 'language') return undefined
    if (providerType.value && spec.adapterType) {
      return snapshots.get(
        snapshotKey(spec.providerName, providerType.value, spec.modelId, spec.adapterType),
      )
    }
    // Before provider metadata supplies the identity, only the original snapshot is safe.
    const profile = initialSnapshot.value?.profile
    return !ready.value &&
      spec.providerName === initialSnapshot.value?.providerName &&
      profile?.modelId === spec.modelId &&
      (!providerType.value || profile.providerType === providerType.value) &&
      (!spec.adapterType || profile.adapterType === spec.adapterType)
      ? profile
      : undefined
  })
  const identity = computed(() => {
    const spec = model.value?.spec
    return JSON.stringify([
      spec?.providerName,
      providerType.value,
      spec?.modelId,
      spec?.modelType,
      spec?.adapterType,
      ready.value,
    ])
  })
  let disposed = false
  let currentRequest: { controller: AbortController; promise: Promise<void> } | undefined

  watch(
    identity,
    () => {
      currentRequest?.controller.abort()
      currentRequest = undefined
      const source = model.value
      const draft = source ? { ...source, spec: { ...source.spec } } : undefined
      if (
        !ready.value ||
        !providerType.value ||
        !draft?.spec.providerName ||
        !draft.spec.modelId ||
        draft.spec.modelType !== 'language' ||
        !draft.spec.adapterType
      ) {
        return
      }
      const expectedProviderType = providerType.value
      const request = { controller: new AbortController(), promise: Promise.resolve() }
      currentRequest = request
      request.promise = (async () => {
        try {
          const { data: profile } = await aiConsoleApiClient.model.getModelCatalogParameters(
            { aiModel: draft },
            { signal: request.controller.signal, timeout: 10_000 },
          )
          if (
            disposed ||
            currentRequest !== request ||
            request.controller.signal.aborted ||
            !profile ||
            profile.providerType !== expectedProviderType ||
            profile.modelId !== draft.spec.modelId ||
            profile.adapterType !== draft.spec.adapterType
          ) {
            return
          }
          retainSnapshot(draft.spec.providerName, profile)
        } catch {
          // A missing or unavailable catalog must not erase a valid saved snapshot.
        }
      })()
    },
    { immediate: true, flush: 'sync' },
  )

  onScopeDispose(() => {
    disposed = true
    currentRequest?.controller.abort()
  })

  async function waitForCurrent() {
    const expectedIdentity = identity.value
    const request = currentRequest
    if (request) {
      let onAbort!: () => void
      try {
        await Promise.race([
          request.promise,
          new Promise<void>((resolve) => {
            onAbort = resolve
            if (request.controller.signal.aborted) resolve()
            else request.controller.signal.addEventListener('abort', onAbort, { once: true })
          }),
        ])
      } finally {
        request.controller.signal.removeEventListener('abort', onAbort)
      }
    }
    return !disposed && identity.value === expectedIdentity && currentRequest === request
  }

  return { data, waitForCurrent }
}