import { createInput } from '@formkit/vue'
import { defineAsyncComponent } from 'vue'

export const AI_MODEL_SELECTOR_FORMKIT_TYPE = 'aiModelSelector'

export const aiModelSelectorInput = createInput<string | undefined>(
  defineAsyncComponent(() => import('./AiModelSelectorInput.vue')),
  {
    props: [
      'modelType',
      'providerName',
      'providerType',
      'enabled',
      'available',
      'requiredFeatures',
      'placeholder',
      'searchPlaceholder',
      'clearable',
      'fullWidth',
    ],
    family: 'text',
  },
)
