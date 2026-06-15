import { defineConfig } from '@rstest/core'

export default defineConfig({
  include: ['src/**/*.test.ts'],
  testEnvironment: 'happy-dom',
})
