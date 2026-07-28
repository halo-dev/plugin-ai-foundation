import { afterEach, describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'

const formKitStub = {
  props: ['label', 'name', 'options'],
  template: `
    <div>
      <label v-if="label">{{ label }}</label>
      <input v-if="name" :name="name" />
      <span v-for="option in options" :key="option.value">{{ option.label }}</span>
      <slot />
    </div>
  `,
}

afterEach(() => {
  rstest.resetModules()
  rstest.clearAllMocks()
})

describe('model group removal', () => {
  it('does not render a model group field in the model form', async () => {
    rstest.doMock('@/composables/use-provider-types-fetch', () => ({
      useProviderTypesFetch: () => ({ data: ref([]) }),
    }))

    const { default: ModelForm } = await import('./ModelForm.vue')
    const wrapper = mount(ModelForm, {
      props: {
        providerType: 'openai',
      },
      global: {
        stubs: {
          FormKit: formKitStub,
        },
      },
    })

    expect(wrapper.text()).not.toContain('分组')
    expect(wrapper.find('input[name="group"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('支持推理历史回传')
    expect(wrapper.text()).toContain('继承供应商')
    expect(wrapper.find('input[name="languageReasoningHistory"]').exists()).toBe(true)
  })
})
