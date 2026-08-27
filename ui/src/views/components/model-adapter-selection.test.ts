import { describe, expect, it, rstest } from '@rstest/core'
import { flushPromises, mount } from '@vue/test-utils'
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

describe('model adapter selection', () => {
  it('shows multiple interfaces and submits an auto-selected sole interface', async () => {
    rstest.doMock('@/composables/use-provider-types-fetch', () => ({
      useProviderTypesFetch: () => ({
        data: ref([
          {
            providerType: 'openai',
            displayName: 'OpenAI',
            supportedModelTypes: ['language', 'embedding'],
            supportedFeatures: [],
            supportedAdapterTypes: ['openai-responses', 'openai-chat', 'openai-embedding'],
            adapters: [
              {
                adapterType: 'openai-responses',
                modelType: 'language',
                displayName: 'OpenAI · Responses API',
                description: '统一的 Responses 协议。',
                recommended: true,
              },
              {
                adapterType: 'openai-chat',
                modelType: 'language',
                displayName: 'OpenAI · Chat Completions',
                description: 'Chat Completions 协议。',
                recommended: false,
              },
              {
                adapterType: 'openai-embedding',
                modelType: 'embedding',
                displayName: 'OpenAI · 文本嵌入',
                description: '原生嵌入接口。',
                recommended: true,
              },
            ],
          },
        ]),
      }),
    }))

    const { default: ModelForm } = await import('./ModelForm.vue')
    const wrapper = mount(ModelForm, {
      props: { providerType: 'openai' },
      global: { stubs: { FormKit: formKitStub } },
    })
    await flushPromises()

    const adapter = wrapper.find<HTMLSelectElement>('select[name="adapterType"]')
    expect(wrapper.text()).toContain('调用接口')
    expect(adapter.element.value).toBe('openai-responses')
    expect(adapter.text()).toContain('OpenAI · Responses API（推荐）')
    expect(adapter.text()).toContain('OpenAI · Chat Completions')
    expect(wrapper.text()).toContain('统一的 Responses 协议。')

    await wrapper.find<HTMLSelectElement>('select[name="modelType"]').setValue('embedding')
    await flushPromises()

    expect(wrapper.find('select[name="adapterType"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('调用接口')

    wrapper.findComponent({ name: 'FormKit' }).vm.$emit('submit', {
      modelId: 'text-embedding-model',
      displayName: 'Embedding model',
      enabled: true,
      modelType: 'embedding',
      features: [],
    })
    await flushPromises()

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      modelId: 'text-embedding-model',
      modelType: 'embedding',
      adapterType: 'openai-embedding',
    })
  })
})
