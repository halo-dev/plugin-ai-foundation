import { aiConsoleApiClient } from '@/api'
import type { AiModel, ModelParameterProfile } from '@/api/generated'
import type { ModelFormState } from '@/types/form'
import { reconcileModelCapabilities } from '@/utils/model-capability-changes'
import ModelForm from '@/views/components/ModelForm.vue'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'

vi.mock('@/api', () => ({
  aiConsoleApiClient: { model: { getModelCatalogParameters: vi.fn() } },
}))
vi.mock('@/composables/use-model-parameters', () => ({
  useModelParameters: () => ({ data: ref(undefined), isError: ref(false) }),
}))
vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderTypesFetch: () => ({
    data: ref([
      {
        providerType: 'openai',
        supportedModelTypes: ['language', 'embedding', 'image-generation', 'rerank'],
        supportedFeatures: ['tool-call', 'structured-output', 'reasoning', 'vision', 'audio-input'],
        adapters: [
          { adapterType: 'openai-chat', modelType: 'language', recommended: true },
          { adapterType: 'openai-responses', modelType: 'language' },
          { adapterType: 'openai-embedding', modelType: 'embedding', recommended: true },
          { adapterType: 'openai-image', modelType: 'image-generation', recommended: true },
          { adapterType: 'rerank', modelType: 'rerank', recommended: true },
        ],
      },
    ]),
  }),
}))

const stubs = {
  FormKit: { name: 'FormKit', emits: ['submit', 'input'], template: '<div><slot/></div>' },
  ParameterMappingFields: true,
  ModelParameterConstraintFields: true,
  ModelNativeParameterConstraintFields: true,
  ModelCatalogProvenance: true,
}
const formData = {
  modelId: 'gpt-5',
  displayName: 'GPT-5',
  enabled: true,
  modelType: 'language' as const,
}
const profile: ModelParameterProfile = {
  providerType: 'openai',
  providerBaseUrl: 'https://api.openai.com/v1',
  modelId: 'gpt-5',
  adapterType: 'openai-chat',
  constraints: { temperature: false, outputTokens: 128000 },
}

function deferredCatalog() {
  let resolve!: (value: { data: ModelParameterProfile }) => void
  const promise = new Promise((res) => {
    resolve = res
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
    data: undefined,
  } as never)
})

it('automatically looks up a newly entered model and waits before emitting a save', async () => {
  const catalog = deferredCatalog()
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
    catalog.promise as never,
  )
  const wrapper = mount(ModelForm, {
    props: { providerName: 'openai-one', providerType: 'openai' },
    global: { stubs },
  })
  expect(aiConsoleApiClient.model.getModelCatalogParameters).not.toHaveBeenCalled()
  wrapper
    .findAllComponents({ name: 'FormKit' })
    .find((c) => c.attributes('name') === 'modelId')!
    .vm.$emit('input', 'gpt-5')
  await flushPromises()
  expect(aiConsoleApiClient.model.getModelCatalogParameters).toHaveBeenCalledOnce()
  expect(
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mock.calls[0]![0].aiModel.spec,
  ).toMatchObject({ modelId: 'gpt-5', adapterType: 'openai-chat' })
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  expect(wrapper.emitted('submit')).toBeUndefined()
  expect(wrapper.vm.isSubmitting).toBe(true)
  catalog.resolve({ data: profile })
  await flushPromises()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ catalogParameters: profile })
  expect(wrapper.vm.isSubmitting).toBe(false)
})

it.each([
  ['embedding', 'openai-embedding'],
  ['image-generation', 'openai-image'],
  ['rerank', 'rerank'],
] as const)(
  'automatically persists %s catalog constraints and keeps manual mappings',
  async (modelType, adapterType) => {
    const nonChatProfile: ModelParameterProfile = {
      providerType: 'openai',
      providerBaseUrl: 'https://api.openai.com/v1',
      modelId: 'test-model',
      adapterType,
      constraints: { inputTokens: 8191 },
    }
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockResolvedValue({
      data: nonChatProfile,
    } as never)
    const state: ModelFormState = {
      modelId: 'test-model',
      displayName: 'Test',
      enabled: true,
      modelType,
      adapterType,
      parameterMappings:
        modelType === 'embedding'
          ? { embedding: { dimensions: { mode: 'UNSUPPORTED' } } }
          : undefined,
      parameterConstraints: { inputTokens: 4096 },
    }
    const wrapper = mount(ModelForm, {
      props: { providerName: 'openai-one', providerType: 'openai', formState: state },
      global: {
        stubs: {
          ...stubs,
          AdvancedSettingsCollapsible: { template: '<section><slot/></section>' },
        },
      },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', state)
    await flushPromises()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      catalogParameters: nonChatProfile,
      parameterConstraints: state.parameterConstraints,
      parameterMappings: state.parameterMappings,
    })
    expect(
      wrapper.findComponent({ name: 'ModelParameterConstraintFields' }).props('modelType'),
    ).toBe(modelType)
    wrapper.unmount()
  },
)

it('cancels an in-flight save when the adapter changes and saves only the new catalog profile', async () => {
  const chat = deferredCatalog()
  const responses = deferredCatalog()
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters)
    .mockReturnValueOnce(chat.promise as never)
    .mockReturnValueOnce(responses.promise as never)
  const wrapper = mount(ModelForm, {
    props: {
      providerName: 'openai-one',
      providerType: 'openai',
      formState: { ...formData, adapterType: 'openai-chat' },
    },
    global: { stubs },
  })
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  wrapper
    .findAllComponents({ name: 'FormKit' })
    .find((c) => c.attributes('name') === 'adapterType')!
    .vm.$emit('input', 'openai-responses')
  await flushPromises()
  expect(wrapper.vm.isSubmitting).toBe(false)
  const responseProfile = { ...profile, adapterType: 'openai-responses' as const }
  responses.resolve({ data: responseProfile })
  chat.resolve({ data: profile })
  await flushPromises()
  expect(wrapper.emitted('submit')).toBeUndefined()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    adapterType: 'openai-responses',
    catalogParameters: responseProfile,
  })
})

it('keeps the saved adapter and snapshot while provider type loads, including on lookup failure', async () => {
  const responseProfile = { ...profile, adapterType: 'openai-responses' as const }
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockRejectedValue(
    new Error('offline'),
  )
  const wrapper = mount(ModelForm, {
    props: {
      providerName: 'openai-one',
      providerType: '',
      formState: {
        ...formData,
        adapterType: 'openai-responses',
        catalogParameters: responseProfile,
        parameterConstraints: { outputTokens: 1024 },
      },
    },
    global: { stubs },
  })
  await flushPromises()
  expect(aiConsoleApiClient.model.getModelCatalogParameters).not.toHaveBeenCalled()
  await wrapper.setProps({ providerType: 'openai' })
  await flushPromises()
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    adapterType: 'openai-responses',
    catalogParameters: responseProfile,
    parameterConstraints: { outputTokens: 1024 },
  })
})

it('allows saving a model without catalog coverage and does not add parameters', async () => {
  const wrapper = mount(ModelForm, {
    props: {
      providerName: 'openai-one',
      providerType: 'openai',
      formState: { ...formData, adapterType: 'openai-chat' },
    },
    global: { stubs },
  })
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    catalogParameters: undefined,
    parameterMappings: undefined,
    parameterConstraints: undefined,
  })
})

it('preserves standalone submission when the optional provider name is absent', async () => {
  const wrapper = mount(ModelForm, {
    props: {
      providerType: 'openai',
      formState: { ...formData, adapterType: 'openai-chat' },
    },
    global: { stubs },
  })
  wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', formData)
  await flushPromises()
  expect(aiConsoleApiClient.model.getModelCatalogParameters).not.toHaveBeenCalled()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    ...formData,
    adapterType: 'openai-chat',
    catalogParameters: undefined,
  })
})

async function settleForm() {
  await new Promise((resolve) => setTimeout(resolve, 25))
  await flushPromises()
  await nextTick()
}

const realForms: { unmount: () => void }[] = []
function mountRealForm(formState: ModelFormState, providerName?: string) {
  const wrapper = mount(ModelForm, {
    props: { providerType: 'openai', formState, providerName },
    global: {
      plugins: [
        [
          FormKitPlugin,
          defaultConfig({
            config: { delay: 0 },
            inputs: {
              switch: {
                type: 'input',
                schema: [
                  {
                    $el: 'input',
                    attrs: { type: 'checkbox', name: '$node.name', checked: '$_value' },
                  },
                ],
              },
            },
          }),
        ],
      ],
      stubs: {
        AdvancedSettingsCollapsible: { template: '<section><slot/></section>' },
        ParameterMappingFields: true,
        ModelParameterConstraintFields: true,
        ModelNativeParameterConstraintFields: true,
        ModelCatalogProvenance: true,
      },
    },
  })
  realForms.push(wrapper)
  return wrapper
}

afterEach(() => {
  realForms.splice(0).forEach((wrapper) => wrapper.unmount())
})

it.each([false, true])(
  'shows legacy manual %s capability constraints and saves consistent features without changing overrides',
  async (supported) => {
    const parameterConstraints = {
      toolCalling: supported,
      structuredOutput: supported,
      outputTokens: 1024,
      reasoning: { supported: true, toggle: false, budgetMaximum: 512 },
    }
    const original: ModelFormState = {
      ...formData,
      adapterType: 'openai-chat',
      features: supported ? ['vision'] : ['vision', 'tool-call', 'structured-output'],
      parameterConstraints,
    }
    const wrapper = mountRealForm(original)
    await settleForm()
    for (const feature of ['tool-call', 'structured-output']) {
      expect(
        wrapper.get<HTMLInputElement>(`input[name="features"][value="${feature}"]`).element.checked,
      ).toBe(supported)
    }
    await wrapper.get('form').trigger('submit')
    await settleForm()
    const saved = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
    expect(saved.parameterConstraints).toEqual(parameterConstraints)
    expect(saved.features).toEqual(
      supported
        ? ['vision', 'tool-call', 'structured-output', 'reasoning']
        : ['vision', 'reasoning'],
    )
    expect(original.features).toEqual(
      supported ? ['vision'] : ['vision', 'tool-call', 'structured-output'],
    )
  },
)

it('writes only the manually changed capability constraints through real checkbox changes', async () => {
  const wrapper = mountRealForm({
    ...formData,
    adapterType: 'openai-chat',
    features: ['tool-call'],
    parameterConstraints: { outputTokens: 1024, reasoning: { budgetMinimum: 128 } },
  })
  await settleForm()
  await wrapper.get('input[name="features"][value="tool-call"]').setValue(false)
  await settleForm()
  await wrapper.get('input[name="features"][value="structured-output"]').setValue(true)
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    features: ['structured-output'],
    parameterConstraints: {
      toolCalling: false,
      structuredOutput: true,
      outputTokens: 1024,
      reasoning: { budgetMinimum: 128 },
    },
  })
})

it.each([false, true])(
  'automatically displays and saves fresh %s support without creating manual overrides',
  async (supported) => {
    const catalog = deferredCatalog()
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
      catalog.promise as never,
    )
    const features = ['tool-call', 'structured-output', 'reasoning'] as const
    const constraints = {
      toolCalling: supported,
      structuredOutput: supported,
      reasoning: { supported },
    }
    const wrapper = mountRealForm(
      {
        ...formData,
        adapterType: 'openai-chat',
        features: supported ? [] : [...features],
        catalogParameters: {
          ...profile,
          constraints: {
            toolCalling: !supported,
            structuredOutput: !supported,
            reasoning: { supported: !supported },
          },
        },
      },
      'openai-one',
    )
    await settleForm()
    catalog.resolve({ data: { ...profile, constraints, featureEvidence: {} } })
    await settleForm()
    for (const feature of features) {
      expect(
        wrapper.get<HTMLInputElement>(`input[name="features"][value="${feature}"]`).element.checked,
      ).toBe(supported)
    }
    await wrapper.get('form').trigger('submit')
    await settleForm()
    const saved = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
    expect(saved.features || []).toEqual(supported ? features : [])
    expect(saved.parameterConstraints).toBeUndefined()
    expect(saved.catalogParameters?.constraints).toEqual(constraints)
  },
)

it.each([false, true])(
  'keeps real checkbox %s choices when a conflicting catalog request finishes later',
  async (supported) => {
    const catalog = deferredCatalog()
    vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters).mockReturnValue(
      catalog.promise as never,
    )
    const features = ['tool-call', 'structured-output', 'reasoning'] as const
    const wrapper = mountRealForm(
      {
        ...formData,
        adapterType: 'openai-chat',
        features: supported ? [] : [...features],
        parameterConstraints: { reasoning: { budgetMinimum: 512 } },
      },
      'openai-one',
    )
    await settleForm()
    for (const feature of features) {
      await wrapper.get(`input[name="features"][value="${feature}"]`).setValue(supported)
      await settleForm()
    }
    catalog.resolve({
      data: {
        ...profile,
        constraints: {
          toolCalling: !supported,
          structuredOutput: !supported,
          reasoning: { supported: !supported },
        },
      },
    })
    await settleForm()
    for (const feature of features) {
      expect(
        wrapper.get<HTMLInputElement>(`input[name="features"][value="${feature}"]`).element.checked,
      ).toBe(supported)
    }
    await wrapper.get('form').trigger('submit')
    await settleForm()
    const saved = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
    expect(saved.features || []).toEqual(supported ? features : [])
    expect(saved.parameterConstraints).toEqual({
      toolCalling: supported,
      structuredOutput: supported,
      reasoning: { supported, budgetMinimum: 512 },
    })
  },
)

it('does not create capability overrides during initialization or an unrelated feature change', async () => {
  const wrapper = mountRealForm({
    ...formData,
    adapterType: 'openai-chat',
    features: ['tool-call'],
    parameterConstraints: { outputTokens: 1024 },
  })
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    features: ['tool-call'],
    parameterConstraints: { outputTokens: 1024 },
  })
  await wrapper.get('input[name="features"][value="vision"]').setValue(true)
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  expect(wrapper.emitted('submit')?.[1]?.[0]).toMatchObject({
    features: ['tool-call', 'vision'],
    parameterConstraints: { outputTokens: 1024 },
  })
})

it('updates capability display from new props without treating value sync as user overrides', async () => {
  const wrapper = mountRealForm({
    ...formData,
    adapterType: 'openai-chat',
    features: ['tool-call'],
    parameterConstraints: { toolCalling: false },
  })
  await settleForm()
  expect(
    wrapper.get<HTMLInputElement>('input[name="features"][value="tool-call"]').element.checked,
  ).toBe(false)
  await wrapper.setProps({
    formState: {
      ...formData,
      adapterType: 'openai-chat',
      features: ['structured-output'],
      parameterConstraints: { toolCalling: true, outputTokens: 2048 },
    },
  })
  await settleForm()
  for (const feature of ['tool-call', 'structured-output']) {
    expect(
      wrapper.get<HTMLInputElement>(`input[name="features"][value="${feature}"]`).element.checked,
    ).toBe(true)
  }
  await wrapper.get('form').trigger('submit')
  await settleForm()
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
    features: ['structured-output', 'tool-call'],
    parameterConstraints: { toolCalling: true, outputTokens: 2048 },
  })
})

it('does not carry automatically filled features to a new model identity without catalog coverage', async () => {
  vi.mocked(aiConsoleApiClient.model.getModelCatalogParameters)
    .mockResolvedValueOnce({
      data: {
        ...profile,
        constraints: { toolCalling: true, structuredOutput: true, reasoning: { supported: true } },
      },
    } as never)
    .mockResolvedValueOnce({ data: undefined } as never)
  const wrapper = mount(ModelForm, {
    props: { providerType: 'openai', providerName: 'openai-one' },
    global: { stubs },
  })
  const modelId = wrapper
    .findAllComponents({ name: 'FormKit' })
    .find((component) => component.attributes('name') === 'modelId')!
  const featureField = () =>
    wrapper
      .findAllComponents({ name: 'FormKit' })
      .find((component) => component.attributes('name') === 'features')!
  modelId.vm.$emit('input', 'gpt-5')
  await flushPromises()
  expect(featureField().attributes('modelvalue')).toBe('tool-call,structured-output,reasoning')
  modelId.vm.$emit('input', 'unlisted-model')
  await flushPromises()
  expect(featureField().attributes('modelvalue')).toBe('')
  wrapper.unmount()
})

function catalogImageSpec(
  outputMediaTypes = ['image/png', 'image/jpeg', 'image/webp'],
): AiModel['spec'] {
  return {
    providerName: 'openai-one',
    modelId: 'gpt-image-1-mini',
    displayName: 'GPT Image 1 Mini',
    enabled: true,
    modelType: 'image-generation',
    adapterType: 'openai-image',
    capabilities: {
      imageGeneration: { outputMediaTypes },
      sources: { imageGeneration: 'unknown' },
    },
    capabilitySources: { imageGeneration: 'unknown' },
    catalogMatch: {
      providerId: 'openai',
      modelId: 'gpt-image-1-mini',
      fields: ['imageGeneration.outputMediaTypes', 'parameters'],
    },
    catalogParameters: {
      providerType: 'openai',
      providerBaseUrl: 'https://api.openai.com/v1',
      modelId: 'gpt-image-1-mini',
      adapterType: 'openai-image',
    },
  }
}

it.each([
  ['output format', 'textarea[name="imageGenerationOutputMediaTypes"]', 'image/jpeg', 1],
  ['image count', 'input[name="imageGenerationMaxImagesPerCall"]', '2', 2],
] as const)(
  'preserves inherited image capabilities when editing only the %s',
  async (field, selector, value, expectedCount) => {
    const previous = catalogImageSpec()
    const original = structuredClone(previous)
    const wrapper = mountRealForm(previous, previous.providerName)
    await settleForm()
    await wrapper.get(selector).setValue(value)
    await settleForm()
    await wrapper.get('form').trigger('submit')
    await settleForm()
    const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
    expect(draft).toBeDefined()
    const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
    expect(saved.capabilities?.imageGeneration?.textToImage).toBe(true)
    expect(saved.capabilities?.imageGeneration?.maxImagesPerCall).toBe(expectedCount)
    expect(saved.capabilities?.imageGeneration?.outputMediaTypes).toEqual(
      field === 'output format'
        ? ['image/jpeg']
        : previous.capabilities?.imageGeneration?.outputMediaTypes,
    )
    expect(saved.capabilities?.sources?.imageGeneration).toBe('manual')
    expect(saved.capabilitySources?.imageGeneration).toBe('manual')
    expect(saved.catalogMatch?.fields).toEqual(
      field === 'output format' ? ['parameters'] : previous.catalogMatch?.fields,
    )
    expect(saved.catalogParameters).toEqual(previous.catalogParameters)
    expect(previous).toEqual(original)
  },
)

it('keeps deliberately cleared image capabilities unknown while changing the output format', async () => {
  const previous = catalogImageSpec()
  previous.capabilities!.imageGeneration!.textToImage = true
  previous.capabilities!.imageGeneration!.maxImagesPerCall = 1
  const wrapper = mountRealForm(previous, previous.providerName)
  await settleForm()
  await wrapper.get('select[name="imageGenerationTextToImage"]').setValue('')
  await wrapper.get('input[name="imageGenerationMaxImagesPerCall"]').setValue('')
  await wrapper.get('textarea[name="imageGenerationOutputMediaTypes"]').setValue('image/jpeg')
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
  expect(draft).toBeDefined()
  const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
  expect(saved.capabilities?.imageGeneration?.textToImage).toBeUndefined()
  expect(saved.capabilities?.imageGeneration?.maxImagesPerCall).toBeUndefined()
  expect(saved.capabilities?.imageGeneration?.outputMediaTypes).toEqual(['image/jpeg'])
  expect(saved.capabilities?.sources?.imageGeneration).toBe('manual')
  expect(saved.capabilitySources?.imageGeneration).toBe('manual')
})

it('does not replace existing manual unknown capabilities when editing an image format', async () => {
  const previous = catalogImageSpec()
  previous.capabilitySources!.imageGeneration = 'manual'
  previous.capabilities!.sources!.imageGeneration = 'manual'
  const wrapper = mountRealForm(previous, previous.providerName)
  await settleForm()
  await wrapper.get('textarea[name="imageGenerationOutputMediaTypes"]').setValue('image/jpeg')
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
  expect(draft).toBeDefined()
  const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
  expect(saved.capabilities?.imageGeneration?.textToImage).toBeUndefined()
  expect(saved.capabilities?.imageGeneration?.maxImagesPerCall).toBeUndefined()
  expect(saved.capabilities?.imageGeneration?.outputMediaTypes).toEqual(['image/jpeg'])
  expect(saved.capabilities?.sources?.imageGeneration).toBe('manual')
  expect(saved.capabilitySources?.imageGeneration).toBe('manual')
})

it('leaves image capabilities unconfigured after clearing the last catalog field in the form', async () => {
  const previous = catalogImageSpec()
  const original = structuredClone(previous)
  const wrapper = mountRealForm(previous, previous.providerName)
  await settleForm()
  await wrapper.get('textarea[name="imageGenerationOutputMediaTypes"]').setValue('')
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
  expect(draft).toBeDefined()
  expect(draft.capabilities?.imageGeneration).toBeUndefined()
  const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
  expect(saved.capabilities?.imageGeneration).toBeUndefined()
  expect(saved.capabilities?.sources?.imageGeneration).toBeUndefined()
  expect(saved.capabilitySources?.imageGeneration).toBeUndefined()
  expect(saved.catalogMatch?.fields).toEqual(['parameters'])
  expect(saved.catalogParameters).toEqual(previous.catalogParameters)
  expect(previous).toEqual(original)
})

it('keeps an explicit unsupported image choice when the catalog formats are cleared', async () => {
  const previous = catalogImageSpec()
  const wrapper = mountRealForm(previous, previous.providerName)
  await settleForm()
  await wrapper.get('textarea[name="imageGenerationOutputMediaTypes"]').setValue('')
  await wrapper.get('select[name="imageGenerationTextToImage"]').setValue('false')
  await settleForm()
  await wrapper.get('form').trigger('submit')
  await settleForm()
  const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
  expect(draft).toBeDefined()
  const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
  expect(saved.capabilities?.imageGeneration?.textToImage).toBe(false)
  expect(saved.capabilities?.imageGeneration?.outputMediaTypes).toBeUndefined()
  expect(saved.capabilitySources?.imageGeneration).toBe('manual')
  expect(saved.catalogMatch?.fields).toEqual(['parameters'])
})

it('preserves explicit empty catalog formats when saving the unchanged image form', async () => {
  const previous = catalogImageSpec([])
  const wrapper = mountRealForm(previous, previous.providerName)
  await settleForm()
  expect(
    wrapper.get<HTMLTextAreaElement>('textarea[name="imageGenerationOutputMediaTypes"]').element
      .value,
  ).toBe('')
  await wrapper.get('form').trigger('submit')
  await settleForm()
  const draft = wrapper.emitted('submit')?.[0]?.[0] as ModelFormState
  expect(draft).toBeDefined()
  const saved = reconcileModelCapabilities(previous, { ...previous, ...draft })
  expect(saved.capabilities?.imageGeneration?.outputMediaTypes).toEqual([])
  expect(saved.capabilitySources?.imageGeneration).toBe('unknown')
  expect(saved.catalogMatch?.fields).toEqual(previous.catalogMatch?.fields)
  expect(saved.catalogParameters).toEqual(previous.catalogParameters)
})