import { aiConsoleApiClient } from '@/api'
import type { ModelParameterProfile } from '@/api/generated'
import type { ModelFormState } from '@/types/form'
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
        supportedModelTypes: ['language'],
        supportedFeatures: ['tool-call', 'structured-output', 'reasoning', 'vision', 'audio-input'],
        adapters: [
          { adapterType: 'openai-chat', modelType: 'language', recommended: true },
          { adapterType: 'openai-responses', modelType: 'language' },
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