import type { FormKitSectionsSchema } from '@formkit/core'
import { createInput } from '@formkit/vue'
import { AI_TOOL_SELECTOR_FORMKIT_TYPE } from '@halo-dev/ai-foundation-sdk'
import { defineAsyncComponent } from 'vue'

export { AI_TOOL_SELECTOR_FORMKIT_TYPE }

const aiToolSelectorSections: FormKitSectionsSchema = {
  inner: { attrs: { class: ':uno: w-full' } },
  input: { props: { class: ':uno: !py-0' } },
}

export const aiToolSelectorInput = createInput<unknown>(
  defineAsyncComponent(() => import('./AiToolSelectorInput.vue')),
  {
    props: ['pluginName', 'category', 'placeholder', 'searchPlaceholder', 'fullWidth'],
    family: 'text',
    forceTypeProp: 'select',
  },
  aiToolSelectorSections,
)
