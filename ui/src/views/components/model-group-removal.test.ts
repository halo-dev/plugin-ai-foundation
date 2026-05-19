import { afterEach, describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { ref } from 'vue'

const formKitStub = {
  props: ['label', 'name'],
  template: `
    <div>
      <label v-if="label">{{ label }}</label>
      <input v-if="name" :name="name" />
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
  })

  it('keeps provider model list source free of group sections', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/views/components/ProviderModelList.vue'),
      'utf8',
    )

    expect(source).not.toContain('groupedModels')
    expect(source).not.toContain('<details')
    expect(source).not.toContain('未分组')
  })
})
