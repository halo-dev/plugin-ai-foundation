import { useToolsFetch } from '@/composables/use-tools-fetch'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import type { CatalogTool } from '@/utils/tools'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiToolSelector from './AiToolSelector.vue'

vi.mock('@/composables/use-tools-fetch', () => ({ useToolsFetch: vi.fn() }))
vi.mock('@halo-dev/components', () => ({
  IconRefreshLine: { template: '<span />' },
  VLoading: { template: '<div role="status">Loading</div>' },
  VButton: {
    props: ['disabled', 'loading'],
    template:
      '<button type="button" :disabled="disabled || loading"><slot name="icon" /><slot /></button>',
  },
  VStatusDot: { props: ['text'], template: '<span>{{ text }}</span>' },
  VTag: { template: '<span><slot /></span>' },
}))

const data = ref<CatalogTool[]>([])
const isLoading = ref(false)
const isError = ref(false)
const isFetching = ref(false)
const refetch = vi.fn()
const wrappers: VueWrapper[] = []
const siteId = 'ai-foundation/search_site'
const webId = 'other-plugin/search'
const disabledId = 'ai-foundation/disabled'
const unconfiguredId = 'other-plugin/unconfigured'

describe('AiToolSelector', () => {
  beforeEach(() => {
    data.value = catalog()
    isLoading.value = false
    isError.value = false
    isFetching.value = false
    vi.mocked(useToolsFetch).mockReturnValue({
      data,
      isLoading,
      isError,
      isFetching,
      refetch,
    } as unknown as ReturnType<typeof useToolsFetch>)
  })
  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('starts collapsed and opens tool choices in a real teleported popover', async () => {
    const wrapper = mountSelector()
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.querySelector('input[aria-label="搜索工具"]')).toBeNull()
    await openSelector(wrapper)
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('true')
    expect(wrapper.element.contains(popup())).toBe(false)
    expect(checkbox('站内搜索').closest('section')).toBe(checkbox('已禁用工具').closest('section'))
    expect(checkbox('网页搜索').closest('section')).toBe(checkbox('待配置工具').closest('section'))
    expect(checkbox('站内搜索').closest('section')).not.toBe(
      checkbox('网页搜索').closest('section'),
    )
    expect(checkboxes()).toHaveLength(4)
  })

  it('reflects controlled changes in the collapsed selection name and count', async () => {
    const wrapper = mountSelector({ modelValue: [siteId] })
    expect(trigger(wrapper).text()).toContain('站内搜索')
    const summaryId = trigger(wrapper).attributes('aria-describedby')?.split(' ')[0]
    expect(document.getElementById(summaryId || '')?.textContent).toContain('1')
    await wrapper.setProps({ modelValue: [webId] })
    expect(trigger(wrapper).text()).toContain('网页搜索')
    expect(trigger(wrapper).text()).not.toContain('站内搜索')
    await wrapper.setProps({ modelValue: [siteId, webId] })
    expect(trigger(wrapper).text()).toContain('2')
    await wrapper.setProps({ modelValue: [] })
    expect(trigger(wrapper).text()).not.toContain('站内搜索')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('keeps the supplied FormKit field id on the actual native trigger', () => {
    const wrapper = mountSelector({ id: 'workflow-tools' })
    expect(trigger(wrapper).attributes('id')).toBe('workflow-tools')
    expect(document.getElementById('workflow-tools')).toBe(trigger(wrapper).element)
    expect(wrapper.get<HTMLLabelElement>('label').element.control).toBe(trigger(wrapper).element)
  })

  it('names the popover with the current label heading inside the dialog', async () => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    await wrapper.setProps({ label: '助手工具' })
    const headingIds = popup().getAttribute('aria-labelledby')?.trim().split(/\s+/) || []
    expect(headingIds).toHaveLength(1)
    const heading = document.getElementById(headingIds[0] || '')
    expect(heading).not.toBeNull()
    expect(heading?.tagName).toBe('H2')
    expect(popup().contains(heading)).toBe(true)
    expect(heading?.textContent).toBe('助手工具')
  })

  it('selects and deselects stable ids without closing the multi-select popover', async () => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    await setChecked('站内搜索', true)
    await setChecked('网页搜索', true)
    expect(lastSelection(wrapper)).toEqual([siteId, webId])
    expect(popup()).toBeTruthy()
    expect(trigger(wrapper).text()).toContain('2')
    await setChecked('站内搜索', false)
    expect(lastSelection(wrapper)).toEqual([webId])
  })

  it.each([
    ['EXTERNAL-CONTENT', ['网页搜索']],
    ['other-plugin', ['网页搜索', '待配置工具']],
    ['站内', ['站内搜索', '已禁用工具']],
  ])('searches tool metadata with %s', async (keyword, labels) => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    await searchFor(keyword)
    expect(checkboxes().map((input) => input.getAttribute('aria-label'))).toEqual(
      labels.map((label) => `选择 ${label}`),
    )
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('selects all available results while preserving unavailable choices and deduplicating ids', async () => {
    const wrapper = mountSelector({ modelValue: ['removed/tool', disabledId, webId, webId] })
    await openSelector(wrapper)
    await clickAction('全选')
    expect(new Set(lastSelection(wrapper))).toEqual(
      new Set(['removed/tool', disabledId, webId, siteId]),
    )
    expect(lastSelection(wrapper)).toHaveLength(4)
    expect(lastSelection(wrapper)).not.toContain(unconfiguredId)
  })

  it('selects only searched results within source/category filters and retains older hidden selections', async () => {
    const wrapper = mountSelector({
      modelValue: [siteId, 'removed/tool'],
      pluginName: 'other-plugin',
      category: 'web',
    })
    await openSelector(wrapper)
    expect(checkboxes()).toHaveLength(2)
    expect(checkboxes().some((input) => input.getAttribute('aria-label') === '选择 站内搜索')).toBe(
      false,
    )
    await searchFor('EXTERNAL-CONTENT')
    await clickAction('全选当前结果')
    expect(new Set(lastSelection(wrapper))).toEqual(new Set([siteId, 'removed/tool', webId]))
    expect(lastSelection(wrapper)).not.toContain(unconfiguredId)
  })

  it('clears the complete selection including hidden and orphaned ids', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, webId, 'removed/tool'] })
    await openSelector(wrapper)
    await searchFor('EXTERNAL-CONTENT')
    expect(checkboxes()).toHaveLength(1)
    await clickAction('全部取消')
    expect(lastSelection(wrapper)).toEqual([])
    expect(document.querySelector('ul[aria-label="不可用的已选工具"]')).toBeNull()
  })

  it('clears selected tools from the collapsed control without opening the popover', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, 'removed/tool'] })
    await wrapper.get('button[aria-label="全部取消"]').trigger('click')
    expect(lastSelection(wrapper)).toEqual([])
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.querySelector('[role="dialog"]')).toBeNull()
  })

  it('shows disabled/unconfigured tools and blocks new selections even through synthetic DOM events', async () => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    expect(checkbox('已禁用工具').disabled).toBe(true)
    expect(checkbox('待配置工具').disabled).toBe(true)
    checkbox('已禁用工具').dispatchEvent(new Event('change', { bubbles: true }))
    checkbox('待配置工具').dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(popup().textContent).toContain('已禁用')
    expect(popup().textContent).toContain('未配置')
  })

  it('retains tools removed from the catalog through refresh until explicitly removed', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, webId] })
    await openSelector(wrapper)
    data.value = data.value.filter((tool) => tool.id !== webId)
    await nextTick()
    expect(document.querySelector('ul[aria-label="不可用的已选工具"]')?.textContent).toContain(
      webId,
    )
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await clickAriaButton('刷新可选工具')
    expect(refetch).toHaveBeenCalledOnce()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await clickAriaButton(`移除 ${webId}`)
    expect(lastSelection(wrapper)).toEqual([siteId])
  })

  it('keeps unavailable selections visible when search hides their rows and allows explicit removal', async () => {
    const wrapper = mountSelector({ modelValue: [disabledId, unconfiguredId, 'removed/tool'] })
    await openSelector(wrapper)
    await searchFor('EXTERNAL-CONTENT')
    const unavailable = document.querySelector('ul[aria-label="不可用的已选工具"]')
    expect(unavailable?.textContent).toContain('已禁用工具')
    expect(unavailable?.textContent).toContain('待配置工具')
    expect(unavailable?.textContent).toContain('removed/tool')
    await clickAriaButton('移除 已禁用工具')
    expect(lastSelection(wrapper)).toEqual([unconfiguredId, 'removed/tool'])
  })

  it('does not mark stored ids unavailable or change values before the first load completes', async () => {
    isLoading.value = true
    data.value = []
    const wrapper = mountSelector({ modelValue: [siteId] })
    await openSelector(wrapper)
    expect(document.querySelector('ul[aria-label="不可用的已选工具"]')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    data.value = catalog()
    isLoading.value = false
    await nextTick()
    expect(checkbox('站内搜索').checked).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('offers retry on API failure without marking selections unavailable or clearing them', async () => {
    isError.value = true
    data.value = []
    const wrapper = mountSelector({ modelValue: ['removed/tool'] })
    await openSelector(wrapper)
    expect(popup().querySelector('[role="alert"]')?.textContent).toContain('无法加载工具')
    expect(document.querySelector('ul[aria-label="不可用的已选工具"]')).toBeNull()
    await clickAction('重试')
    expect(refetch).toHaveBeenCalledOnce()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('disables refresh while a catalog request is running', async () => {
    isFetching.value = true
    const wrapper = mountSelector()
    await openSelector(wrapper)
    const refresh = ariaButton('刷新可选工具')
    expect(refresh.disabled).toBe(true)
    refresh.click()
    expect(refetch).not.toHaveBeenCalled()
  })

  it('blocks adding/selecting all during refresh but still allows explicit removal and clearing', async () => {
    isFetching.value = true
    const wrapper = mountSelector({ modelValue: [siteId, 'removed/tool'] })
    await openSelector(wrapper)
    expect(checkbox('网页搜索').disabled).toBe(true)
    await clickAction('全选')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await clickAriaButton('移除 removed/tool')
    expect(lastSelection(wrapper)).toEqual([siteId])
    await clickAction('全部取消')
    expect(lastSelection(wrapper)).toEqual([])
  })

  it('does not open or change values when initially disabled', async () => {
    const wrapper = mountSelector({ disabled: true, modelValue: [siteId] })
    expect(trigger(wrapper).attributes('disabled')).toBeDefined()
    await trigger(wrapper).trigger('click')
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('closes the popover when disabled and preserves the selection', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, 'removed/tool'] })
    await openSelector(wrapper)
    await wrapper.setProps({ disabled: true })
    await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).toBeNull())
    expect(trigger(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('moves from search to the first/last available native checkbox with ArrowDown/ArrowUp', async () => {
    data.value = [data.value[2]!, data.value[0]!, data.value[3]!, data.value[1]!]
    const wrapper = mountSelector()
    await openSelector(wrapper)
    getSearchInput().focus()
    await pressSearchKey('ArrowDown')
    expect(document.activeElement).toBe(checkbox('站内搜索'))
    getSearchInput().focus()
    await pressSearchKey('ArrowUp')
    expect(document.activeElement).toBe(checkbox('网页搜索'))
  })

  it('closes with Escape and returns keyboard focus to the trigger', async () => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    getSearchInput().focus()
    await pressSearchKey('Escape')
    await vi.waitFor(() => {
      expect(document.querySelector('[role="dialog"]')).toBeNull()
      expect(document.activeElement).toBe(trigger(wrapper).element)
    })
  })

  it('expands details below the tool without replacing search, selection or the scrollable list', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, webId] })
    await openSelector(wrapper)
    await searchFor('站内')
    const content = popup()
    const list = toolList()
    const search = getSearchInput()
    const selectedInput = checkbox('站内搜索')
    const detailsButton = ariaButton('查看 站内搜索 详情')
    detailsButton.focus()
    list.scrollTop = 96

    await clickAriaButton('查看 站内搜索 详情')
    expect(popup()).toBe(content)
    expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(1)
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('true')
    expect(inlineDetails('站内搜索')?.textContent).toContain('站内搜索')
    expect(ariaButton('收起 站内搜索 详情')).toBe(detailsButton)
    expect(detailsButton.getAttribute('aria-expanded')).toBe('true')
    expect(getSearchInput()).toBe(search)
    expect(search.value).toBe('站内')
    expect(checkbox('站内搜索')).toBe(selectedInput)
    expect(selectedInput.checked).toBe(true)
    expect(checkboxes()).toHaveLength(2)
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(popup().textContent).not.toContain('返回工具列表')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await clickAriaButton('收起 站内搜索 详情')
    await vi.waitFor(() => expect(inlineDetails('站内搜索')).toBeNull())
    expect(ariaButton('查看 站内搜索 详情')).toBe(detailsButton)
    expect(document.activeElement).toBe(detailsButton)
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(wrapper.props('modelValue')).toEqual([siteId, webId])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('expands multiple tools independently while allowing selection of another tool', async () => {
    const wrapper = mountSelector()
    await openSelector(wrapper)
    await clickAriaButton('查看 站内搜索 详情')
    await clickAriaButton('查看 网页搜索 详情')
    expect(inlineDetails('站内搜索')?.textContent).toContain('站内搜索')
    expect(inlineDetails('网页搜索')?.textContent).toContain('网页搜索')
    expect(popup().querySelectorAll('[aria-label="工具详情内容"]')).toHaveLength(2)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await setChecked('网页搜索', true)
    expect(lastSelection(wrapper)).toEqual([webId])
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
    expect(inlineDetails('站内搜索')).not.toBeNull()
    expect(inlineDetails('网页搜索')).not.toBeNull()

    await clickAriaButton('收起 站内搜索 详情')
    await vi.waitFor(() => expect(inlineDetails('站内搜索')).toBeNull())
    expect(inlineDetails('网页搜索')).not.toBeNull()
    expect(checkbox('网页搜索').checked).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
  })

  it('closes expanded details with one Escape and reopens the list without leftover expansions', async () => {
    const wrapper = mountSelector({ modelValue: [siteId, webId] })
    await openSelector(wrapper)
    await searchFor('站内')
    await clickAriaButton('查看 站内搜索 详情')
    const detailsButton = ariaButton('收起 站内搜索 详情')
    detailsButton.focus()
    detailsButton.dispatchEvent(
      new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Escape' }),
    )
    await vi.waitFor(() => {
      expect(document.querySelector('[role="dialog"]')).toBeNull()
      expect(document.activeElement).toBe(trigger(wrapper).element)
    })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await openSelector(wrapper)
    expect(getSearchInput().value).toBe('')
    expect(checkboxes()).toHaveLength(4)
    expect(popup().querySelector('[aria-label="工具详情内容"]')).toBeNull()
    expect(ariaButton('查看 站内搜索 详情').getAttribute('aria-expanded')).toBe('false')
    expect(checkbox('站内搜索').checked).toBe(true)
    expect(checkbox('网页搜索').checked).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('resets an expanded tool after search removes its row and then shows it again', async () => {
    const wrapper = mountSelector({ modelValue: [siteId] })
    await openSelector(wrapper)
    await clickAriaButton('查看 站内搜索 详情')
    expect(inlineDetails('站内搜索')).not.toBeNull()

    await searchFor('EXTERNAL-CONTENT')
    expect(popup().querySelector('input[aria-label="选择 站内搜索"]')).toBeNull()
    expect(popup().querySelector('[aria-label="工具详情内容"]')).toBeNull()
    expect(wrapper.props('modelValue')).toEqual([siteId])

    await searchFor('')
    expect(inlineDetails('站内搜索')).toBeNull()
    expect(ariaButton('查看 站内搜索 详情').getAttribute('aria-expanded')).toBe('false')
    expect(checkbox('站内搜索').checked).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('retains expanded details and list position through a background catalog refresh', async () => {
    const wrapper = mountSelector({ modelValue: [siteId] })
    await openSelector(wrapper)
    await searchFor('站内')
    await clickAriaButton('查看 站内搜索 详情')
    const list = toolList()
    const search = getSearchInput()
    const details = inlineDetails('站内搜索')
    list.scrollTop = 96

    isFetching.value = true
    await nextTick()
    expect(ariaButton('收起 站内搜索 详情').disabled).toBe(true)
    expect(inlineDetails('站内搜索')).toBe(details)
    data.value = catalog().map((tool) => ({ ...tool, displayDescription: '更新后的说明' }))
    await nextTick()
    isFetching.value = false
    await nextTick()

    expect(inlineDetails('站内搜索')).toBe(details)
    expect(ariaButton('收起 站内搜索 详情').getAttribute('aria-expanded')).toBe('true')
    expect(getSearchInput()).toBe(search)
    expect(search.value).toBe('站内')
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(checkbox('站内搜索').checked).toBe(true)
    expect(wrapper.props('modelValue')).toEqual([siteId])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})

function mountSelector(props: Record<string, unknown> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: AI_FOUNDATION_ROUTE_NAMES.TOOLS, component: { template: '<div />' } },
    ],
  })
  const wrapper = mount(AiToolSelector, {
    attachTo: document.body,
    global: {
      plugins: [router],
      stubs: {
        ToolDetailsContent: {
          props: ['tool'],
          template: '<article aria-label="工具详情内容"><h3>{{ tool.displayName }}</h3></article>',
        },
      },
      components: {
        FormKit: {
          name: 'FormKit',
          props: ['modelValue', 'id', 'type', 'disabled', 'placeholder', 'label'],
          emits: ['update:modelValue', 'input'],
          template: `<input :id="id" :type="type || 'text'" :aria-label="label" :value="modelValue" :disabled="disabled" :placeholder="placeholder"
        @input="$emit('update:modelValue', $event.target.value); $emit('input', $event.target.value)" />`,
        },
      },
    },
    props: {
      ...props,
      'onUpdate:modelValue': (modelValue: string[]) => {
        void wrapper.setProps({ modelValue })
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

function trigger(wrapper: VueWrapper) {
  return wrapper.get<HTMLButtonElement>('button[aria-label="选择工具"]')
}
async function openSelector(wrapper: VueWrapper) {
  trigger(wrapper).element.focus()
  await trigger(wrapper).trigger('click')
  await flushPromises()
  await vi.waitFor(() => expect(document.querySelector('[role="dialog"]')).not.toBeNull())
}
function popup() {
  const content = document.querySelector<HTMLElement>('[role="dialog"]')
  if (!content) throw new Error('Expected the tool popover to be open')
  return content
}
function checkboxes() {
  return Array.from(popup().querySelectorAll<HTMLInputElement>('input[type="checkbox"]'))
}
function checkbox(label: string) {
  const input = popup().querySelector<HTMLInputElement>(`input[aria-label="选择 ${label}"]`)
  if (!input) throw new Error(`Expected a checkbox for ${label}`)
  return input
}
function inlineDetails(label: string) {
  const row = checkbox(label).closest<HTMLElement>('[data-tool-id]')
  if (!row) throw new Error(`Expected the tool row for ${label}`)
  return row.querySelector<HTMLElement>('[aria-label="工具详情内容"]')
}
function toolList() {
  const list = popup().querySelector<HTMLElement>('[aria-label="工具列表"]')
  if (!list) throw new Error('Expected the scrollable tool list')
  return list
}
async function setChecked(label: string, checked: boolean) {
  const input = checkbox(label)
  input.checked = checked
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await nextTick()
}
function getSearchInput() {
  const input = popup().querySelector<HTMLInputElement>('input[aria-label="搜索工具"]')
  if (!input) throw new Error('Expected the tool search field')
  return input
}
async function searchFor(keyword: string) {
  const input = getSearchInput()
  input.value = keyword
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}
function ariaButton(label: string) {
  const button = popup().querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)
  if (!button) throw new Error(`Expected a button named ${label}`)
  return button
}
async function clickAriaButton(label: string) {
  ariaButton(label).click()
  await flushPromises()
}
function actionButton(text: string) {
  const button = Array.from(popup().querySelectorAll<HTMLButtonElement>('button')).find(
    (button) => button.textContent?.trim() === text,
  )
  if (!button) throw new Error(`Expected the ${text} action`)
  return button
}
async function clickAction(text: string) {
  actionButton(text).click()
  await flushPromises()
}
async function pressSearchKey(key: string) {
  getSearchInput().dispatchEvent(
    new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key }),
  )
  await flushPromises()
}
function lastSelection(wrapper: VueWrapper): string[] {
  const last = wrapper.emitted('update:modelValue')?.slice(-1)[0]?.[0]
  if (!Array.isArray(last)) throw new Error('Expected a selected-tool id array')
  return last as string[]
}
function catalog(): CatalogTool[] {
  return [
    {
      id: siteId,
      pluginName: 'ai-foundation',
      localName: 'search_site',
      name: 'search_site',
      displayName: '站内搜索',
      category: 'site',
      tags: ['公开内容'],
      enabled: true,
      configured: true,
      status: 'ENABLED',
      inputSchema: {},
    },
    {
      id: webId,
      pluginName: 'other-plugin',
      localName: 'search',
      name: 'other_search',
      displayName: '网页搜索',
      category: 'web',
      tags: ['EXTERNAL-CONTENT'],
      enabled: true,
      configured: true,
      status: 'ENABLED',
      inputSchema: {},
    },
    {
      id: disabledId,
      pluginName: 'ai-foundation',
      localName: 'disabled',
      name: 'disabled',
      displayName: '已禁用工具',
      category: 'site',
      enabled: false,
      configured: true,
      status: 'DISABLED',
      inputSchema: {},
    },
    {
      id: unconfiguredId,
      pluginName: 'other-plugin',
      localName: 'unconfigured',
      name: 'unconfigured',
      displayName: '待配置工具',
      category: 'web',
      enabled: true,
      configured: false,
      status: 'UNCONFIGURED',
      inputSchema: {},
    },
  ]
}
