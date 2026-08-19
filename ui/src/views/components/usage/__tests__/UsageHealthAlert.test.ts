import type { UsageHealth } from '@/api/generated'
import { describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import UsageHealthAlert from '../UsageHealthAlert.vue'

rstest.mock('@halo-dev/components', () => ({
  VAlert: defineComponent({
    props: ['title'],
    template: '<div role="alert"><strong>{{ title }}</strong><slot name="description" /></div>',
  }),
}))

function mountAlert(health?: UsageHealth, error = false) {
  return mount(UsageHealthAlert, {
    props: { health, error },
  })
}

describe('UsageHealthAlert', () => {
  it('renders nothing while healthy', () => {
    expect(mountAlert({ available: true, complete: true }).find('[role="alert"]').exists()).toBe(
      false,
    )
    expect(mountAlert(undefined).find('[role="alert"]').exists()).toBe(false)
  })

  it('shows persistent unavailable warning with migration and integrity errors', () => {
    const wrapper = mountAlert({
      available: false,
      complete: false,
      migrationError: 'schema migration failed',
      integrityError: 'integrity check failed',
    })
    const text = wrapper.text()
    expect(text).toContain('统计存储不可用')
    expect(text).toContain('模型调用不受影响')
    expect(text).toContain('schema migration failed')
    expect(text).toContain('integrity check failed')
  })

  it('shows data-loss details while incomplete', () => {
    const wrapper = mountAlert({
      available: true,
      complete: false,
      droppedEvents: 12,
      incompleteCalls: 3,
      writeFailures: 2,
      queueDepth: 40,
      affectedSince: '2026-08-10T00:00:00Z',
      affectedUntil: '2026-08-10T00:30:00Z',
      lastWriteErrorAt: '2026-08-10T12:00:00Z',
    })
    const text = wrapper.text()
    expect(text).toContain('统计数据可能不完整')
    expect(text).toContain('丢弃事件 12 条')
    expect(text).toContain('不完整调用 3 条')
    expect(text).toContain('写入失败 2 次')
    expect(text).toContain('当前队列积压 40 条')
    expect(text).toContain('影响起始时间')
    expect(text).toContain('影响结束时间')
    expect(text).toContain('最近写入错误')
  })

  it('warns instead of staying silent when the health check itself fails', () => {
    const wrapper = mountAlert(undefined, true)
    const text = wrapper.text()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(text).toContain('健康状态检查失败')
    expect(text).toContain('无法获取统计健康状态')
  })
})
