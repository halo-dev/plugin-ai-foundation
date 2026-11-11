import { DecisionQuestionTypeEnum } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref, toRaw } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import DecisionQuestionEditor from './DecisionQuestionEditor.vue'

const mounted: ReturnType<typeof mount>[] = []

afterEach(() => {
  for (const wrapper of mounted) wrapper.unmount()
  mounted.length = 0
})

describe('DecisionQuestionEditor', () => {
  it('edits and removes paired options without changing the remaining values', async () => {
    const wrapper = mountHost()
    await wrapper.get<HTMLInputElement>('input[aria-label="选项 1 标识"]').setValue('rating=high')
    await settleFormKit()
    await wrapper
      .get<HTMLTextAreaElement>('textarea[aria-label="选项 1 说明"]')
      .setValue('高=正面\n继续保持原意')
    await settleFormKit()
    await buttonWithText(wrapper, '添加选项').trigger('click')

    expect(wrapper.get<HTMLInputElement>('input[aria-label="选项 3 标识"]').element.value).toBe('')
    await wrapper.get<HTMLInputElement>('input[aria-label="选项 3 标识"]').setValue('neutral')
    await settleFormKit()
    await wrapper.get<HTMLTextAreaElement>('textarea[aria-label="选项 3 说明"]').setValue('中性')
    await settleFormKit()
    await wrapper.get('button[aria-label="删除选项 2"]').trigger('click')
    await settleFormKit()

    expect(wrapper.vm.question.options).toEqual([
      { key: 'rating=high', label: '高=正面\n继续保持原意' },
      { key: 'neutral', label: '中性' },
    ])
    expect(wrapper.get<HTMLInputElement>('input[aria-label="选项 2 标识"]').element.value).toBe(
      'neutral',
    )
    expect(
      wrapper.get<HTMLTextAreaElement>('textarea[aria-label="选项 2 说明"]').element.value,
    ).toBe('中性')
  })

  it('keeps option and level inputs when switching question types', async () => {
    const wrapper = mountHost()
    await wrapper
      .get<HTMLTextAreaElement>('textarea[aria-label="选项 1 说明"]')
      .setValue('积极评价')
    await settleFormKit()
    await wrapper.get('select').setValue(DecisionQuestionTypeEnum.Score)
    await settleFormKit()
    await wrapper
      .get<HTMLTextAreaElement>('textarea[aria-label="等级 1 说明"]')
      .setValue('普通评价')
    await settleFormKit()
    await buttonWithText(wrapper, '添加等级').trigger('click')
    await wrapper
      .get<HTMLTextAreaElement>('textarea[aria-label="等级 3 说明"]')
      .setValue('出色评价')
    await settleFormKit()
    await wrapper.get('button[aria-label="删除等级 0"]').trigger('click')
    await settleFormKit()

    expect(
      wrapper.get<HTMLTextAreaElement>('textarea[aria-label="等级 0 说明"]').element.value,
    ).toBe('普通评价')
    expect(wrapper.find('textarea[aria-label="等级 3 说明"]').exists()).toBe(false)
    await wrapper.get('select').setValue(DecisionQuestionTypeEnum.YesNo)
    await settleFormKit()
    expect(wrapper.find('fieldset').exists()).toBe(false)
    await wrapper.get('select').setValue(DecisionQuestionTypeEnum.Choice)
    await settleFormKit()

    expect(
      wrapper.get<HTMLTextAreaElement>('textarea[aria-label="选项 1 说明"]').element.value,
    ).toBe('积极评价')
    expect(wrapper.vm.question.levels).toEqual(['普通评价', '好', '出色评价'])
  })

  it('disables every input and action while a request is running', async () => {
    const wrapper = mountHost(true)
    const editor = wrapper.getComponent(DecisionQuestionEditor)
    const original = structuredClone(toRaw(wrapper.vm.question))

    for (const input of wrapper.findAll('input, textarea, select, button')) {
      expect(input.attributes('disabled')).toBeDefined()
    }
    for (const button of wrapper.findAll('button')) await button.trigger('click')

    expect(wrapper.vm.question).toEqual(original)
    expect(editor.emitted('update')).toBeUndefined()
    expect(editor.emitted('remove')).toBeUndefined()
  })

  it('emits deletion only when the delete question action is selected', async () => {
    const wrapper = mountHost()
    const editor = wrapper.getComponent(DecisionQuestionEditor)
    await wrapper.get('button[aria-label="删除选项 1"]').trigger('click')
    expect(editor.emitted('remove')).toBeUndefined()

    await wrapper.get('button[aria-label="删除问题 1"]').trigger('click')
    expect(editor.emitted('remove')).toHaveLength(1)
  })
})

function mountHost(disabled = false) {
  const wrapper = mount(
    defineComponent({
      components: { DecisionQuestionEditor },
      setup() {
        const question = ref<DecisionQuestionDraft>({
          key: 1,
          id: 'sentiment',
          type: DecisionQuestionTypeEnum.Choice,
          instructions: '判断情绪',
          options: [
            { key: 'positive', label: '正面' },
            { key: 'negative', label: '负面' },
          ],
          levels: ['差', '一般', '好'],
        })
        return { question, disabled }
      },
      template:
        '<DecisionQuestionEditor :question="question" :index="0" :disabled="disabled" @update="question = $event" />',
    }),
    {
      global: {
        plugins: [
          [FormKitPlugin, defaultConfig()],
          createRouter({
            history: createMemoryHistory(),
            routes: [{ path: '/', component: { template: '<div />' } }],
          }),
        ],
      },
    },
  )
  mounted.push(wrapper)
  return wrapper
}

function buttonWithText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((button) => button.text() === text)
  if (!button) throw new Error(`找不到按钮：${text}`)
  return button
}

async function settleFormKit() {
  await new Promise((resolve) => setTimeout(resolve, 25))
  await nextTick()
}