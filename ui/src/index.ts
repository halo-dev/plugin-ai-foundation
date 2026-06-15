import { definePlugin } from '@halo-dev/ui-shared'
import 'uno.css'
import { markRaw } from 'vue'
import MingcuteAiLine from '~icons/mingcute/ai-line'
import {
  AI_MODEL_SELECTOR_FORMKIT_TYPE,
  aiModelSelectorInput,
} from './formkit/ai-model-selector-input'
import { AI_FOUNDATION_ROUTE_NAMES } from './routes'

const pluginModule = {
  formkit: {
    inputs: {
      [AI_MODEL_SELECTOR_FORMKIT_TYPE]: aiModelSelectorInput,
    },
  },
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/ai-foundation',
        name: AI_FOUNDATION_ROUTE_NAMES.ROOT,
        component: () =>
          import(/* webpackChunkName: "ProviderManager" */ './views/ProviderManager.vue'),
        redirect: {
          name: AI_FOUNDATION_ROUTE_NAMES.PROVIDERS,
        },
        meta: {
          title: 'AI Foundation',
          permissions: ['*'],
          menu: {
            name: 'AI Foundation',
            group: 'system',
            icon: markRaw(MingcuteAiLine),
            priority: 100,
          },
          hideFooter: true,
        },
        children: [
          {
            path: 'providers',
            name: AI_FOUNDATION_ROUTE_NAMES.PROVIDERS,
            component: () =>
              import(
                /* webpackChunkName: "AiFoundationProviders" */ './views/ProviderConfigView.vue'
              ),
          },
          {
            path: 'models',
            name: AI_FOUNDATION_ROUTE_NAMES.MODELS,
            component: () =>
              import(/* webpackChunkName: "AiFoundationModels" */ './views/AllModelListView.vue'),
          },
          {
            path: 'defaults',
            name: AI_FOUNDATION_ROUTE_NAMES.DEFAULTS,
            component: () =>
              import(
                /* webpackChunkName: "AiFoundationDefaults" */ './views/DefaultModelSlotsView.vue'
              ),
          },
          {
            path: 'callers',
            name: AI_FOUNDATION_ROUTE_NAMES.CALLERS,
            component: () =>
              import(
                /* webpackChunkName: "AiFoundationCallers" */ './views/CallerPluginListView.vue'
              ),
          },
          {
            path: 'test',
            name: AI_FOUNDATION_ROUTE_NAMES.TEST,
            component: () =>
              import(
                /* webpackChunkName: "AiFoundationTest" */ './views/ModelTestWorkbenchView.vue'
              ),
          },
        ],
      },
    },
  ],
  extensionPoints: {},
}

export default definePlugin(pluginModule)
