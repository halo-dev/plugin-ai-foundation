import type { UsageBreakdownItem } from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UsageRankList from '../UsageRankList.vue'

function item(value: string, tokens: number, calls = 10, failed = 0): UsageBreakdownItem {
  return {
    value,
    accountedTotalTokens: tokens,
    callCount: calls,
    failedCount: failed,
  }
}

function mountRank(items: UsageBreakdownItem[], props: Record<string, unknown> = {}) {
  return mount(UsageRankList, {
    props: { items, dimension: 'model', metric: 'tokens', ...props },
  })
}

describe('UsageRankList', () => {
  it('sorts entries by the active metric and renders bars', () => {
    const wrapper = mountRank([item('b', 100), item('a', 300, 5), item('c', 200)])
    const names = wrapper.findAll('.usage-rank-name').map((name) => name.text())
    expect(names).toEqual(['a', 'c', 'b'])
    const values = wrapper.findAll('.usage-rank-value').map((value) => value.text())
    expect(values).toEqual(['300', '200', '100'])
    expect(wrapper.findAll('.usage-rank-bar i')[0]?.attributes('style')).toContain('width: 100%')
  })

  it('merges entries beyond the top N into a static Other row', () => {
    const items = Array.from({ length: 9 }, (_, index) =>
      item(`m${index}`, (9 - index) * 100, 9 - index),
    )
    const wrapper = mountRank(items)
    const rows = wrapper.findAll('.usage-rank')
    expect(rows).toHaveLength(8)
    const other = rows.at(-1)!
    expect(other.text()).toContain('其他 (2)')
    expect(other.attributes('disabled')).toBeDefined()
    // The Other row aggregates the remaining two entries
    expect(other.find('.usage-rank-value').text()).toBe('300')
  })

  it('emits select on click and highlight on hover', async () => {
    const wrapper = mountRank([item('a', 300), item('b', 100)])
    const first = wrapper.findAll('.usage-rank')[0]!
    await first.trigger('click')
    expect(wrapper.emitted('select')).toEqual([['a']])
    await first.trigger('mouseenter')
    expect(wrapper.emitted('highlight')).toContainEqual(['a'])
    await first.trigger('mouseleave')
    expect(wrapper.emitted('highlight')).toContainEqual([null])
  })

  it('dims non-highlighted rows and renders unknown keys as 未知', async () => {
    const wrapper = mountRank([item('a', 300), item('', 100)], { highlightKey: 'a' })
    const rows = wrapper.findAll('.usage-rank')
    expect(rows[1]!.classes()).toContain('is-dimmed')
    expect(rows[1]!.text()).toContain('未知')
  })

  it('shows an empty hint without items', () => {
    const wrapper = mountRank([])
    expect(wrapper.text()).toContain('暂无数据')
  })
})
