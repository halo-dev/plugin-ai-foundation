import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import ModelEditingModal from '@/views/components/ModelEditingModal.vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api', () => ({
  aiConsoleApiClient: { model: { updateModel: vi.fn().mockResolvedValue({}) } },
}))
vi.mock('@/composables/use-providers-fetch', () => ({
  useProvidersFetch: () => ({
    data: ref([{ metadata: { name: 'openai-test' }, spec: { providerType: 'openai' } }]),
  }),
}))
vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderTypesFetch: () => ({
    data: ref([
      {
        providerType: 'openai',
        supportedModelTypes: ['language'],
        supportedFeatures: ['vision', 'audio-input'],
        adapters: [
          {
            adapterType: 'openai-chat',
            modelType: 'language',
            supportedFeatures: ['vision', 'audio-input'],
            recommended: true,
          },
          { adapterType: 'openai-responses', modelType: 'language', supportedFeatures: ['vision'] },
        ],
      },
    ]),
  }),
}))
vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  useMutation: ({ mutationFn }: { mutationFn: (value: unknown) => Promise<unknown> }) => ({
    mutate: mutationFn,
    isPending: ref(false),
  }),
}))

beforeEach(() => vi.clearAllMocks())

it('actual edit form and modal must invalidate obsolete text-only catalog media attribution', async () => {
  const saved: AiModel = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: 'text-only' },
    spec: {
      providerName: 'openai-test',
      modelId: 'text-only',
      displayName: 'Text only',
      modelType: 'language',
      adapterType: 'openai-chat',
      enabled: true,
      features: [],
      capabilitySources: { language: 'unknown' },
      capabilities: {
        language: {
          imageInput: false,
          fileInput: false,
          reasoningHistory: false,
          inputMediaTypes: [],
          inputSources: [],
        },
      },
      catalogMatch: {
        providerId: 'openai',
        modelId: 'text-only',
        fields: [
          'language.imageInput',
          'language.fileInput',
          'language.inputMediaTypes',
          'language.inputSources',
        ],
      },
    },
  }
  const wrapper = mount(ModelEditingModal, {
    props: { model: saved },
    global: {
      stubs: {
        VModal: { template: '<div><slot/><slot name="footer"/></div>' },
        FormKit: { name: 'FormKit', emits: ['submit', 'input'], template: '<div><slot/></div>' },
        ParameterMappingFields: true,
        ModelCatalogProvenance: true,
      },
    },
  })
  await flushPromises()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', {
    modelId: 'text-only',
    displayName: 'Text only',
    modelType: 'language',
    enabled: true,
    features: ['vision'],
    languageFileInput: 'false',
    languageReasoningHistory: 'false',
    languageInputMediaTypes: '',
    languageInputSources: [],
  })
  await flushPromises()
  expect(aiConsoleApiClient.model.updateModel).toHaveBeenCalledOnce()
  const after = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls[0]![0]!.aiModel
  expect(after.spec.features).toEqual(['vision'])
  expect(after.spec.capabilitySources?.language).toBe('manual')
  expect(after.spec.catalogMatch?.fields ?? []).not.toContain('language.inputMediaTypes')
})

it('changing a saved catalog model from Responses to Chat must drop Responses-only PDF coverage', async () => {
  const saved: AiModel = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: 'pdf-model' },
    spec: {
      providerName: 'openai-test',
      modelId: 'pdf-model',
      displayName: 'PDF',
      modelType: 'language',
      adapterType: 'openai-responses',
      enabled: true,
      features: [],
      capabilitySources: { language: 'unknown' },
      capabilities: {
        language: {
          imageInput: false,
          fileInput: true,
          reasoningHistory: false,
          inputMediaTypes: ['application/pdf'],
          inputSources: ['data'],
        },
      },
      catalogMatch: {
        providerId: 'openai',
        modelId: 'pdf-model',
        fields: [
          'language.imageInput',
          'language.fileInput',
          'language.inputMediaTypes',
          'language.inputSources',
        ],
      },
    },
  }
  const wrapper = mount(ModelEditingModal, {
    props: { model: saved },
    global: {
      stubs: {
        VModal: { template: '<div><slot/><slot name="footer"/></div>' },
        FormKit: { name: 'FormKit', emits: ['submit', 'input'], template: '<div><slot/></div>' },
        ParameterMappingFields: true,
        ModelCatalogProvenance: true,
      },
    },
  })
  await flushPromises()
  wrapper
    .findAllComponents({ name: 'FormKit' })
    .find((c) => c.attributes('name') === 'adapterType')!
    .vm.$emit('input', 'openai-chat')
  await flushPromises()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', {
    modelId: 'pdf-model',
    displayName: 'PDF',
    modelType: 'language',
    enabled: true,
    features: [],
    languageFileInput: 'true',
    languageReasoningHistory: 'false',
    languageInputMediaTypes: 'application/pdf',
    languageInputSources: ['data'],
  })
  await flushPromises()
  const after = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls[0]![0]!.aiModel
  expect(after.spec.adapterType).toBe('openai-chat')
  expect(after.spec.capabilities?.language?.inputMediaTypes ?? []).not.toContain('application/pdf')
})
