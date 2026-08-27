import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const formKitStub = {
  name: 'FormKit',
  props: ['label', 'name', 'options', 'value', 'help'],
  emits: ['input', 'submit'],
  template: `
    <div>
      <label v-if="label">{{ label }}</label>
      <select
        v-if="name && options"
        :name="name"
        :value="value"
        @change="$emit('input', $event.target.value)"
      >
        <option v-for="option in options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <input v-else-if="name" :name="name" :value="value" />
      <small v-if="help">{{ help }}</small>
      <slot />
    </div>
  `,
}

afterEach(() => {
  vi.resetModules()
  vi.clearAllMocks()
})

describe('model group removal', () => {
  it('does not render a model group field in the model form', async () => {
    vi.doMock('@/composables/use-provider-types-fetch', () => ({
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
    expect(wrapper.find('[name="languageReasoningHistory"]').exists()).toBe(true)
  })
})
