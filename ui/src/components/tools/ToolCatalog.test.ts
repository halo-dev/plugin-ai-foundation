import { aiConsoleApiClient } from '@/api'
import type { ToolDescriptor } from '@/api/generated'
import { useToolsFetch } from '@/composables/use-tools-fetch'
import { Toast } from '@halo-dev/components'
import { useQueryClient } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import ToolCatalog from './ToolCatalog.vue'
import ToolCatalogList from './ToolCatalogList.vue'

vi.mock('@/composables/use-tools-fetch', () => ({
  QK_TOOLS: 'plugin:ai-foundation:tools',
  useToolsFetch: vi.fn(),
}))
vi.mock('@/api', () => ({ aiConsoleApiClient: { tool: { updateToolState: vi.fn() } } }))
vi.mock('@tanstack/vue-query', () => ({ useQueryClient: vi.fn(), useQuery: vi.fn() }))
vi.mock('@halo-dev/components', () => ({
  Toast: { success: vi.fn(), error: vi.fn() },
  VLoading: { template: '<div role="status">Loading</div>' },
  VButton: {
    props: ['disabled'],
    template: '<button type="button" :disabled="disabled"><slot /></button>',
  },
  VTag: { template: '<span><slot /></span>' },
  VStatusDot: { props: ['text'], template: '<span>{{ text }}</span>' },
  VSwitch: {
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue'],
    template:
      '<button :disabled="disabled" type="button" role="switch" :aria-checked="modelValue" @click="$emit(\'update:modelValue\', !modelValue)" />',
  },
  VCard: { template: '<div><slot name="header" /><slot /><slot name="footer" /></div>' },
  VEmpty: { props: ['title', 'message'], template: '<div>{{ title }} {{ message }}</div>' },
  VModal: { props: ['title'], template: '<div role="dialog">{{ title }}<slot /></div>' },
}))

const data = ref<ToolDescriptor[]>([])
const isLoading = ref(false)
const isError = ref(false)
const refetch = vi.fn()
const invalidateQueries = vi.fn(async () => undefined)
const wrappers: VueWrapper[] = []

describe('ToolCatalog', () => {
  beforeEach(() => {
    data.value = [
      {
        id: 'ai-foundation/search_site',
        pluginName: 'ai-foundation',
        localName: 'search_site',
        name: 'ai_foundation_search_site',
        displayName: '站内搜索',
        category: 'content',
        tags: ['公开内容'],
        enabled: true,
        configured: true,
        status: 'ENABLED',
        inputSchema: {
          type: 'object',
          properties: { keyword: { type: 'string' } },
        } as unknown as ToolDescriptor['inputSchema'],
        outputSchema: { type: 'array' } as unknown as ToolDescriptor['outputSchema'],
      },
    ]
    isLoading.value = false
    isError.value = false
    vi.mocked(useQueryClient).mockReturnValue({ invalidateQueries } as unknown as ReturnType<
      typeof useQueryClient
    >)
    vi.mocked(aiConsoleApiClient.tool.updateToolState).mockResolvedValue(
      {} as Awaited<ReturnType<typeof aiConsoleApiClient.tool.updateToolState>>,
    )
    vi.mocked(useToolsFetch).mockReturnValue({
      data,
      isLoading,
      isError,
      isFetching: ref(false),
      refetch,
    } as unknown as ReturnType<typeof useToolsFetch>)
  })

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    vi.clearAllMocks()
  })

  it('shows category, source, identity, and detailed input/output schemas', async () => {
    const wrapper = mountCatalog()
    expect(wrapper.text()).toContain('内容')
    expect(wrapper.text()).toContain('ai-foundation')
    expect(wrapper.text()).toContain('公开内容')
    await wrapper.get('button[aria-label="查看 站内搜索 详情"]').trigger('click')
    const details = wrapper.get('[role="dialog"]')
    expect(details.text()).toContain('ai_foundation_search_site')
    expect(details.text()).toContain('keyword')
    expect(details.text()).toContain('array')
  })

  it('keeps unconfigured and disabled tools visible in the management catalog', () => {
    data.value = [
      {
        ...data.value[0],
        id: 'ai-foundation/web_search',
        displayName: '联网搜索',
        localName: 'web_search',
        configured: false,
        status: 'UNCONFIGURED',
      },
      {
        ...data.value[0],
        id: 'ai-foundation/read_web_page',
        displayName: '网页读取',
        localName: 'read_web_page',
        enabled: false,
        status: 'DISABLED',
      },
    ]
    const wrapper = mountCatalog()
    expect(wrapper.text()).toContain('联网搜索')
    expect(wrapper.text()).toContain('未配置')
    expect(wrapper.text()).toContain('网页读取')
    expect(wrapper.text()).toContain('已禁用')
  })

  it('groups mixed categories by source plugin and removes empty groups after filtering', async () => {
    data.value = [
      data.value[0]!,
      {
        ...data.value[0],
        id: 'other-plugin/search',
        pluginName: 'other-plugin',
        localName: 'search',
        displayName: '其他插件搜索',
      },
      {
        ...data.value[0],
        id: 'ai-foundation/read_web_page',
        localName: 'read_web_page',
        displayName: '网页读取',
        category: 'web',
      },
    ]
    const wrapper = mountCatalog()
    expect(wrapper.findAll('section[aria-label]')).toHaveLength(2)
    const foundation = wrapper.get('section[aria-label="AI Foundation 提供的工具"]')
    expect(foundation.get('h2').text()).toBe('AI Foundation')
    expect(foundation.get('header').text()).toContain('2')
    expect(
      foundation.findAll('[data-tool-id]').map((card) => card.attributes('data-tool-id')),
    ).toEqual(['ai-foundation/search_site', 'ai-foundation/read_web_page'])
    const other = wrapper.get('section[aria-label="other-plugin 提供的工具"]')
    expect(other.findAll('[data-tool-id]').map((card) => card.attributes('data-tool-id'))).toEqual([
      'other-plugin/search',
    ])
    expect(wrapper.findAllComponents(ToolCatalogList)).toHaveLength(2)

    await wrapper.get('input[aria-label="搜索工具"]').setValue('站内搜索')
    expect(wrapper.findAll('section[aria-label]')).toHaveLength(1)
    expect(wrapper.find('section[aria-label="other-plugin 提供的工具"]').exists()).toBe(false)
    expect(wrapper.get('section[aria-label="AI Foundation 提供的工具"] header').text()).toContain(
      '1',
    )
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
  })

  it('updates per-tool enabled state and refreshes the shared catalog', async () => {
    const wrapper = mountCatalog()
    wrapper.findComponent(ToolCatalogList).vm.$emit('enabled', data.value[0], false)
    await flushPromises()
    expect(aiConsoleApiClient.tool.updateToolState).toHaveBeenCalledWith({
      pluginName: 'ai-foundation',
      localName: 'search_site',
      toolEnabledRequest: { enabled: false },
    })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['plugin:ai-foundation:tools'] })
    expect(Toast.success).toHaveBeenCalledWith('工具已禁用')
  })

  it('finds group metadata and leaf names, expanding only the matching grouped tools', async () => {
    data.value = groupedCatalog()
    const wrapper = mountCatalog()
    expect(wrapper.findAll('[data-tool-group-id]')).toHaveLength(1)
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
    await wrapper.get('input[aria-label="搜索工具"]').setValue('文章管理')
    expect(wrapper.get('[data-tool-group-id] [role="heading"]').text()).toBe('文章管理')
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(2)
    expect(wrapper.text()).toContain('当前显示 2 个')
    await wrapper.get('input[aria-label="搜索工具"]').setValue('读取正文')
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
    expect(wrapper.get('[data-tool-id]').attributes('data-tool-id')).toBe('ai-foundation/get_post')
    expect(wrapper.text()).toContain('当前显示 1 个')
    expect(wrapper.find('section[aria-label="standalone-plugin 提供的工具"]').exists()).toBe(false)
  })

  it('updates a grouped leaf through its switch without treating the group as an enabled resource', async () => {
    data.value = groupedCatalog()
    const wrapper = mountCatalog()
    const group = wrapper.get('[data-tool-group-id]')
    expect(group.findAll('[role="switch"]')).toHaveLength(0)
    await group.get('button[aria-label="展开 文章管理 工具组"]').trigger('click')
    await group.get('button[aria-label="启用 读取正文"]').trigger('click')
    await flushPromises()
    expect(aiConsoleApiClient.tool.updateToolState).toHaveBeenCalledWith({
      pluginName: 'ai-foundation',
      localName: 'get_post',
      toolEnabledRequest: { enabled: false },
    })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['plugin:ai-foundation:tools'] })
  })

  it('reports a failed tool state change without claiming success', async () => {
    vi.mocked(aiConsoleApiClient.tool.updateToolState).mockRejectedValue(new Error('Unavailable'))
    const wrapper = mountCatalog()
    wrapper.findComponent(ToolCatalogList).vm.$emit('enabled', data.value[0], false)
    await flushPromises()
    expect(Toast.error).toHaveBeenCalledWith('工具状态更新失败，请重试')
    expect(invalidateQueries).not.toHaveBeenCalled()
  })

  it('blocks duplicate changes while the tool state request is pending', async () => {
    type StateResponse = Awaited<ReturnType<typeof aiConsoleApiClient.tool.updateToolState>>
    let resolveRequest!: (value: StateResponse) => void
    const request = new Promise<StateResponse>((resolve) => {
      resolveRequest = resolve
    })
    vi.mocked(aiConsoleApiClient.tool.updateToolState).mockReturnValue(request)
    const wrapper = mountCatalog()
    const list = wrapper.findComponent(ToolCatalogList)
    list.vm.$emit('enabled', data.value[0], false)
    list.vm.$emit('enabled', data.value[0], false)
    await nextTick()
    expect(aiConsoleApiClient.tool.updateToolState).toHaveBeenCalledOnce()
    expect(list.props('pendingIds')).toEqual(['ai-foundation/search_site'])
    resolveRequest({} as StateResponse)
    await flushPromises()
    expect(list.props('pendingIds')).toEqual([])
  })

  it('reports a failed catalog separately and retries', async () => {
    isError.value = true
    const wrapper = mountCatalog()
    expect(wrapper.get('[role="alert"]').text()).toContain('无法加载工具列表')
    expect(wrapper.text()).not.toContain('暂无工具')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(refetch).toHaveBeenCalledOnce()
  })

  it('distinguishes loading, empty, and no matching tools', async () => {
    isLoading.value = true
    const wrapper = mountCatalog()
    expect(wrapper.get('[role="status"]').text()).toBe('Loading')
    isLoading.value = false
    await wrapper.get('input[aria-label="搜索工具"]').setValue('not present')
    expect(wrapper.text()).toContain('无匹配结果')
    data.value = []
    await wrapper.get('input[aria-label="搜索工具"]').setValue('')
    expect(wrapper.text()).toContain('暂无工具')
  })
})

function groupedCatalog(): ToolDescriptor[] {
  const group = {
    id: 'ai-foundation/posts',
    displayName: '文章管理',
    description: '管理本站文章',
  }
  return [
    {
      ...data.value[0],
      id: 'ai-foundation/list_posts',
      localName: 'list_posts',
      displayName: '查询文章',
      group,
    },
    {
      ...data.value[0],
      id: 'ai-foundation/get_post',
      localName: 'get_post',
      displayName: '读取正文',
      group,
    },
    {
      ...data.value[0],
      id: 'standalone-plugin/clock',
      pluginName: 'standalone-plugin',
      displayName: '独立时间工具',
    },
  ]
}

function mountCatalog() {
  const wrapper = mount(ToolCatalog, {
    global: {
      stubs: {
        FormKit: {
          props: ['type', 'id', 'label', 'modelValue', 'disabled', 'placeholder', 'options'],
          emits: ['update:modelValue'],
          template: `<div><label :for="id">{{ label }}</label>
            <select v-if="type === 'select'" :id="id" :value="modelValue" :disabled="disabled" :aria-label="label"
              @change="$emit('update:modelValue', $event.target.value)">
              <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <input v-else :id="id" type="text" :aria-label="label" :value="modelValue" :disabled="disabled"
              @input="$emit('update:modelValue', $event.target.value)" /></div>`,
        },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
