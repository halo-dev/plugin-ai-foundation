import type { UsageCallDetail, UsageExecutionRecord } from '@/api/generated'
import { useUsageCallDetail } from '@/composables/use-usage-statistics'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import UsageCallExecutions from '../UsageCallExecutions.vue'

vi.mock('@/composables/use-usage-statistics', () => ({
  useUsageCallDetail: vi.fn(),
}))

vi.mock('@halo-dev/components', () => ({
  VLoading: defineComponent({
    template: '<div data-test="loading">loading</div>',
  }),
  VButton: defineComponent({ template: '<button><slot /></button>' }),
  VTag: defineComponent({
    template: '<span><slot /></span>',
  }),
}))

const useUsageCallDetailMock = vi.mocked(useUsageCallDetail)

beforeEach(() => {
  useUsageCallDetailMock.mockClear()
})

function mockDetail(result: { data?: UsageCallDetail; isLoading?: boolean; isError?: boolean }) {
  useUsageCallDetailMock.mockReturnValue({
    data: ref(result.data),
    isLoading: ref(result.isLoading ?? false),
    isError: ref(result.isError ?? false),
  } as unknown as ReturnType<typeof useUsageCallDetail>)
}

function mountExecutions() {
  return mount(UsageCallExecutions, {
    props: { callId: 'call-1' },
  })
}

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
    mockDetail({ isLoading: true })
    const wrapper = mountExecutions()
    expect(wrapper.find('[data-test="loading"]').exists()).toBe(true)
  })

  it('shows error state', () => {
    mockDetail({ isError: true })
    const wrapper = mountExecutions()
    expect(wrapper.text()).toContain('执行详情加载失败')
  })

  it('shows empty state with retention hint', () => {
    mockDetail({ data: { executions: [] } })
    const wrapper = mountExecutions()
    expect(wrapper.text()).toContain('暂无执行记录')
    expect(wrapper.text()).toContain('30 天')
  })

  it('renders execution rows with labels, quality and error', () => {
    mockDetail({
      data: {
        executions: [
          execution(),
          execution({
            id: 'exec-2',
            status: 'FAILED',
            usage: undefined,
            error: { type: 'PROVIDER_ERROR', code: 'rate_limit' },
          }),
        ],
      },
    })
    const wrapper = mountExecutions()
    const text = wrapper.text()
    expect(text).toContain('嵌入批次')
    expect(text).toContain('#2')
    expect(text).toContain('成功')
    expect(text).toContain('失败')
    expect(text).toContain('部分用量')
    // null usage 显示「未知」而非 0
    expect(text).toContain('未知')
    expect(text).toContain('PROVIDER_ERROR（rate_limit）')
  })

  it('loads detail lazily by callId', () => {
    mockDetail({ data: { executions: [execution()] } })
    mountExecutions()
    expect(useUsageCallDetailMock).toHaveBeenCalledOnce()
  })
  it('distinguishes a first request from a retry and labels token dimensions', () => {
    mockDetail({ data: { executions: [execution(), execution({id: 'retry', attemptIndex: 1})] } })
    const wrapper = mountExecutions()
    expect(wrapper.text()).toContain('首次请求')
    expect(wrapper.text()).toContain('第 1 次重试')
    expect(wrapper.text()).toContain('输入 Token')
    expect(wrapper.text()).toContain('输出 Token')
    expect(wrapper.text()).toContain('合计 Token')
  })

})
