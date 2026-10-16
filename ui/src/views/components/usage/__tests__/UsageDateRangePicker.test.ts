import { parseDate } from '@internationalized/date'
import { flushPromises, mount } from '@vue/test-utils'
import { RangeCalendarRoot } from 'reka-ui'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UsageDateRangePicker from '../UsageDateRangePicker.vue'

function mountPicker(props: { fromDate?: string; toDate?: string } = {}) {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  // Use a real teleport to body (the teleport stub does not render lazily mounted content)
  return mount(UsageDateRangePicker, { props, attachTo: document.body })
}

async function openCalendar(wrapper: ReturnType<typeof mountPicker>) {
  await wrapper.find('button[aria-label="自定义起止日期"]').trigger('click')
  await flushPromises()
  return wrapper.findComponent(RangeCalendarRoot)
}

describe('UsageDateRangePicker', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('shows the selected range on the trigger', () => {
    const wrapper = mountPicker({ fromDate: '2026-08-01', toDate: '2026-08-10' })
    expect(wrapper.find('button[aria-label="自定义起止日期"]').text()).toContain(
      '2026-08-01 至 2026-08-10',
    )
  })

  it('shows placeholder text when the range is incomplete', () => {
    const wrapper = mountPicker({ fromDate: '2026-08-01' })
    expect(wrapper.text()).toContain('选择起止日期')
  })

  it('opens the calendar without re-emitting the synced range', async () => {
    const wrapper = mountPicker({ fromDate: '2026-08-01', toDate: '2026-08-10' })
    const calendar = await openCalendar(wrapper)
    expect(calendar.exists()).toBe(true)
    expect(wrapper.emitted('update')).toBeUndefined()
  })

  it('does not emit until both dates are picked', async () => {
    const wrapper = mountPicker({ fromDate: '2026-08-01', toDate: '2026-08-10' })
    const calendar = await openCalendar(wrapper)
    calendar.vm.$emit('update:modelValue', { start: parseDate('2026-08-02'), end: undefined })
    await flushPromises()
    expect(wrapper.emitted('update')).toBeUndefined()
  })

  it('emits the completed range', async () => {
    const wrapper = mountPicker({ fromDate: '2026-08-01', toDate: '2026-08-10' })
    const calendar = await openCalendar(wrapper)
    calendar.vm.$emit('update:modelValue', {
      start: parseDate('2026-08-02'),
      end: parseDate('2026-08-15'),
    })
    await flushPromises()
    expect(wrapper.emitted('update')).toEqual([[{ fromDate: '2026-08-02', toDate: '2026-08-15' }]])
  })
})
