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
  it('labels mixed usage as a known subtotal only in token mode', async () => {
    const wrapper = mountRank([
      { ...item('mixed', 15, 3), missingUsageCalls: 1, partialUsageCalls: 1 },
      { ...item('complete', 10, 1), missingUsageCalls: 0, partialUsageCalls: 0 },
    ])
    const rows = wrapper.findAll('.usage-rank')
    expect(rows[0]!.get('.usage-rank-value').text()).toBe('已知 15')
    expect(rows[0]!.text()).toContain('缺失用量 1 次 · 部分上报 1 次')
    expect(rows[1]!.get('.usage-rank-value').text()).toBe('10')
    expect(wrapper.text()).toContain('排序和占比仅基于已知 Token')
    await wrapper.setProps({ metric: 'calls' })
    expect(wrapper.findAll('.usage-rank-value')[0]!.text()).toBe('3')
    expect(wrapper.text()).not.toContain('缺失用量')
    expect(wrapper.text()).not.toContain('排序和占比仅基于已知 Token')
    wrapper.unmount()
  })

  it('aggregates missing and partial usage for Other without replacing known zero', () => {
    const wrapper = mountRank([
      ...Array.from({ length: 7 }, (_, index) => item(`known-${index}`, 100)),
      { ...item('zero', 0, 1), partialUsageCalls: 1 },
      { value: 'missing', callCount: 2, missingUsageCalls: 2 },
    ])
    const other = wrapper.findAll('.usage-rank').at(-1)!
    expect(other.get('.usage-rank-value').text()).toBe('已知 0')
    expect(other.text()).toContain('缺失用量 2 次 · 部分上报 1 次')
    wrapper.unmount()
  })

  it('keeps an all-unknown Other total and share unknown without a value bar', () => {
    const wrapper = mountRank([
      ...Array.from({ length: 7 }, (_, index) => item(`known-${index}`, 100)),
      { value: 'unknown-a', callCount: 2 },
      { value: 'unknown-b', callCount: 3 },
    ])
    const other = wrapper.findAll('.usage-rank').at(-1)!
    expect(other.get('.usage-rank-value').text()).toBe('未知')
    expect(other.get('.usage-rank-sub').text()).toContain('5 次调用 · 未知')
    expect(other.find('.usage-rank-bar').exists()).toBe(false)
  })

  it('preserves a known zero in Other when another member is unknown', () => {
    const wrapper = mountRank([
      ...Array.from({ length: 7 }, (_, index) => item(`known-${index}`, 100)),
      item('zero', 0),
      { value: 'unknown', callCount: 3 },
    ])
    expect(wrapper.findAll('.usage-rank-value').at(-1)?.text()).toBe('0')
  })

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

  it('shows an unknown caller as a static row while known callers remain selectable', async () => {
    const wrapper = mountRank([item('plugin-a', 300), item('', 100)], {
      dimension: 'callerPlugin',
    })
    const [known, unknown] = wrapper.findAll('.usage-rank')
    expect(unknown!.text()).toContain('未知')
    expect(unknown!.attributes('disabled')).toBeDefined()
    const unknownButton = unknown!.element as HTMLButtonElement
    unknownButton.click()
    expect(wrapper.emitted('select')).toBeUndefined()
    await known!.trigger('click')
    expect(wrapper.emitted('select')).toEqual([['plugin-a']])
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
