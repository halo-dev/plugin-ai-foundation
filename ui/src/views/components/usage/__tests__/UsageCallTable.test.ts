import type { UsageCallItem } from '@/api/generated'
import { describe, expect, it, rstest } from '@rstest/core'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import UsageCallTable from '../UsageCallTable.vue'

rstest.mock('@halo-dev/components', () => ({
  VLoading: defineComponent({
    template: '<div data-test="loading">loading</div>',
  }),
  VEmpty: defineComponent({
    props: ['title', 'message'],
    template: '<div data-test="empty"><div>{{ title }}</div><div>{{ message }}</div></div>',
  }),
  VButton: defineComponent({
    props: ['loading', 'disabled'],
    emits: ['click'],
    setup(props, { emit, slots }) {
      return () =>
        h(
          'button',
          { disabled: props.disabled, onClick: () => emit('click') },
          slots.default?.(),
        )
    },
  }),
  VTag: defineComponent({
    template: '<span><slot /></span>',
  }),
}))

const UsageCallExecutionsStub = defineComponent({
  props: ['callId'],
  template: '<div data-test="executions">executions:{{ callId }}</div>',
})

function mountTable(
  props: Partial<InstanceType<typeof UsageCallTable>['$props']> = {},
) {
  return mount(UsageCallTable, {
    props: { items: [], ...props },
    global: {
      stubs: {
        UsageCallExecutions: UsageCallExecutionsStub,
      },
      directives: {
        tooltip: () => {},
      },
    },
  })
}

function call(partial: Partial<UsageCallItem> = {}): UsageCallItem {
  return {
    id: 'call-1',
    status: 'SUCCEEDED',
    startedAt: '2026-08-10T08:00:00Z',
    completedAt: '2026-08-10T08:00:01Z',
    durationMillis: 1000,
    callerPluginName: 'plugin-search',
    callerPluginVersion: '1.2.0',
    modelName: 'gpt-4o-prod',
    providerName: 'openai-main',
    providerType: 'openai',
    requestModelId: 'gpt-4o',
    responseModelId: 'gpt-4o-2026-01-01',
    modelType: 'LANGUAGE',
    operation: 'language.generateText',
    stepCount: 1,
    attemptCount: 1,
    complete: true,
    usage: {
      inputTokens: 100,
      outputTokens: 50,
      accountedTotalTokens: 150,
      quality: 'REPORTED_COMPONENTS',
    },
    ...partial,
  }
}

describe('UsageCallTable', () => {
  it('shows loading state', () => {
    const wrapper = mountTable({ loading: true })
    expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
  })

  it('shows error state and emits retry', async () => {
    const wrapper = mountTable({ error: true })
    expect(wrapper.text()).toContain('调用历史加载失败')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('keeps loaded rows visible when loading the next page fails', async () => {
    const wrapper = mountTable({ items: [call()], hasNextPage: true, loadMoreError: true })

    expect(wrapper.text()).toContain('plugin-search')
    expect(wrapper.text()).not.toContain('调用历史加载失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '加载失败，重试')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    expect(wrapper.emitted('loadMore')).toHaveLength(1)
  })

  it('shows empty state with retention hint', () => {
    const wrapper = mountTable({ items: [] })
    expect(wrapper.text()).toContain('暂无调用记录')
    expect(wrapper.text()).toContain('90 天')
  })

  it('renders all terminal statuses and explicit unknown values', () => {
    const wrapper = mountTable({
      items: [
        call({ id: 'c1', status: 'CANCELLED', usage: undefined }),
        call({ id: 'c2', status: 'TIMED_OUT' }),
        call({ id: 'c3', status: 'ABANDONED' }),
        call({ id: 'c4', status: 'IN_PROGRESS' }),
        call({
          id: 'c5',
          status: 'FAILED',
          callerPluginName: undefined,
          modelName: undefined,
          providerName: undefined,
          errorType: 'PROVIDER_ERROR',
          errorCode: 'rate_limit',
        }),
      ],
    })
    const text = wrapper.text()
    expect(text).toContain('已取消')
    expect(text).toContain('超时')
    expect(text).toContain('已废弃')
    expect(text).toContain('进行中')
    expect(text).toContain('失败')
    // 未知调用方/模型/Token 显式展示为未知
    expect(text).toContain('未知调用方')
    expect(text).toContain('未知模型')
    expect(text).toContain('PROVIDER_ERROR（rate_limit）')
    expect((text.match(/未知/g) || []).length).toBeGreaterThanOrEqual(3)
  })

  it('renders partial and missing usage qualities distinctly', () => {
    const wrapper = mountTable({
      items: [
        call({
          id: 'c1',
          usage: { accountedTotalTokens: 80, quality: 'PARTIAL' },
          missingExecutionCount: 2,
        }),
        call({ id: 'c2', usage: { quality: 'MISSING' } }),
      ],
    })
    const text = wrapper.text()
    expect(text).toContain('部分用量')
    expect(text).toContain('用量缺失')
    expect(text).toContain('2 条执行用量缺失')
  })

  it('marks streaming and incomplete calls', () => {
    const wrapper = mountTable({
      items: [call({ id: 'c1', streaming: true, complete: false })],
    })
    expect(wrapper.text()).toContain('流式')
    expect(wrapper.text()).toContain('不完整')
  })

  it('uses historical snapshots without linking to live resources', () => {
    const wrapper = mountTable({ items: [call()] })
    const text = wrapper.text()
    expect(text).toContain('plugin-search（v1.2.0）')
    expect(text).toContain('gpt-4o-prod · openai-main')
    expect(text).toContain('gpt-4o → gpt-4o-2026-01-01')
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('expands execution details on click and collapses again', async () => {
    const wrapper = mountTable({ items: [call()] })
    const row = wrapper.find('[role="button"]')
    expect(wrapper.find('[data-test="executions"]').exists()).toBe(false)

    await row.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="executions"]').text()).toContain('executions:call-1')
    expect(row.attributes('aria-expanded')).toBe('true')

    await row.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="executions"]').exists()).toBe(false)
  })

  it('emits loadMore from the pagination button', async () => {
    const wrapper = mountTable({ items: [call()], hasNextPage: true })
    const loadMore = wrapper.findAll('button').find((button) => button.text() === '加载更多')
    expect(loadMore).toBeDefined()
    await loadMore!.trigger('click')
    expect(wrapper.emitted('loadMore')).toHaveLength(1)
  })

  it('shows loaded-all hint when there is no next page', () => {
    const wrapper = mountTable({ items: [call()], hasNextPage: false })
    expect(wrapper.text()).toContain('共 1 条，已加载全部')
    expect(wrapper.text()).not.toContain('加载更多')
  })
})
