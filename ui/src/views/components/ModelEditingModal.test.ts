import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import ModelEditingModal from '@/views/components/ModelEditingModal.vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/composables/use-model-parameters', () => ({
  useModelParameters: () => ({ data: ref(undefined), isError: ref(false) }),
}))

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    model: { updateModel: vi.fn().mockResolvedValue({}), getModelCatalogParameters: vi.fn() },
  },
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
        supportedFeatures: ['tool-call', 'structured-output', 'reasoning', 'vision', 'audio-input'],
        adapters: [
          {
            adapterType: 'openai-chat',
            modelType: 'language',
            supportedFeatures: [
              'tool-call',
              'structured-output',
              'reasoning',
              'vision',
              'audio-input',
            ],
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

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
    data: undefined,
  } as never)
})

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

it('automatically loads catalog parameters in the draft and preserves manual overrides until save', async () => {
  const catalogParameters = {
    providerType: 'openai',
    providerBaseUrl: 'https://api.openai.com/v1',
    modelId: 'example',
    adapterType: 'openai-chat' as const,
    constraints: { temperature: false, outputTokens: 8192 },
  }
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
    data: catalogParameters,
  } as never)
  const saved: AiModel = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: 'example' },
    spec: {
      providerName: 'openai-test',
      modelId: 'example',
      displayName: 'Example',
      enabled: true,
      modelType: 'language',
      adapterType: 'openai-chat',
      parameterConstraints: { outputTokens: 4096 },
      parameterMappings: {
        language: { temperature: { mode: 'TEMPLATE', template: 'chat.temperature' } },
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
  expect(wrapper.text()).not.toContain('采用目录参数')
  expect(aiConsoleApiClient.model.getModelCatalogParameters).toHaveBeenCalledOnce()
  expect(aiConsoleApiClient.model.updateModel).not.toHaveBeenCalled()
  expect(saved.spec.catalogParameters).toBeUndefined()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', {
    modelId: 'example',
    displayName: 'Example renamed',
    enabled: true,
    modelType: 'language',
  })
  await flushPromises()
  const after = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls[0]![0]!.aiModel
  expect(after.spec.catalogParameters).toEqual(catalogParameters)
  expect(after.spec.parameterConstraints).toEqual(saved.spec.parameterConstraints)
  expect(after.spec.parameterMappings).toEqual(saved.spec.parameterMappings)
})

it('waits for catalog loading before saving an existing model', async () => {
  let resolveCatalog!: (value: unknown) => void
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
    new Promise((resolve) => {
      resolveCatalog = resolve
    }) as never,
  )
  const saved: AiModel = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: 'waiting' },
    spec: {
      providerName: 'openai-test',
      modelId: 'waiting',
      displayName: 'Waiting',
      enabled: true,
      modelType: 'language',
      adapterType: 'openai-chat',
      parameterConstraints: { outputTokens: 1024 },
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
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', saved.spec)
  await flushPromises()
  expect(aiConsoleApiClient.model.updateModel).not.toHaveBeenCalled()
  expect(
    wrapper
      .findAllComponents({ name: 'Button' })
      .find((c) => c.text() === '保存')
      ?.props('loading'),
  ).toBe(true)

  const catalogParameters = {
    providerType: 'openai',
    providerBaseUrl: 'https://api.openai.com/v1',
    modelId: 'waiting',
    adapterType: 'openai-chat',
    constraints: { outputTokens: 8192 },
  }
  resolveCatalog({ data: catalogParameters })
  await flushPromises()
  expect(aiConsoleApiClient.model.updateModel).toHaveBeenCalledOnce()
  const after = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls[0]![0]!.aiModel
  expect(after.spec.catalogParameters).toEqual(catalogParameters)
  expect(after.spec.parameterConstraints).toEqual({ outputTokens: 1024 })
})

it('saves the newly resolved catalog profile when changing a saved model protocol', async () => {
  const previousProfile = {
    providerType: 'openai',
    providerBaseUrl: 'https://api.openai.com/v1',
    modelId: 'protocol-switch',
    adapterType: 'openai-chat' as const,
    constraints: { outputTokens: 8192 },
  }
  const responsesProfile = {
    ...previousProfile,
    adapterType: 'openai-responses' as const,
    constraints: { temperature: false, outputTokens: 32768 },
  }
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockImplementation(
    async (request) =>
      ({
        data:
          request.aiModel.spec.adapterType === 'openai-responses'
            ? responsesProfile
            : previousProfile,
      }) as never,
  )
  const saved: AiModel = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name: 'protocol-switch' },
    spec: {
      providerName: 'openai-test',
      modelId: 'protocol-switch',
      displayName: 'Protocol switch',
      enabled: true,
      modelType: 'language',
      adapterType: 'openai-chat',
      catalogParameters: previousProfile,
      parameterConstraints: { outputTokens: 1024 },
      parameterMappings: {
        language: { temperature: { mode: 'TEMPLATE', template: 'chat.temperature' } },
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
    .find((component) => component.attributes('name') === 'adapterType')!
    .vm.$emit('input', 'openai-responses')
  await flushPromises()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', saved.spec)
  await flushPromises()

  expect(aiConsoleApiClient.model.updateModel).toHaveBeenCalledOnce()
  const after = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls[0]![0]!.aiModel
  expect(after.spec.adapterType).toBe('openai-responses')
  expect(after.spec.catalogParameters).toEqual(responsesProfile)
  expect(after.spec.parameterConstraints).toEqual(saved.spec.parameterConstraints)
  expect(after.spec.parameterMappings).toEqual(saved.spec.parameterMappings)
  expect(saved.spec.catalogParameters).toEqual(previousProfile)
  wrapper.unmount()
})

it.each([false, true])(
  'saves and reopens refreshed %s catalog features without labeling them manual',
  async (supported) => {
    const features = ['tool-call', 'structured-output', 'reasoning'] as const
    const constraints = {
      toolCalling: supported,
      structuredOutput: supported,
      reasoning: { supported },
    }
    const oldProfile = {
      providerType: 'openai',
      providerBaseUrl: 'https://api.openai.com/v1',
      modelId: 'example',
      adapterType: 'openai-chat' as const,
      constraints: {
        toolCalling: !supported,
        structuredOutput: !supported,
        reasoning: { supported: !supported },
      },
      featureEvidence: {},
    }
    const newProfile = { ...oldProfile, constraints }
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
      data: newProfile,
    } as never)
    let saved: AiModel = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiModel',
      metadata: { name: 'example' },
      spec: {
        providerName: 'openai-test',
        modelId: 'example',
        displayName: 'Example',
        enabled: true,
        modelType: 'language',
        adapterType: 'openai-chat',
        features: supported ? [] : [...features],
        capabilitySources: { language: 'unknown' },
        catalogMatch: {
          providerId: 'openai',
          modelId: 'example',
          fields: features.map((feature) => `features.${feature}`),
        },
        catalogParameters: oldProfile,
      },
    }
    for (let reopen = 0; reopen < 2; reopen++) {
      const wrapper = mount(ModelEditingModal, {
        props: { model: saved },
        global: {
          stubs: {
            VModal: { template: '<div><slot/><slot name="footer"/></div>' },
            FormKit: {
              name: 'FormKit',
              emits: ['submit', 'input'],
              template: '<div><slot/></div>',
            },
            ParameterMappingFields: true,
            ModelCatalogProvenance: true,
          },
        },
      })
      await flushPromises()
      wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', {
        modelId: 'example',
        displayName: 'Example renamed',
        enabled: true,
        modelType: 'language',
        features: saved.spec.features,
      })
      await flushPromises()
      const calls = vi.mocked(aiConsoleApiClient.model.updateModel).mock.calls
      saved = calls[calls.length - 1]![0]!.aiModel
      expect(saved.spec.catalogParameters?.constraints).toEqual(constraints)
      expect(saved.spec.parameterConstraints).toBeUndefined()
      expect(saved.spec.features || []).toEqual(supported ? features : [])
      expect(saved.spec.catalogMatch?.fields).toEqual(
        features.map((feature) => `features.${feature}`),
      )
      expect(saved.spec.capabilitySources?.language).toBe('unknown')
      wrapper.unmount()
    }
  },
)