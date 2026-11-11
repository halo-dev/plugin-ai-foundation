import { aiConsoleApiClient } from '@/api'
import type { AiModel, ModelParameterProfile } from '@/api/generated'
import { computed, onScopeDispose, shallowReactive, shallowRef, watch, type Ref } from 'vue'

interface CatalogRequest {
  key: string
  controller: AbortController
  promise: Promise<void>
}

export function useModelCatalogParameters(
  model: Readonly<Ref<AiModel | undefined>>,
  providerType: Readonly<Ref<string>>,
  ready: Readonly<Ref<boolean>>,
  initialProfile: Readonly<Ref<ModelParameterProfile | undefined>>,
) {
  const snapshots = shallowReactive(new Map<string, ModelParameterProfile>())
  const initialSnapshot = shallowRef<{
    key?: string
    providerName?: string
    modelType?: AiModel['spec']['modelType']
    profile?: ModelParameterProfile
  }>()
  function snapshotKey(
    providerName: string,
    type: string,
    modelId: string,
    modelType: AiModel['spec']['modelType'],
    adapterType: string,
  ) {
    return JSON.stringify([providerName, type, modelId, modelType, adapterType])
  }
  function retainSnapshot(
    providerName: string | undefined,
    modelType: AiModel['spec']['modelType'] | undefined,
    profile?: ModelParameterProfile,
  ) {
    if (
      providerName &&
      modelType &&
      profile?.providerType &&
      profile.providerBaseUrl &&
      profile.modelId &&
      profile.adapterType
    ) {
      const key = snapshotKey(
        providerName,
        profile.providerType,
        profile.modelId,
        modelType,
        profile.adapterType,
      )
      snapshots.set(key, profile)
      return key
    }
  }
  watch(
    initialProfile,
    (profile) => {
      const providerName = model.value?.spec.providerName
      const modelType = model.value?.spec.modelType
      initialSnapshot.value = {
        providerName,
        modelType,
        profile,
        key: retainSnapshot(providerName, modelType, profile),
      }
    },
    { immediate: true },
  )

  const data = computed(() => {
    const spec = model.value?.spec
    if (!spec?.providerName || !spec.modelType) return undefined
    if (providerType.value && spec.adapterType) {
      return snapshots.get(
        snapshotKey(
          spec.providerName,
          providerType.value,
          spec.modelId,
          spec.modelType,
          spec.adapterType,
        ),
      )
    }
    // Before provider metadata supplies the identity, only the original snapshot is safe.
    const initial = initialSnapshot.value
    const profile = initial?.profile
    if (
      ready.value ||
      !profile?.providerBaseUrl ||
      spec.providerName !== initial?.providerName ||
      spec.modelType !== initial?.modelType ||
      profile.modelId !== spec.modelId ||
      (providerType.value && profile.providerType !== providerType.value) ||
      (spec.adapterType && profile.adapterType !== spec.adapterType)
    ) {
      return undefined
    }
    return profile
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
  let currentRequest: CatalogRequest | undefined

  async function loadProfile(
    request: CatalogRequest,
    draft: AiModel,
    expectedProviderType: string,
  ) {
    try {
      const { data: profile, status } = await aiConsoleApiClient.model.getModelCatalogParameters(
        { aiModel: draft },
        { signal: request.controller.signal, timeout: 10_000 },
      )
      if (disposed || currentRequest !== request || request.controller.signal.aborted) return
      if (status === 204) {
        snapshots.delete(request.key)
        if (initialSnapshot.value?.key === request.key) initialSnapshot.value = undefined
        return
      }
      if (
        !profile?.providerBaseUrl ||
        profile.providerType !== expectedProviderType ||
        profile.modelId !== draft.spec.modelId ||
        profile.adapterType !== draft.spec.adapterType
      ) {
        return
      }
      retainSnapshot(draft.spec.providerName, draft.spec.modelType, profile)
    } catch {
      // A failed request must not erase a valid saved snapshot.
    }
  }

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
        !draft.spec.modelType ||
        !draft.spec.adapterType
      ) {
        return
      }
      const request: CatalogRequest = {
        key: snapshotKey(
          draft.spec.providerName,
          providerType.value,
          draft.spec.modelId,
          draft.spec.modelType,
          draft.spec.adapterType,
        ),
        controller: new AbortController(),
        promise: Promise.resolve(),
      }
      currentRequest = request
      request.promise = loadProfile(request, draft, providerType.value)
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