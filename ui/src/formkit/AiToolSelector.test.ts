import { useToolsFetch } from '@/composables/use-tools-fetch'
import { AI_FOUNDATION_ROUTE_NAMES } from '@/routes'
import type { CatalogTool } from '@/utils/tools'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiToolSelectGroup from './AiToolSelectGroup.vue'
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
    ['站点', ['站内搜索', '已禁用工具']],
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
    await searchFor('站点')
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
    expect(search.value).toBe('站点')
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
    await searchFor('站点')
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
    await searchFor('站点')
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
    expect(search.value).toBe('站点')
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(checkbox('站内搜索').checked).toBe(true)
    expect(wrapper.props('modelValue')).toEqual([siteId])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('collapses business groups within their existing source and exposes availability counts', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector()
    await openSelector(wrapper)
    const group = toolGroup()
    expect(group.textContent).toContain('可选 2')
    expect(group.textContent).toContain('不可选 2')
    expect(group.textContent).toContain('已选 0')
    expect(group.textContent).not.toContain('文章和页面工作流')
    expect(group.textContent).not.toContain('取消本组')
    expect(ariaButton('展开工具组 内容工具').getAttribute('aria-expanded')).toBe('false')
    expect(group.querySelector('[data-tool-id]')).toBeNull()
    expect(checkbox('网页搜索').closest('section')).not.toBe(group.closest('section'))
    await clickAriaButton('展开工具组 内容工具')
    expect(group.textContent).toContain('文章和页面工作流')
    expect(checkbox('站内搜索').closest('[data-tool-group]')).toBe(group)
    expect(checkbox('文章读取').disabled).toBe(false)
    expect(checkbox('已禁用工具').disabled).toBe(true)
    expect(checkbox('未配置工具').disabled).toBe(true)
    await clickAriaButton('收起工具组 内容工具')
    await vi.waitFor(() => expect(group.querySelector('[data-tool-id]')).toBeNull())
    expect(popup()).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('selects, overrides and clears group members while preserving choices in other groups', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector({ modelValue: [siteId, disabledId, webId, 'removed/tool'] })
    await openSelector(wrapper)
    expect(groupCheckbox().indeterminate).toBe(true)
    expect(groupCheckbox().getAttribute('aria-checked')).toBe('mixed')
    groupCheckbox().click()
    await nextTick()
    expect(lastSelection(wrapper)).toEqual([siteId, disabledId, webId, 'removed/tool', articleId])
    expect(groupCheckbox().checked).toBe(true)
    expect(groupCheckbox().indeterminate).toBe(false)
    await clickAriaButton('展开工具组 内容工具')
    await clickAriaButton('查看 站内搜索 详情')
    await setChecked('文章读取', false)
    expect(groupCheckbox().indeterminate).toBe(true)
    expect(inlineDetails('站内搜索')).not.toBeNull()
    groupCheckbox().click()
    await nextTick()
    expect(groupCheckbox().checked).toBe(true)
    groupCheckbox().click()
    await nextTick()
    expect(lastSelection(wrapper)).toEqual([webId, 'removed/tool'])
    expect(groupCheckbox().checked).toBe(false)
    expect(inlineDetails('站内搜索')).not.toBeNull()
    expect(popup()).toBeTruthy()
  })

  it('adds a full preset during a narrower search and reports missing or unavailable members', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector({ modelValue: [webId] })
    await openSelector(wrapper)
    await searchFor('站内搜索')
    expect(ariaButton('收起工具组 内容工具').getAttribute('aria-expanded')).toBe('true')
    expect(toolGroup().querySelector('input[aria-label="选择 文章读取"]')).toBeNull()
    expect(toolGroup().textContent).toContain('组选择仅作用于当前搜索结果')
    expect(ariaButton('切换预设 写作预设').textContent).toContain('+2')
    expect(ariaButton('切换预设 写作预设').textContent).toContain('3不可用')
    await clickAriaButton('切换预设 写作预设')
    expect(lastSelection(wrapper)).toEqual([webId, siteId, articleId])
    expect(lastSelection(wrapper)).not.toContain(disabledId)
    expect(lastSelection(wrapper)).not.toContain(groupNeedsConfigId)
    expect(lastSelection(wrapper)).not.toContain('ai-foundation/missing')
    expect(lastSelection(wrapper)).not.toContain(contentGroupId)
    expect(ariaButton('切换预设 写作预设').disabled).toBe(false)
    expect(ariaButton('切换预设 写作预设').getAttribute('aria-pressed')).toBe('true')
    expect(getSearchInput().value).toBe('站内搜索')
    expect(popup()).toBeTruthy()
    await searchFor('')
    expect(ariaButton('展开工具组 内容工具').getAttribute('aria-expanded')).toBe('false')
    await clickAriaButton('展开工具组 内容工具')
    expect(checkbox('文章读取').checked).toBe(true)
    await setChecked('文章读取', false)
    expect(lastSelection(wrapper)).toEqual([webId, siteId])
  })

  it('expands matching groups in search and limits batch changes to visible group results', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector({ modelValue: [articleId, webId] })
    await openSelector(wrapper)
    await searchFor('内容工具')
    expect(checkbox('站内搜索')).toBeTruthy()
    expect(checkbox('未配置工具').disabled).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await searchFor('站内搜索')
    groupCheckbox().click()
    await nextTick()
    expect(lastSelection(wrapper)).toEqual([articleId, webId, siteId])
    groupCheckbox().click()
    await nextTick()
    expect(lastSelection(wrapper)).toEqual([articleId, webId])
  })

  it('keeps preset additions within permanent source/category constraints while preserving older IDs', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector({
      modelValue: [articleId, webId],
      pluginName: 'ai-foundation',
      category: 'site',
    })
    await openSelector(wrapper)
    await searchFor('站内搜索')
    expect(ariaButton('切换预设 写作预设').textContent).toContain('+1')
    expect(ariaButton('切换预设 写作预设').textContent).toContain('4不可用')
    await clickAriaButton('切换预设 写作预设')
    expect(lastSelection(wrapper)).toEqual([articleId, webId, siteId])
    expect(lastSelection(wrapper)).not.toContain(disabledId)
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
    await clickAriaButton('切换预设 写作预设')
    expect(lastSelection(wrapper)).toEqual([articleId, webId])
    expect(wrapper.emitted('update:modelValue')).toHaveLength(2)
  })

  it('omits zero unavailable counts and labels a fully selected preset as selected', async () => {
    data.value = groupedCatalog()
      .filter((tool) => tool.id === siteId || tool.id === articleId)
      .map((tool) => ({
        ...tool,
        group: {
          ...tool.group!,
          presets: [{ id: 'read', displayName: '只读预设', toolIds: [siteId, articleId] }],
        },
      }))
    const wrapper = mountSelector({ modelValue: [siteId, articleId] })
    await openSelector(wrapper)
    expect(toolGroup().textContent).not.toContain('不可选 0')
    expect(toolGroup().textContent).not.toContain('不可用')
    expect(ariaButton('切换预设 只读预设').textContent).toContain('已选')
    expect(ariaButton('切换预设 只读预设').textContent).not.toContain('+0')
    expect(ariaButton('切换预设 只读预设').disabled).toBe(false)
    expect(ariaButton('切换预设 只读预设').getAttribute('aria-pressed')).toBe('true')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await clickAriaButton('切换预设 只读预设')
    expect(lastSelection(wrapper)).toEqual([])
    expect(ariaButton('切换预设 只读预设').getAttribute('aria-pressed')).toBe('false')
    expect(ariaButton('切换预设 只读预设').textContent).toContain('+2')
    await clickAriaButton('切换预设 只读预设')
    expect(lastSelection(wrapper)).toEqual([siteId, articleId])
    expect(popup()).toBeTruthy()
  })

  it('toggles overlapping read and edit presets without clearing members of the other preset', async () => {
    const createId = 'ai-foundation/create_post'
    const updateId = 'ai-foundation/update_post'
    const readIds = [siteId, articleId]
    const editIds = [...readIds, createId, updateId]
    const source = groupedCatalog()
    const article = source.find((tool) => tool.id === articleId)!
    data.value = [
      ...source,
      { ...article, id: createId, displayName: '创建草稿' },
      { ...article, id: updateId, displayName: '编辑草稿' },
    ].map((tool) => ({
      ...tool,
      group: tool.group
        ? {
            ...tool.group,
            presets: [
              { id: 'read', displayName: '只读查询', toolIds: readIds },
              { id: 'edit', displayName: '草稿编辑', toolIds: editIds },
            ],
          }
        : undefined,
    }))
    const wrapper = mountSelector({ modelValue: [webId, ...editIds, disabledId] })
    await openSelector(wrapper)
    const list = toolList()
    list.scrollTop = 96
    expect(ariaButton('切换预设 只读查询').getAttribute('aria-pressed')).toBe('true')
    expect(ariaButton('切换预设 草稿编辑').getAttribute('aria-pressed')).toBe('true')
    await clickAriaButton('切换预设 只读查询')
    expect(lastSelection(wrapper)).toEqual([webId, createId, updateId, disabledId])
    expect(ariaButton('切换预设 草稿编辑').getAttribute('aria-pressed')).toBe('false')
    expect(ariaButton('切换预设 草稿编辑').textContent).toContain('+2')
    await clickAriaButton('切换预设 草稿编辑')
    expect(lastSelection(wrapper)).toEqual([webId, createId, updateId, disabledId, ...readIds])
    expect(ariaButton('切换预设 草稿编辑').getAttribute('aria-pressed')).toBe('true')
    await clickAriaButton('切换预设 草稿编辑')
    expect(lastSelection(wrapper)).toEqual([webId, disabledId])
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(popup()).toBeTruthy()
  })

  it.each(['group', 'preset'])(
    'allows an unavailable saved selection to be cleared with its %s toggle',
    async (control) => {
      data.value = groupedCatalog().map((tool) =>
        tool.group ? { ...tool, enabled: false, status: 'DISABLED' } : tool,
      )
      const wrapper = mountSelector({ modelValue: [siteId, webId] })
      await openSelector(wrapper)
      expect(groupCheckbox().disabled).toBe(false)
      expect(groupCheckbox().indeterminate).toBe(true)
      expect(groupCheckbox().getAttribute('aria-checked')).toBe('mixed')
      expect(ariaButton('切换预设 写作预设').disabled).toBe(false)
      if (control === 'group') {
        groupCheckbox().click()
        await nextTick()
      } else {
        await clickAriaButton('切换预设 写作预设')
      }
      expect(lastSelection(wrapper)).toEqual([webId])
      expect(groupCheckbox().checked).toBe(false)
      expect(groupCheckbox().indeterminate).toBe(false)
      expect(groupCheckbox().disabled).toBe(true)
      expect(ariaButton('切换预设 写作预设').disabled).toBe(true)
      groupCheckbox().dispatchEvent(new Event('change', { bubbles: true }))
      ariaButton('切换预设 写作预设').dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await nextTick()
      expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
      expect(popup()).toBeTruthy()
    },
  )

  it('does not extend saved IDs when new members join a selected group after refresh', async () => {
    data.value = groupedCatalog()
    const wrapper = mountSelector({ modelValue: [siteId, articleId] })
    await openSelector(wrapper)
    await clickAriaButton('展开工具组 内容工具')
    await clickAriaButton('查看 站内搜索 详情')
    const details = inlineDetails('站内搜索')
    const list = toolList()
    list.scrollTop = 96
    data.value = [
      ...groupedCatalog(),
      {
        ...data.value.find((tool) => tool.id === articleId)!,
        id: 'ai-foundation/new-member',
        displayName: '新增工具',
      },
    ]
    await nextTick()
    expect(checkbox('新增工具').checked).toBe(false)
    expect(groupCheckbox().indeterminate).toBe(true)
    expect(inlineDetails('站内搜索')).toBe(details)
    expect(toolList()).toBe(list)
    expect(list.scrollTop).toBe(96)
    expect(wrapper.props('modelValue')).toEqual([siteId, articleId])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('guards group selection and preset synthetic events while the catalog is refreshing', async () => {
    data.value = groupedCatalog()
    isFetching.value = true
    const wrapper = mountSelector({ modelValue: [siteId] })
    await openSelector(wrapper)
    expect(groupCheckbox().disabled).toBe(true)
    expect(ariaButton('切换预设 写作预设').disabled).toBe(true)
    groupCheckbox().checked = true
    groupCheckbox().indeterminate = false
    groupCheckbox().dispatchEvent(new Event('change', { bubbles: true }))
    ariaButton('切换预设 写作预设').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    const group = wrapper.findComponent(AiToolSelectGroup)
    group.vm.$emit('select', [articleId])
    group.vm.$emit('preset', [articleId])
    group.vm.$emit('remove', [siteId])
    await nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(groupCheckbox().checked).toBe(false)
    expect(groupCheckbox().indeterminate).toBe(true)
    expect(popup()).toBeTruthy()
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
  await flushPromises()
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

const contentGroupId = 'ai-foundation/content-tools'
const articleId = 'ai-foundation/get_post'
const groupNeedsConfigId = 'ai-foundation/needs-config'

function groupedCatalog(): CatalogTool[] {
  const group = {
    id: contentGroupId,
    displayName: '内容工具',
    description: '文章和页面工作流',
    presets: [
      {
        id: 'writing',
        displayName: '写作预设',
        description: '读取和编辑内容',
        toolIds: [siteId, articleId, disabledId, groupNeedsConfigId, 'ai-foundation/missing'],
      },
    ],
  }
  return [
    ...catalog()
      .filter((tool) => tool.id !== unconfiguredId)
      .map((tool) => ({
        ...tool,
        group: tool.pluginName === 'ai-foundation' ? group : undefined,
      })),
    {
      id: articleId,
      pluginName: 'ai-foundation',
      displayName: '文章读取',
      category: 'content',
      enabled: true,
      configured: true,
      status: 'ENABLED',
      group,
    },
    {
      id: groupNeedsConfigId,
      pluginName: 'ai-foundation',
      displayName: '未配置工具',
      category: 'content',
      enabled: true,
      configured: false,
      status: 'UNCONFIGURED',
      group,
    },
  ]
}

function toolGroup() {
  const group = popup().querySelector<HTMLElement>(`[data-tool-group="${contentGroupId}"]`)
  if (!group) throw new Error('Expected the content tool group')
  return group
}

function groupCheckbox() {
  return toolGroup().querySelector<HTMLInputElement>('input[aria-label="选择工具组 内容工具"]')!
}
