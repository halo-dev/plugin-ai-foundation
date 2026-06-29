import type { FormKitSectionsSchema } from '@formkit/core'
import { createInput } from '@formkit/vue'
import { defineAsyncComponent } from 'vue'

export const AI_MODEL_SELECTOR_FORMKIT_TYPE = 'aiModelSelector'

const aiModelSelectorSections: FormKitSectionsSchema = {
  inner: {
    attrs: {
      class: ':uno: w-full',
    },
  },
  input: {
    props: {
      class: ':uno: !py-0',
    },
  },
}

export const aiModelSelectorInput = createInput<unknown>(
  defineAsyncComponent(() => import('./AiModelSelectorInput.vue')),
  {
    props: [
      'modelType',
      'providerName',
      'providerType',
      'enabled',
      'available',
      'requiredFeatures',
      'requiredCapabilities',
      'placeholder',
      'searchPlaceholder',
      'clearable',
      'fullWidth',
    ],
    family: 'text',
    forceTypeProp: 'select',
  },
  aiModelSelectorSections,
)
