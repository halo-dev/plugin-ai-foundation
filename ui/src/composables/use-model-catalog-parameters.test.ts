import { aiConsoleApiClient } from '@/api'
import type { AiModel, ModelParameterProfile } from '@/api/generated'
import { useModelCatalogParameters } from '@/composables/use-model-catalog-parameters'
import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref, shallowRef, type EffectScope } from 'vue'

vi.mock('@/api', () => ({
  aiConsoleApiClient: { model: { getModelCatalogParameters: vi.fn() } },
}))

const profile: ModelParameterProfile = {
  providerType: 'openai',
  modelId: 'gpt-5',
  adapterType: 'openai-chat',
  constraints: { temperature: false, outputTokens: 128000 },
}
const scopes: EffectScope[] = []
function setup(options: { initial?: ModelParameterProfile; ready?: boolean } = {}) {
  const scope = effectScope()
  scopes.push(scope)
  const model = ref<AiModel>({
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: '' },
    spec: {
      providerName: 'openai-one',
      modelId: 'gpt-5',
      displayName: 'GPT-5',
      enabled: true,
      modelType: 'language',
      adapterType: 'openai-chat',
    },
  })
  const providerType = ref(options.ready === false ? '' : 'openai')
  const ready = ref(options.ready !== false)
  const initial = shallowRef(options.initial)
  const result = scope.run(() => useModelCatalogParameters(model, providerType, ready, initial))!
  return { ...result, model, providerType, ready, initial, scope }
}

function deferred() {
  let resolve!: (value: unknown) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
    data: undefined,
  } as never)
})
afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop())
})

describe('automatic model catalog parameters', () => {
  it('loads the selected identity without writing model data', async () => {
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
      data: profile,
    } as never)
    const form = setup()
    await form.waitForCurrent()
    expect(form.data.value).toEqual(profile)
    expect(aiConsoleApiClient.model.getModelCatalogParameters).toHaveBeenCalledOnce()
    expect(form.model.value.spec.catalogParameters).toBeUndefined()
  })

  it('retains an existing snapshot while provider metadata is still loading', async () => {
    const form = setup({ initial: profile, ready: false })
    expect(form.data.value).toEqual(profile)
    expect(aiConsoleApiClient.model.getModelCatalogParameters).not.toHaveBeenCalled()
    form.providerType.value = 'openai'
    form.ready.value = true
    await form.waitForCurrent()
    expect(aiConsoleApiClient.model.getModelCatalogParameters).toHaveBeenCalledOnce()
    expect(form.data.value).toEqual(profile)
  })

  it('keeps a same-identity snapshot on failure and allows safe saving', async () => {
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockRejectedValue(
      new Error('offline'),
    )
    const form = setup({ initial: profile })
    await expect(form.waitForCurrent()).resolves.toBe(true)
    expect(form.data.value).toEqual(profile)
    form.model.value.spec.modelId = 'another'
    await form.waitForCurrent()
    expect(form.data.value).toBeUndefined()
  })

  it('does not invent configuration when the catalog has no match', async () => {
    const form = setup()
    await expect(form.waitForCurrent()).resolves.toBe(true)
    expect(form.data.value).toBeUndefined()
  })

  it.each(['missing', 'failure'] as const)(
    'retains saved and previously loaded adapter snapshots after switching back with a %s result',
    async (result) => {
      const responsesProfile: ModelParameterProfile = {
        ...profile,
        adapterType: 'openai-responses',
        constraints: { outputTokens: 8192 },
      }
      vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters)
        .mockResolvedValueOnce({ data: undefined } as never)
        .mockResolvedValueOnce({ data: responsesProfile } as never)
      const form = setup({ initial: profile })
      await form.waitForCurrent()
      expect(form.data.value).toEqual(profile)
      form.model.value.spec.adapterType = 'openai-responses'
      await form.waitForCurrent()
      expect(form.data.value).toEqual(responsesProfile)

      if (result === 'failure') {
        vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockRejectedValue(
          new Error('offline'),
        )
      }
      form.model.value.spec.adapterType = 'openai-chat'
      await expect(form.waitForCurrent()).resolves.toBe(true)
      expect(form.data.value).toEqual(profile)
      form.model.value.spec.adapterType = 'openai-responses'
      await expect(form.waitForCurrent()).resolves.toBe(true)
      expect(form.data.value).toEqual(responsesProfile)
    },
  )

  it('keeps saved and loaded snapshots isolated between providers with the same model ID', async () => {
    const responsesProfile: ModelParameterProfile = {
      ...profile,
      adapterType: 'openai-responses',
      constraints: { outputTokens: 8192 },
    }
    const secondProviderProfile: ModelParameterProfile = {
      ...profile,
      constraints: { outputTokens: 4096 },
    }
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters)
      .mockResolvedValueOnce({ data: undefined } as never)
      .mockResolvedValueOnce({ data: responsesProfile } as never)
    const form = setup({ initial: profile })
    await form.waitForCurrent()
    form.model.value.spec.adapterType = 'openai-responses'
    await form.waitForCurrent()

    form.model.value.spec.providerName = 'openai-two'
    await form.waitForCurrent()
    expect(form.data.value).toBeUndefined()
    form.model.value.spec.adapterType = 'openai-chat'
    await form.waitForCurrent()
    expect(form.data.value).toBeUndefined()

    form.ready.value = false
    form.providerType.value = ''
    expect(form.data.value).toBeUndefined()
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValueOnce({
      data: secondProviderProfile,
    } as never)
    form.providerType.value = 'openai'
    form.ready.value = true
    await form.waitForCurrent()
    expect(form.data.value).toEqual(secondProviderProfile)

    form.model.value.spec.providerName = 'openai-one'
    await form.waitForCurrent()
    expect(form.data.value).toEqual(profile)
    form.model.value.spec.adapterType = 'openai-responses'
    await form.waitForCurrent()
    expect(form.data.value).toEqual(responsesProfile)
    form.model.value.spec.providerName = 'openai-two'
    await form.waitForCurrent()
    expect(form.data.value).toBeUndefined()
    form.model.value.spec.adapterType = 'openai-chat'
    await form.waitForCurrent()
    expect(form.data.value).toEqual(secondProviderProfile)
  })

  it.each(['providerName', 'modelId', 'adapterType', 'providerType'] as const)(
    'cancels and ignores late responses when %s changes',
    async (field) => {
      const oldRequest = deferred()
      const newRequest = deferred()
      vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters)
        .mockReturnValueOnce(oldRequest.promise as never)
        .mockReturnValueOnce(newRequest.promise as never)
      const form = setup()
      const oldSignal = vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mock
        .calls[0]![1]!.signal
      const waitingSave = form.waitForCurrent()
      const changedProfile = { ...profile, constraints: { outputTokens: 4096 } }
      if (field === 'providerType') {
        form.providerType.value = 'compatible'
        changedProfile.providerType = 'compatible'
      } else if (field === 'adapterType') {
        form.model.value.spec.adapterType = 'openai-responses'
        changedProfile.adapterType = 'openai-responses'
      } else {
        form.model.value.spec[field] = 'another'
        if (field === 'modelId') changedProfile.modelId = 'another'
      }
      expect(oldSignal?.aborted).toBe(true)
      await expect(waitingSave).resolves.toBe(false)
      newRequest.resolve({ data: changedProfile })
      await form.waitForCurrent()
      oldRequest.resolve({ data: profile })
      await flushPromises()
      expect(form.data.value).toEqual(changedProfile)
    },
  )

  it('stops querying and removes language parameters when switching model type', async () => {
    const pending = deferred()
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
      pending.promise as never,
    )
    const form = setup({ initial: profile })
    form.model.value.spec.modelType = 'embedding'
    pending.resolve({ data: profile })
    await form.waitForCurrent()
    await flushPromises()
    expect(aiConsoleApiClient.model.getModelCatalogParameters).toHaveBeenCalledOnce()
    expect(form.data.value).toBeUndefined()
  })

  it('does not accept a profile for a different model or adapter', async () => {
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
      data: { ...profile, modelId: 'another' },
    } as never)
    const form = setup()
    await form.waitForCurrent()
    expect(form.data.value).toBeUndefined()
  })

  it('waits for the current request and aborts waiting when the form closes', async () => {
    const pending = deferred()
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
      pending.promise as never,
    )
    const form = setup()
    let settled = false
    const save = form.waitForCurrent().then((valid) => {
      settled = true
      return valid
    })
    await flushPromises()
    expect(settled).toBe(false)
    form.scope.stop()
    await expect(save).resolves.toBe(false)
    pending.resolve({ data: profile })
    await flushPromises()
    expect(form.data.value).toBeUndefined()
  })
})