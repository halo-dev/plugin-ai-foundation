import { defineConfig } from '@rstest/core'

export default defineConfig({
  include: ['packages/ai-ui-vue/src/**/*.test.ts'],
  testEnvironment: 'happy-dom',
})
