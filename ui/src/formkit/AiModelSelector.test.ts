import type { ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import AiModelSelector from './AiModelSelector.vue'

vi.mock('@/composables/use-model-options-fetch', () => ({
  useModelOptionsFetch: vi.fn(),
}))

vi.mock('@halo-dev/components', () => ({
  VLoading: { template: '<div>Loading</div>' },
}))

const modelOptions = ref<ModelOption[]>([])
const isLoading = ref(false)
const wrappers: VueWrapper[] = []

describe('AiModelSelector', () => {
  beforeEach(() => {
    modelOptions.value = [
      modelOption({
        name: 'deepseek-chat',
        displayName: 'DeepSeek Chat',
        modelId: 'deepseek-chat',
        providerName: 'deepseek',
        providerDisplayName: 'DeepSeek',
        providerTypeDisplayName: '深度求索 DeepSeek',
      }),
      modelOption({
        name: 'openai-gpt-4o',
        displayName: 'GPT-4o',
        modelId: 'gpt-4o',
        providerName: 'openai',
        providerDisplayName: 'OpenAI Production',
        providerTypeDisplayName: 'OpenAI',
      }),
      modelOption({
        name: 'disabled-model',
        displayName: 'Disabled Model',
        modelId: 'disabled-model',
        providerName: 'openai',
        providerDisplayName: 'OpenAI Production',
        providerTypeDisplayName: 'OpenAI',
        available: false,
        unavailableReason: 'model-disabled',
      }),
    ]
    isLoading.value = false
    vi.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading,
    } as ReturnType<typeof useModelOptionsFetch>)
  })

  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) {
      wrapper.unmount()
    }
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('selects an available model and updates the controlled value', async () => {
    const wrapper = mountSelector()

    await openSelector(wrapper)
    await selectOption('GPT-4o')

    expect(wrapper.emitted('update:modelValue')).toContainEqual(['openai-gpt-4o'])
    expect(wrapper.get('[role="combobox"]').text()).toContain('GPT-4o')
    expect(document.querySelector('[role="listbox"]')).toBeNull()
  })

  it('clears a selected model with undefined', async () => {
    const wrapper = mountSelector({ modelValue: 'deepseek-chat' })

    await wrapper.get('button[aria-label="清除"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toContainEqual([undefined])
    expect(wrapper.get('[role="combobox"]').text()).toContain('请选择模型')
  })

  it('filters models by keyword and resets the search after closing', async () => {
    const wrapper = mountSelector({ searchPlaceholder: '搜索模型...' })

    await openSelector(wrapper)
    await searchFor('deepseek')

    expect(getOptions()).toHaveLength(1)
    expect(getOptions()[0]?.textContent).toContain('DeepSeek Chat')
    expect(document.body.textContent).toContain('DeepSeek (深度求索 DeepSeek)')

    await pressSearchKey('Escape')
    await openSelector(wrapper)

    expect((getSearchInput() as HTMLInputElement).value).toBe('')
    expect(getOptions()).toHaveLength(3)
    expect(getOptions().map((option) => option.textContent)).toEqual([
      expect.stringContaining('DeepSeek Chat'),
      expect.stringContaining('GPT-4o'),
      expect.stringContaining('Disabled Model'),
    ])
  })

  it('renders unavailable models without allowing selection', async () => {
    const wrapper = mountSelector()

    await openSelector(wrapper)
    const option = getOption('Disabled Model')

    expect(option.getAttribute('aria-disabled')).toBe('true')
    expect(option.textContent).toContain('模型已禁用')

    await dispatchPointerEvent(option, 'pointerup')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(document.querySelector('[role="listbox"]')).not.toBeNull()
  })

  it('reflects external model value changes and clearing', async () => {
    const wrapper = mountSelector({ modelValue: 'deepseek-chat' })

    expect(wrapper.get('[role="combobox"]').text()).toContain('DeepSeek Chat')

    await wrapper.setProps({ modelValue: 'openai-gpt-4o' })
    expect(wrapper.get('[role="combobox"]').text()).toContain('GPT-4o')

    await wrapper.setProps({ modelValue: undefined })
    expect(wrapper.get('[role="combobox"]').text()).toContain('请选择模型')
  })
})

function mountSelector(props: Record<string, unknown> = {}) {
  const wrapper = mount(AiModelSelector, {
    attachTo: document.body,
    props: {
      ...props,
      'onUpdate:modelValue': (value: string | undefined) => {
        void wrapper.setProps({ modelValue: value })
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

async function openSelector(wrapper: VueWrapper) {
  await wrapper.get('[role="combobox"]').trigger('pointerdown', {
    button: 0,
    ctrlKey: false,
    pointerId: 1,
    pointerType: 'mouse',
  })
  await flushPromises()
}

async function selectOption(label: string) {
  await dispatchPointerEvent(getOption(label), 'pointerup')
}

async function searchFor(keyword: string) {
  const input = getSearchInput() as HTMLInputElement
  input.value = keyword
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}

async function pressSearchKey(key: string) {
  getSearchInput().dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key }))
  await flushPromises()
}

function getSearchInput() {
  const input = document.querySelector('input[placeholder="搜索模型..."]')
  if (!(input instanceof HTMLInputElement)) {
    throw new Error('Expected model search input to be rendered')
  }
  return input
}

function getOption(label: string) {
  const option = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(
    (element) => element.textContent?.includes(label),
  )
  if (!option) {
    throw new Error(`Expected option ${label} to be rendered`)
  }
  return option
}

function getOptions() {
  return Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'))
}

async function dispatchPointerEvent(element: HTMLElement, type: string) {
  element.dispatchEvent(
    new PointerEvent(type, {
      bubbles: true,
      button: 0,
      pointerId: 1,
      pointerType: 'mouse',
    }),
  )
  await flushPromises()
}

function modelOption({
  name,
  displayName,
  modelId,
  providerName,
  providerDisplayName,
  providerTypeDisplayName,
  available = true,
  unavailableReason,
}: {
  name: string
  displayName: string
  modelId: string
  providerName: string
  providerDisplayName: string
  providerTypeDisplayName: string
  available?: boolean
  unavailableReason?: ModelOption['unavailableReason']
}): ModelOption {
  return {
    name,
    displayName,
    modelId,
    modelType: 'language',
    features: [],
    available,
    unavailableReason,
    provider: {
      name: providerName,
      displayName: providerDisplayName,
      providerType: providerName,
      providerTypeDisplayName,
      enabled: true,
    },
  }
}
