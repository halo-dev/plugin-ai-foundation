import type { UsageExecutionRecord } from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import UsageCallExecutions from '../UsageCallExecutions.vue'

vi.mock('@halo-dev/components', () => ({
  VLoading: defineComponent({
    template: '<div data-test="loading">loading</div>',
  }),
  VButton: defineComponent({ template: '<button><slot /></button>' }),
  VTag: defineComponent({ template: '<span><slot /></span>' }),
}))

function execution(partial: Partial<UsageExecutionRecord> = {}): UsageExecutionRecord {
  return {
    id: 'exec-1',
    callId: 'call-1',
    unitKind: 'EMBEDDING_BATCH',
    unitIndex: 1,
    attemptIndex: 0,
    status: 'SUCCEEDED',
    startedAt: '2026-08-10T08:00:00Z',
    completedAt: '2026-08-10T08:00:02Z',
    usage: {
      inputTokens: 100,
      outputTokens: 0,
      accountedTotalTokens: 100,
      quality: 'PARTIAL',
    },
    ...partial,
  }
}

describe('UsageCallExecutions', () => {
  it('shows loading state', () => {
    const wrapper = mount(UsageCallExecutions, { props: { loading: true } })
    expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
  })

  it('shows error state and emits retry', async () => {
    const wrapper = mount(UsageCallExecutions, { props: { error: true } })
    expect(wrapper.text()).toContain('执行详情加载失败')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('shows empty state with retention hint', () => {
    const wrapper = mount(UsageCallExecutions, { props: { executions: [] } })
    expect(wrapper.text()).toContain('暂无执行记录')
    expect(wrapper.text()).toContain('30 天')
  })

  it('renders execution rows with labels, quality and error', () => {
    const wrapper = mount(UsageCallExecutions, {
      props: {
        executions: [
          execution(),
          execution({
            id: 'exec-2',
            status: 'FAILED',
            usage: undefined,
            error: { type: 'PROVIDER_ERROR' },
          }),
        ],
      },
    })
    expect(wrapper.text()).toContain('嵌入批次')
    expect(wrapper.text()).toContain('#2')
    expect(wrapper.text()).toContain('成功')
    expect(wrapper.text()).toContain('失败')
    expect(wrapper.text()).toContain('部分用量')
    expect(wrapper.text()).toContain('未知')
    expect(wrapper.text()).toContain('PROVIDER_ERROR')
  })

  it('distinguishes a first request from a retry and labels token dimensions', () => {
    const wrapper = mount(UsageCallExecutions, {
      props: { executions: [execution(), execution({ id: 'retry', attemptIndex: 1 })] },
    })
    expect(wrapper.text()).toContain('首次请求')
    expect(wrapper.text()).toContain('第 1 次重试')
    expect(wrapper.text()).toContain('输入 Token')
    expect(wrapper.text()).toContain('输出 Token')
    expect(wrapper.text()).toContain('合计 Token')
  })
})
