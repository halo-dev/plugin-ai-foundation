import type {
  DefaultParameterMappingInfo,
  ModelParameterDefinitionInfo,
  ModelParameterMappings,
  ParameterMappingTemplateInfo,
} from '@/api/generated'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { describe, expect, it } from '@rstest/core'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref } from 'vue'
import ParameterMappingFields from './ParameterMappingFields.vue'

const temperatureTemplate: ParameterMappingTemplateInfo = {
  id: 'chat.temperature',
  displayName: 'temperature',
  defaultField: 'temperature',
  parameter: 'TEMPERATURE',
  modelType: 'language',
  adapterTypes: ['openai-chat'],
  configurationType: 'NONE',
}

const reasoningTemplate: ParameterMappingTemplateInfo = {
  id: 'reasoning.effort',
  displayName: 'OpenAI 请求体',
  defaultField: 'reasoning_effort',
  parameter: 'REASONING',
  modelType: 'language',
  adapterTypes: ['openai-chat'],
  configurationType: 'REASONING_MAPPING',
  defaultReasoningMapping: {
    low: { field: 'reasoning_effort', valueType: 'STRING', value: 'low' },
    medium: { field: 'reasoning_effort', valueType: 'STRING', value: 'medium' },
    high: { field: 'reasoning_effort', valueType: 'STRING', value: 'high' },
  },
}

const dashScopeImageCountTemplate: ParameterMappingTemplateInfo = {
  id: 'image.parameters.n',
  displayName: 'parameters.n',
  defaultField: 'parameters.n',
  parameter: 'IMAGE_COUNT',
  modelType: 'image-generation',
  adapterTypes: ['dashscope-image'],
  configurationType: 'NONE',
}

const languageDefinitions = [
  definition('MAX_OUTPUT_TOKENS', 'language', 'maxOutputTokens', '最大输出 Token', true),
  definition('TEMPERATURE', 'language', 'temperature', '随机性（Temperature）', true),
  definition('TOP_P', 'language', 'topP', 'Top P', true),
  definition('TOP_K', 'language', 'topK', 'Top K'),
  definition('MIN_P', 'language', 'minP', 'Min P'),
  definition('PRESENCE_PENALTY', 'language', 'presencePenalty', '存在惩罚'),
  definition('FREQUENCY_PENALTY', 'language', 'frequencyPenalty', '频率惩罚'),
  definition('REPETITION_PENALTY', 'language', 'repetitionPenalty', '重复惩罚'),
  definition('STOP_SEQUENCES', 'language', 'stopSequences', '停止序列'),
  definition('SEED', 'language', 'seed', '随机种子'),
  definition('LOGPROBS', 'language', 'logprobs', 'Token 概率'),
  definition('TOP_LOGPROBS', 'language', 'topLogprobs', '候选 Token 概率数'),
  definition('PARALLEL_TOOL_CALLS', 'language', 'parallelToolCalls', '并行工具调用'),
  definition('REASONING', 'language', 'reasoning', '推理模式', true),
]
const temperatureDefinition = languageDefinitions[1]
const reasoningDefinition = languageDefinitions[13]
const imageCountDefinition = definition('IMAGE_COUNT', 'imageGeneration', 'n', '图片数量', true)
const languageDefaults = Object.fromEntries(
  languageDefinitions.map(({ parameter }) => [parameter, { mode: 'UNSUPPORTED' }]),
) as Record<string, DefaultParameterMappingInfo>

describe('ParameterMappingFields', () => {
  it('uses one FormKit select and mounts a text input only for a custom field', async () => {
    const wrapper = mountHost()
    const mapping = wrapper.get('select')

    expect(wrapper.find('.card-wrapper').exists()).toBe(true)
    expect(wrapper.findAll('select')).toHaveLength(1)
    expect(wrapper.find('input[type="text"]').exists()).toBe(false)

    await mapping.setValue('chat.temperature')
    await settleFormKit()
    expect(wrapper.findAll('select')).toHaveLength(1)
    expect(wrapper.find('input[type="text"]').exists()).toBe(false)
    expect((wrapper.vm as { mappings?: ModelParameterMappings }).mappings).toMatchObject({
      language: {
        temperature: {
          mode: 'TEMPLATE',
          template: 'chat.temperature',
        },
      },
    })

    await mapping.setValue('__custom_field__')
    await settleFormKit()
    expect(wrapper.get('input[type="text"]').attributes('value')).toBe('temperature')

    await mapping.setValue('__inherit__')
    await settleFormKit()
    expect(wrapper.findAll('select')).toHaveLength(1)
    expect(wrapper.find('input[type="text"]').exists()).toBe(false)
    expect((wrapper.vm as { mappings?: ModelParameterMappings }).mappings).toBeUndefined()
  })

  it('persists a typed native field override for scalar templates', async () => {
    const wrapper = mountHost()
    await wrapper.get('select').setValue('__custom_field__')
    await settleFormKit()
    await wrapper.get('input[type="text"]').setValue('request.temperature')
    await settleFormKit()

    expect((wrapper.vm as { mappings?: ModelParameterMappings }).mappings).toMatchObject({
      language: {
        temperature: {
          mode: 'TEMPLATE',
          template: 'chat.temperature',
          field: 'request.temperature',
        },
      },
    })
  })

  it('initializes scoped custom fields relative to the template placement', async () => {
    const wrapper = mountScopedHost()

    await wrapper.get('select').setValue('__custom_field__')
    await settleFormKit()

    expect(wrapper.get('input[type="text"]').attributes('value')).toBe('n')
    expect((wrapper.vm as { mappings?: ModelParameterMappings }).mappings).toMatchObject({
      imageGeneration: {
        n: {
          mode: 'TEMPLATE',
          template: 'image.parameters.n',
          field: 'n',
        },
      },
    })
  })

  it('edits fixed reasoning intents as independent field and value mappings', async () => {
    const wrapper = mountReasoningHost()
    await wrapper.get('select').setValue('__reasoning_custom__')
    await settleFormKit()

    for (const label of ['开启', '关闭', '低', '中', '高']) {
      expect(wrapper.text()).toContain(label)
    }
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(5)

    await wrapper.get('input[name="reasoningMappingEnabled_enabled"]').setValue(true)
    await settleFormKit()
    const inlineFields = wrapper.get('.grid.md\\:grid-cols-3')
    expect(inlineFields.classes()).toContain('items-start')
    expect(inlineFields.findAll('.formkit-outer')).toHaveLength(3)
    for (const field of inlineFields.findAll('.formkit-outer')) {
      expect(field.classes()).toContain('!py-0')
    }
    await wrapper.get('input[name="reasoningMappingField_enabled"]').setValue('enable_thinking')
    await wrapper.get('input[name="reasoningMappingValue_enabled"]').setValue('enabled')
    await settleFormKit()

    expect((wrapper.vm as { mappings?: ModelParameterMappings }).mappings).toMatchObject({
      language: {
        reasoning: {
          mode: 'TEMPLATE',
          template: 'reasoning.effort',
          reasoningMapping: {
            enabled: {
              field: 'enable_thinking',
              valueType: 'STRING',
              value: 'enabled',
            },
            low: { field: 'reasoning_effort', valueType: 'STRING', value: 'low' },
          },
        },
      },
    })
  })

  it('shows common mappings first and keeps additional mappings expandable', async () => {
    const wrapper = mountVisibilityHost()

    expect(wrapper.findAll('.card-wrapper')).toHaveLength(4)
    expect(wrapper.text()).toContain('最大输出 Token')
    expect(wrapper.text()).toContain('随机性（Temperature）')
    expect(wrapper.text()).toContain('Top P')
    expect(wrapper.text()).toContain('推理模式')
    expect(wrapper.text()).not.toContain('Top K')

    await wrapper.get('button').trigger('click')
    await nextTick()
    expect(wrapper.findAll('.card-wrapper')).toHaveLength(14)
    expect(wrapper.text()).toContain('Top K')
    expect(wrapper.get('button').text()).toContain('收起更多参数')

    await wrapper.get('button').trigger('click')
    await nextTick()
    expect(wrapper.findAll('.card-wrapper')).toHaveLength(4)
  })

  it('keeps an explicitly configured additional mapping visible while collapsed', () => {
    const wrapper = mountVisibilityHost({
      language: {
        topK: { mode: 'UNSUPPORTED' },
      },
    })

    expect(wrapper.findAll('.card-wrapper')).toHaveLength(5)
    expect(wrapper.text()).toContain('Top K')
  })
})

function mountHost() {
  return mount(
    defineComponent({
      components: { ParameterMappingFields },
      setup() {
        const mappings = ref<ModelParameterMappings>()
        return { mappings, temperatureDefinition, temperatureTemplate }
      },
      template: `
        <ParameterMappingFields
          v-model="mappings"
          context="provider"
          model-type="language"
          adapter-type="openai-chat"
          :definitions="[temperatureDefinition]"
          :templates="[temperatureTemplate]"
        />
      `,
    }),
    {
      global: {
        plugins: [[FormKitPlugin, defaultConfig()]],
      },
    },
  )
}

function mountReasoningHost() {
  return mount(
    defineComponent({
      components: { ParameterMappingFields },
      setup() {
        const mappings = ref<ModelParameterMappings>()
        const defaults = {
          REASONING: { mode: 'TEMPLATE', template: 'reasoning.effort' },
        }
        return { mappings, defaults, reasoningDefinition, reasoningTemplate }
      },
      template: `
        <ParameterMappingFields
          v-model="mappings"
          context="provider"
          model-type="language"
          adapter-type="openai-chat"
          :definitions="[reasoningDefinition]"
          :templates="[reasoningTemplate]"
          :defaults="defaults"
        />
      `,
    }),
    {
      global: {
        plugins: [[FormKitPlugin, defaultConfig()]],
      },
    },
  )
}

function mountScopedHost() {
  return mount(
    defineComponent({
      components: { ParameterMappingFields },
      setup() {
        const mappings = ref<ModelParameterMappings>()
        return { dashScopeImageCountTemplate, imageCountDefinition, mappings }
      },
      template: `
        <ParameterMappingFields
          v-model="mappings"
          context="provider"
          model-type="image-generation"
          adapter-type="dashscope-image"
          :definitions="[imageCountDefinition]"
          :templates="[dashScopeImageCountTemplate]"
        />
      `,
    }),
    {
      global: {
        plugins: [[FormKitPlugin, defaultConfig()]],
      },
    },
  )
}

function mountVisibilityHost(initialMappings?: ModelParameterMappings) {
  return mount(
    defineComponent({
      components: { ParameterMappingFields },
      setup() {
        const mappings = ref<ModelParameterMappings | undefined>(initialMappings)
        return { languageDefinitions, mappings, languageDefaults }
      },
      template: `
        <ParameterMappingFields
          v-model="mappings"
          context="provider"
          model-type="language"
          :definitions="languageDefinitions"
          :defaults="languageDefaults"
        />
      `,
    }),
    {
      global: {
        plugins: [[FormKitPlugin, defaultConfig()]],
      },
    },
  )
}

async function settleFormKit() {
  await new Promise((resolve) => setTimeout(resolve, 25))
  await nextTick()
}

function definition(
  parameter: string,
  domain: 'language' | 'embedding' | 'rerank' | 'imageGeneration',
  field: string,
  displayName: string,
  common = false,
): ModelParameterDefinitionInfo {
  return {
    parameter,
    domain,
    field,
    displayName,
    description: `${displayName}说明`,
    modelType: domain === 'imageGeneration' ? 'image-generation' : domain,
    common,
  }
}
