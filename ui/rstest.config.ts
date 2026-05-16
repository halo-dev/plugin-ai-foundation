import { pluginVue } from '@rsbuild/plugin-vue'
import { defineConfig } from '@rstest/core'

export default defineConfig({
  plugins: [pluginVue()],
  source: {
    alias: {
      '@': './src',
    },
  },
  testEnvironment: 'happy-dom',
})
