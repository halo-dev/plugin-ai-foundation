import type { UsageHealth } from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import UsageHealthAlert from '../UsageHealthAlert.vue'

vi.mock('@halo-dev/components', () => ({
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

  it('shows persistent unavailable warning with initialization and integrity errors', () => {
    const wrapper = mountAlert({
      available: false,
      complete: false,
      initializationError: 'schema initialization failed',
      integrityError: 'integrity check failed',
    })
    const text = wrapper.text()
    expect(text).toContain('统计存储不可用')
    expect(text).toContain('模型调用不受影响')
    expect(text).toContain('用量统计已停止记录')
    expect(text).toContain('不会自动恢复或清空数据库')
    expect(text).toContain('plugins/ai-foundation/ai-foundation.sqlite')
    expect(text).toContain('操作文件前请停止 Halo')
    expect(text).toContain('不能修复存储故障')
    expect(text).toContain('schema initialization failed')
    expect(text).toContain('integrity check failed')
  })

  it('warns instead of staying silent when the health check itself fails', () => {
    const wrapper = mountAlert(undefined, true)
    const text = wrapper.text()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(text).toContain('统计状态检查失败')
    expect(text).toContain('无法获取统计健康状态')
  })
})
