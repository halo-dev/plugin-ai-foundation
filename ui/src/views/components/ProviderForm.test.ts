import type { ModelParameterMappings, ProviderTypeInfo } from '@/api/generated'
import { useProviderTypesFetch } from '@/composables/use-provider-types-fetch'
import { beforeEach, describe, expect, it, rstest } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import ProviderForm from './ProviderForm.vue'

rstest.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderTypesFetch: rstest.fn(),
}))

rstest.mock('@halo-dev/components', () => ({
  Toast: { error: rstest.fn() },
}))

const providerTypes = ref<ProviderTypeInfo[]>([])

const FormKitStub = defineComponent({
  name: 'FormKit',
  inheritAttrs: false,
  props: {
    modelValue: { type: [String, Number, Boolean, Object], default: undefined },
    name: { type: String, default: undefined },
    options: { type: Array, default: () => [] },
    type: { type: [String, Object], default: 'text' },
  },
  emits: ['input', 'submit', 'update:modelValue'],
  setup(props, { emit, slots }) {
    return () => {
      if (props.type === 'form') return h('form', slots.default?.())
      if (props.type === 'select') {
        return h(
          'select',
          {
            name: props.name,
            value: props.modelValue,
            onChange: (event: Event) => {
              const value = (event.target as HTMLSelectElement).value
              emit('update:modelValue', value)
              emit('input', value)
            },
          },
          (props.options as Array<{ label: string; value: string }>).map((option) =>
            h('option', { value: option.value }, option.label),
          ),
        )
      }
      return h('input', {
        name: props.name,
        value: props.modelValue as string | number | undefined,
      })
    }
  },
})

const ParameterMappingFieldsStub = defineComponent({
  name: 'ParameterMappingFields',
  props: {
    modelValue: { type: Object, default: undefined },
  },
  emits: ['update:modelValue'],
  template: '<div data-testid="parameter-mappings" />',
})

describe('ProviderForm', () => {
  beforeEach(() => {
    providerTypes.value = [providerType('openai'), providerType('dashscope')]
    rstest.mocked(useProviderTypesFetch).mockReturnValue({
      data: providerTypes,
    } as ReturnType<typeof useProviderTypesFetch>)
  })

  it('clears configured parameter mappings when the provider type changes during creation', async () => {
    const wrapper = mount(ProviderForm, {
      global: {
        components: { FormKit: FormKitStub },
        stubs: {
          AdvancedSettingsCollapsible: { template: '<div><slot /></div>' },
          ParameterMappingFields: ParameterMappingFieldsStub,
        },
      },
    })
    const providerTypeSelect = wrapper.get('select[name="providerType"]')

    await providerTypeSelect.setValue('openai')
    await nextTick()
    const mappingFields = wrapper.getComponent(ParameterMappingFieldsStub)
    const mappings: ModelParameterMappings = {
      language: { temperature: { mode: 'TEMPLATE', template: 'chat.temperature' } },
    }
    mappingFields.vm.$emit('update:modelValue', mappings)
    await nextTick()
    expect(mappingFields.props('modelValue')).toEqual(mappings)

    await providerTypeSelect.setValue('dashscope')
    await nextTick()

    expect(mappingFields.props('modelValue')).toBeUndefined()
  })
})

function providerType(providerType: string): ProviderTypeInfo {
  return {
    providerType,
    displayName: providerType,
    parameterDefinitions: [
      {
        parameter: 'TEMPERATURE',
        modelType: 'language',
        domain: 'language',
        field: 'temperature',
        displayName: '随机性（Temperature）',
        description: '控制生成结果的随机程度',
        common: true,
      },
    ],
    parameterMappingTemplates: [
      {
        id: 'chat.temperature',
        displayName: 'temperature',
        defaultField: 'temperature',
        parameter: 'TEMPERATURE',
        modelType: 'language',
        adapterTypes: ['openai-chat'],
      },
    ],
  }
}
