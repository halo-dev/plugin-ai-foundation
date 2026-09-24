<script setup lang="ts">
import { IconArrowDown, IconSearch, VDropdown } from '@halo-dev/components'
import { computed, ref } from 'vue'

const props = defineProps<{
  label: string
  modelValue?: string
  items: { label: string; value?: string; description?: string }[]
  searchable?: boolean
  allowCustom?: boolean
}>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string | undefined): void }>()
const keyword = ref('')
const open = ref(false)
const selected = computed(() => props.items.find((item) => item.value === props.modelValue))
const filtered = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  if (!query) return props.items
  return props.items.filter((item) =>
    `${item.label} ${item.description || ''} ${item.value || ''}`
      .toLocaleLowerCase()
      .includes(query),
  )
})
const customValue = computed(() => {
  const value = keyword.value.trim()
  if (!props.allowCustom) return undefined
  if (!value) return undefined
  if (props.items.some((item) => item.value === value)) return undefined
  return value
})
function closeMenu() {
  keyword.value = ''
  open.value = false
}
function moveFocus(event: KeyboardEvent) {
  const container = event.currentTarget as HTMLElement
  const buttons = Array.from(container.querySelectorAll<HTMLButtonElement>('[data-option]'))
  const index = buttons.indexOf(event.target as HTMLButtonElement)
  let next: number
  switch (event.key) {
    case 'ArrowDown':
      next = index + 1
      break
    case 'ArrowUp':
      next = index < 0 ? buttons.length - 1 : index - 1
      break
    default:
      return
  }
  event.preventDefault()
  buttons[(next + buttons.length) % buttons.length]?.focus()
}
</script>

<template>
  <VDropdown placement="bottom-start" :distance="6" @show="open = true" @hide="closeMenu">
    <button
      type="button"
      class=":uno: usage-select-trigger"
      :aria-expanded="open"
      :class="{ ':uno: is-active': modelValue !== undefined }"
      :aria-label="`${label}：${selected?.label || modelValue || '全部'}`"
    >
      <span class=":uno: usage-select-label">{{ label }}</span>
      <span class=":uno: usage-select-value" :title="selected?.label || modelValue">{{
        selected?.label || modelValue || '全部'
      }}</span>
      <IconArrowDown class=":uno: usage-select-arrow" />
    </button>
    <template #popper>
      <div class=":uno: usage-select-menu" @keydown="moveFocus">
        <div v-if="searchable" class=":uno: usage-select-search">
          <IconSearch class=":uno: size-4 shrink-0 text-gray-400" />
          <input
            v-model="keyword"
            type="search"
            :aria-label="`搜索${label}`"
            :placeholder="`搜索${label}名称或标识`"
          />
        </div>
        <div class=":uno: usage-select-options" :aria-label="`${label}选项`">
          <button
            v-for="(item, index) in filtered"
            :key="item.value ?? index"
            v-close-popper
            type="button"
            data-option
            class=":uno: usage-select-option"
            :aria-pressed="item.value === modelValue"
            @click="emit('update:modelValue', item.value)"
          >
            <span class=":uno: min-w-0 flex-1">
              <span class=":uno: usage-select-option-label">{{ item.label }}</span>
              <span v-if="item.description" class=":uno: usage-select-description">{{
                item.description
              }}</span>
            </span>
            <span class=":uno: usage-select-check" aria-hidden="true">{{
              item.value === modelValue ? '✓' : ''
            }}</span>
          </button>
          <button
            v-if="customValue"
            v-close-popper
            type="button"
            data-option
            class=":uno: usage-select-option"
            @click="emit('update:modelValue', customValue)"
          >
            <span class=":uno: min-w-0 flex-1">
              <span class=":uno: usage-select-option-label">历史标识：{{ customValue }}</span>
              <span class=":uno: usage-select-description">按已删除资源的完整标识查询</span>
            </span>
          </button>
          <p
            v-if="!filtered.length && !customValue"
            class=":uno: px-3 py-6 text-center text-sm text-gray-500"
            role="status"
          >
            没有匹配的选项
          </p>
        </div>
      </div>
    </template>
  </VDropdown>
</template>

<style scoped>
.usage-select-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 260px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
}
.usage-select-trigger:hover,
.usage-select-trigger:focus-visible {
  background: #f9fafb;
  border-color: #9ca3af;
}
.usage-select-trigger.is-active {
  border-color: #d1d5db;
}
.usage-select-label {
  flex-shrink: 0;
  color: #6b7280;
}
.usage-select-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-select-arrow {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
  margin-left: auto;
}
.usage-select-menu {
  width: min(320px, calc(100vw - 32px));
  max-height: min(400px, 65dvh);
  display: flex;
  flex-direction: column;
}
.usage-select-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid #f3f4f6;
}
.usage-select-search input {
  min-width: 0;
  width: 100%;
  height: 28px;
  background: transparent;
  border: 0;
  outline: none;
  font-size: 12px;
}
.usage-select-options {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px;
}
.usage-select-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 0;
  border-radius: 4px;
  padding: 8px 10px;
  text-align: left;
  background: white;
  cursor: pointer;
}
.usage-select-option:hover,
.usage-select-option:focus-visible,
.usage-select-option[aria-pressed='true'] {
  background: #f3f4f6;
  outline: none;
}
.usage-select-option:focus-visible {
  box-shadow: inset 0 0 0 1px #9ca3af;
}
.usage-select-option-label {
  display: block;
  font-size: 13px;
  line-height: 20px;
  color: #111827;
  overflow-wrap: anywhere;
}
.usage-select-description {
  display: block;
  margin-top: 2px;
  color: #6b7280;
  font-size: 11px;
  line-height: 16px;
  overflow-wrap: anywhere;
}
.usage-select-check {
  width: 14px;
  flex-shrink: 0;
  color: #374151;
}
</style>
