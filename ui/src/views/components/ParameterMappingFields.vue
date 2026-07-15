<script setup lang="ts">
import type {
  DefaultParameterMappingInfo,
  ModelParameterDefinitionInfo,
  ModelParameterMappings,
  ParameterMappingTemplateInfo,
  ReasoningMapping,
  ReasoningValueMapping,
  Selection,
} from '@/api/generated'
import {
  effectiveSelection,
  parameterDefinitionsForModelType,
  readSelection,
  templatesForParameter,
  writeSelection,
  type MappingContext,
  type MappingModelType,
  type ParameterDefinition,
} from '@/utils/parameter-mappings'
import { VButton, VCard } from '@halo-dev/components'
import { computed, shallowRef } from 'vue'

const INHERIT_CHOICE = '__inherit__'
const CUSTOM_FIELD_CHOICE = '__custom_field__'
const REASONING_CUSTOM_CHOICE = '__reasoning_custom__'
const UNSUPPORTED_CHOICE = '__unsupported__'
const REASONING_INTENTS = [
  { key: 'enabled', label: '开启', defaultValue: 'enabled' },
  { key: 'disabled', label: '关闭', defaultValue: 'disabled' },
  { key: 'low', label: '低', defaultValue: 'low' },
  { key: 'medium', label: '中', defaultValue: 'medium' },
  { key: 'high', label: '高', defaultValue: 'high' },
] as const
type ReasoningIntent = (typeof REASONING_INTENTS)[number]['key']

const props = defineProps<{
  context: MappingContext
  modelType?: MappingModelType
  adapterType?: string
  definitions?: ModelParameterDefinitionInfo[]
  templates?: ParameterMappingTemplateInfo[]
  defaults?: Record<string, DefaultParameterMappingInfo>
  inheritedMappings?: ModelParameterMappings
}>()

const mappings = defineModel<ModelParameterMappings>()
const showAdditionalMappings = shallowRef(false)

const definitions = computed(() =>
  parameterDefinitionsForModelType(props.definitions, props.modelType).filter(
    (definition) =>
      props.defaults?.[definition.parameter] ||
      templatesForParameter(props.templates, definition, props.adapterType).length,
  ),
)
const commonDefinitions = computed(() =>
  definitions.value.filter((definition) => definition.common),
)
const additionalDefinitions = computed(() =>
  definitions.value.filter((definition) => !definition.common),
)
const visibleDefinitions = computed(() => [
  ...commonDefinitions.value,
  ...additionalDefinitions.value.filter(
    (definition) => showAdditionalMappings.value || hasExplicitSelection(definition),
  ),
])

function selection(definition: ParameterDefinition) {
  return readSelection(mappings.value, definition)
}

function hasExplicitSelection(definition: ParameterDefinition) {
  const current = selection(definition)
  return Boolean(current && current.mode !== 'INHERIT')
}

function mode(definition: ParameterDefinition) {
  return selection(definition)?.mode || 'INHERIT'
}

function resolvedSelection(definition: ParameterDefinition) {
  return effectiveSelection(
    props.context,
    definition,
    mappings.value,
    props.inheritedMappings,
    props.defaults,
  ).selection
}

function mappingChoice(definition: ParameterDefinition) {
  const current = selection(definition)
  if (!current || current.mode === 'INHERIT') return INHERIT_CHOICE
  if (current.mode === 'UNSUPPORTED') return UNSUPPORTED_CHOICE
  if (definition.parameter === 'REASONING') return REASONING_CUSTOM_CHOICE
  if (current.field !== undefined) return CUSTOM_FIELD_CHOICE
  return current.template || INHERIT_CHOICE
}

function mappingOptions(definition: ParameterDefinition) {
  const templates = templatesForParameter(props.templates, definition, props.adapterType)
  if (definition.parameter === 'REASONING') {
    return [
      {
        value: INHERIT_CHOICE,
        label: props.context === 'provider' ? '使用内置默认' : '继承 Provider',
      },
      ...(templates.length ? [{ value: REASONING_CUSTOM_CHOICE, label: '自定义五档映射' }] : []),
      { value: UNSUPPORTED_CHOICE, label: '标记为不支持' },
    ]
  }
  return [
    {
      value: INHERIT_CHOICE,
      label: props.context === 'provider' ? '使用内置默认' : '继承 Provider',
    },
    ...templates.flatMap((template) =>
      template.id && template.displayName
        ? [{ value: template.id, label: template.displayName }]
        : [],
    ),
    ...(templates.length ? [{ value: CUSTOM_FIELD_CHOICE, label: '自定义请求字段…' }] : []),
    { value: UNSUPPORTED_CHOICE, label: '标记为不支持' },
  ]
}

function updateMappingChoice(definition: ParameterDefinition, value: unknown) {
  const choice = String(value)
  if (choice === INHERIT_CHOICE) {
    mappings.value = writeSelection(mappings.value, definition, undefined)
    return
  }
  if (choice === UNSUPPORTED_CHOICE) {
    mappings.value = writeSelection(mappings.value, definition, { mode: 'UNSUPPORTED' })
    return
  }
  if (choice === CUSTOM_FIELD_CHOICE) {
    const current = selection(definition)
    const effective = resolvedSelection(definition)
    const template =
      current?.template ||
      effective?.template ||
      templatesForParameter(props.templates, definition, props.adapterType)[0]?.id
    const descriptor = templateDescriptor(definition, template)
    mappings.value = writeSelection(mappings.value, definition, {
      ...templateSelection(definition, template),
      field: current?.field ?? effective?.field ?? defaultCustomField(descriptor),
    })
    return
  }
  if (choice === REASONING_CUSTOM_CHOICE) {
    mappings.value = writeSelection(mappings.value, definition, reasoningSelection(definition))
    return
  }
  mappings.value = writeSelection(mappings.value, definition, templateSelection(definition, choice))
}

function templateSelection(definition: ParameterDefinition, template?: string): Selection {
  return {
    mode: 'TEMPLATE',
    template,
  }
}

function reasoningSelection(definition: ParameterDefinition): Selection {
  const current = selection(definition)
  const inherited = effectiveSelection(
    props.context,
    definition,
    undefined,
    props.inheritedMappings,
    props.defaults,
  ).selection
  const template =
    (current?.mode === 'TEMPLATE' ? current.template : undefined) ||
    inherited?.template ||
    templatesForParameter(props.templates, definition, props.adapterType)[0]?.id
  const descriptor = templateDescriptor(definition, template)
  const source =
    (current?.mode === 'TEMPLATE' ? current.reasoningMapping : undefined) ||
    inherited?.reasoningMapping ||
    descriptor?.defaultReasoningMapping
  return {
    mode: 'TEMPLATE',
    template,
    reasoningMapping: cloneReasoningMapping(source),
  }
}

function updateField(definition: ParameterDefinition, value: unknown) {
  const current = selection(definition)
  if (current?.mode !== 'TEMPLATE') return
  mappings.value = writeSelection(mappings.value, definition, {
    ...current,
    field: String(value),
  })
}

function reasoningState(definition: ParameterDefinition, intent: ReasoningIntent) {
  return selection(definition)?.reasoningMapping?.[intent]
}

function setReasoningState(
  definition: ParameterDefinition,
  intent: ReasoningIntent,
  value: ReasoningValueMapping | undefined,
) {
  const current = selection(definition)
  if (current?.mode !== 'TEMPLATE') return
  mappings.value = writeSelection(mappings.value, definition, {
    ...current,
    reasoningMapping: {
      ...current.reasoningMapping,
      [intent]: value,
    },
  })
}

function toggleReasoningState(
  definition: ParameterDefinition,
  intent: ReasoningIntent,
  enabled: unknown,
) {
  if (!enabled) {
    setReasoningState(definition, intent, undefined)
    return
  }
  const descriptor = selectedTemplate(definition)
  const defaultState = descriptor?.defaultReasoningMapping?.[intent]
  const intentDefinition = REASONING_INTENTS.find((item) => item.key === intent)
  setReasoningState(
    definition,
    intent,
    defaultState
      ? { ...defaultState }
      : {
          field: descriptor?.defaultField || '',
          valueType: 'STRING',
          value: intentDefinition?.defaultValue || '',
        },
  )
}

function updateReasoningState(
  definition: ParameterDefinition,
  intent: ReasoningIntent,
  property: keyof ReasoningValueMapping,
  value: unknown,
) {
  const current = reasoningState(definition, intent)
  if (!current) return
  const next = { ...current, [property]: String(value) }
  if (
    property === 'valueType' &&
    value === 'BOOLEAN' &&
    !['true', 'false'].includes(next.value || '')
  ) {
    next.value = intent === 'disabled' ? 'false' : 'true'
  }
  setReasoningState(definition, intent, next)
}

function cloneReasoningMapping(value?: ReasoningMapping): ReasoningMapping {
  return Object.fromEntries(
    REASONING_INTENTS.flatMap(({ key }) => (value?.[key] ? [[key, { ...value[key] }]] : [])),
  ) as ReasoningMapping
}

function templateDescriptor(definition: ParameterDefinition, template?: string) {
  return templatesForParameter(props.templates, definition, props.adapterType).find(
    (item) => item.id === template,
  )
}

function selectedTemplate(definition: ParameterDefinition) {
  return templateDescriptor(definition, selection(definition)?.template)
}

function defaultCustomField(descriptor?: ParameterMappingTemplateInfo) {
  const field = descriptor?.defaultField || ''
  return field.startsWith('parameters.') ? field.slice('parameters.'.length) : field
}

function effectiveLabel(definition: ParameterDefinition) {
  const effective = effectiveSelection(
    props.context,
    definition,
    mappings.value,
    props.inheritedMappings,
    props.defaults,
  )
  if (effective.selection?.mode === 'UNSUPPORTED') return `${effective.source} · 不支持`
  if (definition.parameter === 'REASONING') return `${effective.source} · 五档映射`
  const template = props.templates?.find((item) => item.id === effective.selection?.template)
  return `${effective.source} · ${effective.selection?.field || template?.displayName || effective.selection?.template || '未声明'}`
}
</script>

<template>
  <div class=":uno: space-y-3">
    <VCard
      v-for="definition in visibleDefinitions"
      :key="definition.parameter"
      :body-class="['!p-4']"
    >
      <div class=":uno: flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div class=":uno: text-sm text-gray-800 font-medium">{{ definition.displayName }}</div>
          <div class=":uno: mt-0.5 text-xs text-gray-500">{{ definition.description }}</div>
        </div>
        <span class=":uno: shrink-0 rounded bg-gray-100 px-2 py-1 text-[11px] text-gray-600">
          {{ effectiveLabel(definition) }}
        </span>
      </div>

      <div class=":uno: mt-3">
        <FormKit
          type="select"
          :name="`parameterMapping_${definition.parameter}`"
          label="参数映射"
          :options="mappingOptions(definition)"
          :delay="0"
          :model-value="mappingChoice(definition)"
          @input="updateMappingChoice(definition, $event)"
        />
      </div>

      <FormKit
        v-if="
          definition.parameter !== 'REASONING' && mappingChoice(definition) === CUSTOM_FIELD_CHOICE
        "
        type="text"
        :name="`parameterMappingCustomField_${definition.parameter}`"
        label="自定义请求字段"
        help="沿用当前映射的放置位置和值转换规则；支持最多四段的点分路径。"
        validation="required"
        :delay="0"
        :placeholder="defaultCustomField(selectedTemplate(definition)) || '例如 temperature'"
        :model-value="selection(definition)?.field"
        @input="updateField(definition, $event)"
      />

      <div
        v-if="definition.parameter === 'REASONING' && mode(definition) === 'TEMPLATE'"
        class=":uno: mt-3 divide-y divide-gray-100"
      >
        <div
          v-for="intent in REASONING_INTENTS"
          :key="intent.key"
          class=":uno: py-4 first:pt-1 last:pb-0"
        >
          <div class=":uno: mb-3 flex items-start justify-between gap-4">
            <div>
              <div class=":uno: text-sm text-gray-800 font-medium">{{ intent.label }}</div>
              <div class=":uno: mt-0.5 text-xs text-gray-500">
                仅在调用方选择“{{ intent.label }}”时发送这组字段和值
              </div>
            </div>
            <FormKit
              type="checkbox"
              :name="`reasoningMappingEnabled_${intent.key}`"
              label="启用映射"
              :delay="0"
              :model-value="Boolean(reasoningState(definition, intent.key))"
              @input="toggleReasoningState(definition, intent.key, $event)"
            />
          </div>

          <div
            v-if="reasoningState(definition, intent.key)"
            class=":uno: grid items-start gap-3 md:grid-cols-3"
          >
            <FormKit
              type="text"
              outer-class="!py-0"
              :name="`reasoningMappingField_${intent.key}`"
              label="请求字段"
              help="支持最多四段的点分路径。"
              validation="required"
              :delay="0"
              :model-value="reasoningState(definition, intent.key)?.field"
              @input="updateReasoningState(definition, intent.key, 'field', $event)"
            />
            <FormKit
              type="select"
              outer-class="!py-0"
              :name="`reasoningMappingValueType_${intent.key}`"
              label="值类型"
              :options="[
                { label: '文本', value: 'STRING' },
                { label: '布尔值', value: 'BOOLEAN' },
                { label: '整数', value: 'INTEGER' },
                { label: '小数', value: 'DECIMAL' },
              ]"
              validation="required"
              :delay="0"
              :model-value="reasoningState(definition, intent.key)?.valueType"
              @input="updateReasoningState(definition, intent.key, 'valueType', $event)"
            />
            <FormKit
              v-if="reasoningState(definition, intent.key)?.valueType === 'BOOLEAN'"
              type="select"
              outer-class="!py-0"
              :name="`reasoningMappingValue_${intent.key}`"
              label="请求值"
              :options="[
                { label: 'true', value: 'true' },
                { label: 'false', value: 'false' },
              ]"
              validation="required"
              :delay="0"
              :model-value="reasoningState(definition, intent.key)?.value"
              @input="updateReasoningState(definition, intent.key, 'value', $event)"
            />
            <FormKit
              v-else
              outer-class="!py-0"
              :type="
                ['INTEGER', 'DECIMAL'].includes(
                  reasoningState(definition, intent.key)?.valueType || '',
                )
                  ? 'number'
                  : 'text'
              "
              :name="`reasoningMappingValue_${intent.key}`"
              label="请求值"
              :step="reasoningState(definition, intent.key)?.valueType === 'INTEGER' ? '1' : 'any'"
              validation="required"
              :delay="0"
              :model-value="reasoningState(definition, intent.key)?.value"
              @input="updateReasoningState(definition, intent.key, 'value', $event)"
            />
          </div>
        </div>
      </div>
    </VCard>

    <div v-if="additionalDefinitions.length" class=":uno: flex justify-center pt-1">
      <VButton size="sm" ghost @click="showAdditionalMappings = !showAdditionalMappings">
        {{ showAdditionalMappings ? '收起更多参数' : '显示更多参数' }}
      </VButton>
    </div>
  </div>
</template>
