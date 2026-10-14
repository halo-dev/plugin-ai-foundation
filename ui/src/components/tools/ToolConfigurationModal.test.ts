import { aiConsoleApiClient } from '@/api'
import type { ToolDescriptor } from '@/api/generated'
import { getNode } from '@formkit/core'
import { defaultConfig, plugin as formKitPlugin } from '@formkit/vue'
import { Toast } from '@halo-dev/components'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ToolConfigurationModal from './ToolConfigurationModal.vue'

vi.mock('@/api', () => ({
  aiConsoleApiClient: { tool: { getToolConfiguration: vi.fn(), updateToolConfiguration: vi.fn() } },
}))
vi.mock('@halo-dev/components', () => ({
  Toast: { success: vi.fn() },
  VLoading: { template: '<div role="status">Loading</div>' },
  VButton: {
    props: ['disabled', 'loading'],
    template: '<button type="button" :disabled="disabled || loading"><slot /></button>',
  },
  VSpace: { template: '<div><slot /></div>' },
  VModal: {
    props: ['title'],
    emits: ['close'],
    setup(
      _props: unknown,
      context: { expose: (value: { close: () => void }) => void; emit: (event: 'close') => void },
    ) {
      context.expose({ close: () => context.emit('close') })
    },
    template: '<div role="dialog">{{ title }}<slot /><slot name="footer" /></div>',
  },
}))

const tool: ToolDescriptor = {
  id: 'ai-foundation/web_search',
  pluginName: 'ai-foundation',
  localName: 'web_search',
  displayName: '联网搜索',
  configurationSchema: [
    { $formkit: 'text', name: 'apiKeySecretName', label: '密钥引用', delay: 0 },
  ] as unknown as ToolDescriptor['configurationSchema'],
}
const wrappers: VueWrapper[] = []
const clients: QueryClient[] = []

describe('ToolConfigurationModal', () => {
  beforeEach(() => {
    vi.mocked(aiConsoleApiClient.tool.getToolConfiguration).mockResolvedValue({
      data: { values: { apiKeySecretName: 'saved-secret' } },
    } as unknown as Awaited<ReturnType<typeof aiConsoleApiClient.tool.getToolConfiguration>>)
    vi.mocked(aiConsoleApiClient.tool.updateToolConfiguration).mockResolvedValue(
      {} as Awaited<ReturnType<typeof aiConsoleApiClient.tool.updateToolConfiguration>>,
    )
  })

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    clients.splice(0).forEach((client) => client.clear())
    vi.clearAllMocks()
  })

  it('loads current values and renders the tool-provided FormKit fields', async () => {
    const { wrapper } = mountConfiguration()
    await flushPromises()
    expect(aiConsoleApiClient.tool.getToolConfiguration).toHaveBeenCalledWith({
      pluginName: 'ai-foundation',
      localName: 'web_search',
    })
    expect(wrapper.get('input').element.value).toBe('saved-secret')
    expect(wrapper.text()).toContain('密钥引用')
  })

  it('lets a failed initial load be retried', async () => {
    vi.mocked(aiConsoleApiClient.tool.getToolConfiguration).mockRejectedValueOnce(
      new Error('Offline'),
    )
    const { wrapper } = mountConfiguration()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('无法加载工具配置')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(aiConsoleApiClient.tool.getToolConfiguration).toHaveBeenCalledTimes(2)
    expect(wrapper.get('input').element.value).toBe('saved-secret')
  })

  it('keeps the form open and its edited values when saving fails', async () => {
    vi.mocked(aiConsoleApiClient.tool.updateToolConfiguration).mockRejectedValueOnce(
      new Error('Invalid configuration'),
    )
    const { wrapper } = mountConfiguration()
    await flushPromises()
    await wrapper.get('input').setValue('edited-secret')
    await getNode(wrapper.get('form').attributes('id')!)?.settled
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('配置保存失败')
    expect(wrapper.get('input').element.value).toBe('edited-secret')
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(Toast.success).not.toHaveBeenCalled()
  })

  it('saves per-tool values, refreshes the catalog, and closes on success', async () => {
    const { wrapper, queryClient } = mountConfiguration()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    await flushPromises()
    await wrapper.get('input').setValue('new-secret')
    await getNode(wrapper.get('form').attributes('id')!)?.settled
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(aiConsoleApiClient.tool.updateToolConfiguration).toHaveBeenCalledWith({
      pluginName: 'ai-foundation',
      localName: 'web_search',
      toolConfig: { values: { apiKeySecretName: 'new-secret' } },
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['plugin:ai-foundation:tools'] })
    expect(Toast.success).toHaveBeenCalledWith('工具配置已保存')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('preserves active edits when another component refreshes the configuration', async () => {
    const { wrapper, queryClient } = mountConfiguration()
    await flushPromises()
    await wrapper.get('input').setValue('editing-secret')
    queryClient.setQueryData(
      ['plugin:ai-foundation:tool-configuration', 'ai-foundation', 'web_search'],
      { values: { apiKeySecretName: 'external-secret' } },
    )
    await flushPromises()
    expect(wrapper.get('input').element.value).toBe('editing-secret')
  })

  it('reacts to conditional FormKit fields and saves their values without runtime slot data', async () => {
    vi.mocked(aiConsoleApiClient.tool.getToolConfiguration).mockResolvedValueOnce({
      data: { values: { advanced: false } },
    } as unknown as Awaited<ReturnType<typeof aiConsoleApiClient.tool.getToolConfiguration>>)
    const conditionalTool: ToolDescriptor = {
      ...tool,
      configurationSchema: [
        { $formkit: 'checkbox', name: 'advanced', label: '高级配置', delay: 0 },
        {
          if: '$advanced',
          then: { $formkit: 'text', name: 'endpoint', label: '服务地址', delay: 0 },
        },
      ] as unknown as ToolDescriptor['configurationSchema'],
    }
    const { wrapper } = mountConfiguration(undefined, conditionalTool)
    await flushPromises()
    expect(wrapper.find('input[name="endpoint"]').exists()).toBe(false)
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await getNode(wrapper.get('form').attributes('id')!)?.settled
    await flushPromises()
    expect(wrapper.text()).toContain('服务地址')
    await wrapper.get('input[name="endpoint"]').setValue('https://example.com')
    await getNode(wrapper.get('form').attributes('id')!)?.settled
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(aiConsoleApiClient.tool.updateToolConfiguration).toHaveBeenCalledWith({
      pluginName: 'ai-foundation',
      localName: 'web_search',
      toolConfig: { values: { advanced: true, endpoint: 'https://example.com' } },
    })
  })

  it('fetches current configuration each time the modal opens', async () => {
    const { wrapper, queryClient } = mountConfiguration()
    await flushPromises()
    wrapper.unmount()
    vi.mocked(aiConsoleApiClient.tool.getToolConfiguration).mockResolvedValueOnce({
      data: { values: { apiKeySecretName: 'latest-secret' } },
    } as unknown as Awaited<ReturnType<typeof aiConsoleApiClient.tool.getToolConfiguration>>)
    const next = mountConfiguration(queryClient).wrapper
    await flushPromises()
    expect(aiConsoleApiClient.tool.getToolConfiguration).toHaveBeenCalledTimes(2)
    expect(next.get('input').element.value).toBe('latest-secret')
  })
})

function mountConfiguration(existingClient?: QueryClient, configuredTool = tool) {
  const queryClient =
    existingClient || new QueryClient({ defaultOptions: { queries: { retry: false } } })
  if (!existingClient) clients.push(queryClient)
  const wrapper = mount(ToolConfigurationModal, {
    props: { tool: configuredTool },
    global: {
      plugins: [
        [VueQueryPlugin, { queryClient }],
        [formKitPlugin, defaultConfig()],
      ],
    },
  })
  wrappers.push(wrapper)
  return { wrapper, queryClient }
}
