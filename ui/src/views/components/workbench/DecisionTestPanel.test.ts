import { DecisionAnswerTypeEnum, DecisionQuestionTypeEnum } from '@/api/generated'
import type { DecisionQuestionDraft } from '@/utils/decision-workbench'
import { defaultConfig, plugin as FormKitPlugin } from '@formkit/vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import DecisionQuestionEditor from './DecisionQuestionEditor.vue'
import DecisionResults from './DecisionResults.vue'
import DecisionTestPanel from './DecisionTestPanel.vue'

const mounted: ReturnType<typeof mount>[] = []
afterEach(() => {
  mounted.forEach((wrapper) => wrapper.unmount())
  mounted.length = 0
  vi.useRealTimers()
})

function question(key: number): DecisionQuestionDraft {
  return {
    key,
    id: `q${key}`,
    type: DecisionQuestionTypeEnum.YesNo,
    instructions: '判断内容',
    options: [],
    levels: [],
  }
}
function mountPanel() {
  const questions = [question(1), question(2), question(3)]
  const wrapper = mount(DecisionTestPanel, {
    attachTo: document.body,
    props: {
      questions,
      stateText: '待判断内容',
      stateFormat: 'text',
      error: '',
      isLoading: false,
      disabled: false,
      result: {
        answers: {
          q1: { type: DecisionAnswerTypeEnum.YesNo, probability: 0.2 },
          q2: { type: DecisionAnswerTypeEnum.YesNo, probability: 0.8 },
        },
      },
      resultSnapshot: {
        stateText: '待判断内容',
        stateFormat: 'text',
        questions: structuredClone(questions),
      },
    },
    global: {
      plugins: [
        [FormKitPlugin, defaultConfig()],
        createRouter({
          history: createMemoryHistory(),
          routes: [{ path: '/', component: { template: '<div />' } }],
        }),
      ],
      stubs: { DecisionQuestionEditor: true, DecisionResults: true },
    },
  })
  mounted.push(wrapper)
  return wrapper
}

describe('DecisionTestPanel', () => {
  it('runs with the latest content when clicked immediately after editing', async () => {
    const wrapper = mountPanel()
    const run = vi.fn(() => wrapper.props('stateText'))
    await wrapper.setProps({
      'onUpdate:stateText': (value: string) => wrapper.setProps({ stateText: value }),
      onRun: run,
    })
    vi.useFakeTimers()

    const input = wrapper.get('textarea[aria-label="待判断的内容"]')
    await input.setValue('最新的待判断内容')
    await vi.advanceTimersByTimeAsync(5)
    await input.trigger('blur')
    const button = wrapper.findAll('button').find((item) => item.text() === '运行全部问题')!
    await button.trigger('click')

    expect(run).toHaveBeenCalledOnce()
    expect(run).toHaveReturnedWith('最新的待判断内容')
  })

  it('switches the editor and answer together and retains the submitted question after edits', async () => {
    const wrapper = mountPanel()
    expect(wrapper.getComponent(DecisionQuestionEditor).props('question').id).toBe('q1')
    await wrapper
      .get('[role="tab"][aria-label="问题 2：q2"]')
      .trigger('mousedown', { button: 0, ctrlKey: false })
    expect(wrapper.getComponent(DecisionQuestionEditor).props('question').id).toBe('q2')
    expect(wrapper.getComponent(DecisionResults).props('answer').probability).toBe(0.8)
    await wrapper.setProps({
      questions: [question(1), { ...question(2), id: 'renamed' }, question(3)],
      isResultStale: true,
    })
    expect(wrapper.getComponent(DecisionQuestionEditor).props('question').id).toBe('renamed')
    expect(wrapper.getComponent(DecisionResults).props('question')?.id).toBe('q2')
    expect(wrapper.text()).toContain('当前显示上次运行的结果')
  })

  it('selects an added question and selects the adjacent question after deletion', async () => {
    const wrapper = mountPanel()
    await wrapper.setProps({ questions: [question(1), question(2), question(3), question(4)] })
    expect(wrapper.getComponent(DecisionQuestionEditor).props('question').key).toBe(4)
    expect(wrapper.findComponent(DecisionResults).exists()).toBe(false)
    await wrapper.setProps({ questions: [question(1), question(2), question(3)] })
    await flushPromises()
    expect(wrapper.getComponent(DecisionQuestionEditor).props('question').key).toBe(3)
    expect(document.activeElement?.getAttribute('aria-label')).toBe('问题 3：q3')
    await wrapper.setProps({ questions: [] })
    await flushPromises()
    expect(wrapper.findComponent(DecisionQuestionEditor).exists()).toBe(false)
    expect(wrapper.text()).toContain('添加一个问题')
    expect(document.activeElement?.getAttribute('aria-label')).toBe('添加问题')
  })

  it('shows loading without old answers and leaves absent usage unreported', async () => {
    const wrapper = mountPanel()
    expect(wrapper.text()).not.toContain('tokens')
    await wrapper.setProps({ isLoading: true })
    expect(wrapper.findComponent(DecisionResults).exists()).toBe(false)
    expect(wrapper.getComponent(DecisionQuestionEditor).props('disabled')).toBe(true)
    const cancel = wrapper.findAll('button').find((button) => button.text() === '取消运行')!
    await cancel.trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})