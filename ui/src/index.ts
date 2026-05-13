import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiBrainLine from '~icons/ri/brain-line'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/ai-foundation',
        name: 'AiFoundation',
        component: () =>
          import(/* webpackChunkName: "ProviderManager" */ './views/ProviderManager.vue'),
        meta: {
          title: 'AI 模型配置',
          permissions: ['plugin:ai-foundation:manage'],
          menu: {
            name: 'AI 模型配置',
            group: 'system',
            icon: markRaw(RiBrainLine),
            priority: 100,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
