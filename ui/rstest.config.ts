import { pluginVue } from '@rsbuild/plugin-vue'
import { defineConfig } from '@rstest/core'
import Icons from 'unplugin-icons/rspack'

export default defineConfig({
  plugins: [pluginVue()],
  source: {
    alias: {
      '@': './src',
    },
  },
  tools: {
    rspack: {
      plugins: [Icons({ compiler: 'vue3' })],
    },
  },
  testEnvironment: 'happy-dom',
})
