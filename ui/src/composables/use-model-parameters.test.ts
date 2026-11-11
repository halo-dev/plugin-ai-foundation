import { aiConsoleApiClient, aiCoreApiClient } from '@/api'
import type { AiModel, ModelOption } from '@/api/generated'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, shallowRef } from 'vue'
import { useSavedModelParameters } from './use-model-parameters'

vi.mock('@/api', () => ({
  aiCoreApiClient: { model: { getAiModel: vi.fn() } },
  aiConsoleApiClient: { model: { resolveModelParameters: vi.fn() } },
}))

beforeEach(() => vi.resetAllMocks())

it('loads language parameters but skips decision models and discards a stale response', async () => {
  const selected = shallowRef<ModelOption | undefined>({ name: 'decision', modelType: 'decision' })
  let parameters!: ReturnType<typeof useSavedModelParameters>
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(
    defineComponent({
      setup() {
        parameters = useSavedModelParameters(selected)
        return () => null
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient }]] } },
  )
  try {
    await flushPromises()
    expect(aiCoreApiClient.model.getAiModel).not.toHaveBeenCalled()
    expect(aiConsoleApiClient.model.resolveModelParameters).not.toHaveBeenCalled()
    expect(parameters.isFetching.value).toBe(false)

    const model: AiModel = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiModel',
      metadata: { name: 'chat' },
      spec: {
        modelId: 'chat',
        displayName: 'Chat',
        providerName: 'provider',
        modelType: 'language',
        enabled: true,
      },
    }
    vi.mocked(aiCoreApiClient.model.getAiModel).mockResolvedValue({ data: model } as never)
    let resolve!: (value: unknown) => void
    vi.mocked(aiConsoleApiClient.model.resolveModelParameters).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }) as never,
    )
    selected.value = { name: 'chat', modelType: 'language' }
    await vi.waitFor(() =>
      expect(aiConsoleApiClient.model.resolveModelParameters).toHaveBeenCalledOnce(),
    )
    const signal = vi.mocked(aiConsoleApiClient.model.resolveModelParameters).mock.calls[0]![1]
      ?.signal
    selected.value = { name: 'decision', modelType: 'decision' }
    await flushPromises()
    expect(signal?.aborted).toBe(true)
    resolve({ data: { constraints: { temperature: false } } })
    await flushPromises()
    expect(aiCoreApiClient.model.getAiModel).toHaveBeenCalledOnce()
    expect(aiConsoleApiClient.model.resolveModelParameters).toHaveBeenCalledOnce()
    expect(parameters.data.value).toBeUndefined()
    expect(parameters.isFetching.value).toBe(false)
    expect(parameters.isError.value).toBe(false)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})
