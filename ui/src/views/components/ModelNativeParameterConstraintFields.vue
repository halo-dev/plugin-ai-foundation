<script setup lang="ts">
import type { ModelParameterConstraints } from '@/api/generated'

const props = defineProps<{
  inherited?: ModelParameterConstraints
  loading?: boolean
  failed?: boolean
}>()
const constraints = defineModel<ModelParameterConstraints>()
const booleanFields = [
  { key: 'toolStrict', label: '原生工具严格模式' },
  { key: 'store', label: '响应存储' },
  { key: 'streamUsage', label: '流式用量统计' },
  { key: 'promptCacheKey', label: '提示词缓存键' },
] as const
const listFields = [
  { key: 'toolChoices', label: '工具选择模式' },
  { key: 'serviceTiers', label: '服务等级' },
] as const

function options(inherited?: boolean) {
  return [
    {
      label: automaticValue(inherited === undefined ? undefined : inherited ? '支持' : '不支持'),
      value: '',
    },
    { label: '手动：支持', value: 'true' },
    { label: '手动：不支持', value: 'false' },
  ]
}

function automaticValue(value?: string) {
  if (props.loading) return '自动（加载中）'
  if (props.failed) return '自动（读取失败）'
  return value === undefined ? '自动（未声明）' : `自动（models.dev：${value}）`
}

function update(key: keyof ModelParameterConstraints, value: unknown) {
  const next = { ...constraints.value, [key]: value }
  if (value === undefined) delete next[key]
  constraints.value = Object.keys(next).length ? next : undefined
}

function booleanValue(value: unknown) {
  return value === 'true' ? true : value === 'false' ? false : undefined
}

function listValue(value: unknown) {
  return String(value || '').trim()
    ? String(value)
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean)
    : undefined
}
</script>

<template>
  <div class=":uno: grid gap-4 sm:grid-cols-2">
    <div v-for="field in booleanFields" :key="field.key">
      <FormKit
        type="select"
        :name="`constraint_${field.key}`"
        :label="field.label"
        :options="options(inherited?.[field.key])"
        :model-value="constraints?.[field.key] === undefined ? '' : String(constraints[field.key])"
        @input="update(field.key, booleanValue($event))"
      />
    </div>
    <div v-for="field in listFields" :key="field.key">
      <FormKit
        type="text"
        :name="`constraint_${field.key}`"
        :label="field.label"
        :placeholder="automaticValue(inherited?.[field.key]?.join(', '))"
        :model-value="constraints?.[field.key]?.join(', ')"
        @input="update(field.key, listValue($event))"
      />
    </div>
  </div>
</template>