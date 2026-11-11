import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit'
import path from 'node:path'
import UnoCSS from 'unocss/vite'
import Icons from 'unplugin-icons/vite'

const MANIFEST_PATH = '../app/src/main/resources/plugin.yaml'
const OUT_DIR_PROD = './build/dist'
const OUT_DIR_DEV = '../app/build/resources/main/ui'

export default viteConfig({
  manifestPath: MANIFEST_PATH,
  format: 'esm',
  vite: ({ mode }) => {
    return {
      resolve: {
        alias: {
          '@': path.resolve(import.meta.dirname, 'src'),
        },
      },
      build: {
        outDir: mode === 'production' ? OUT_DIR_PROD : OUT_DIR_DEV,
      },
      plugins: [
        Icons({ compiler: 'vue3' }),
        ...(mode === 'test'
          ? []
          : [
              UnoCSS({
                mode: 'vue-scoped',
              }),
            ]),
      ],
      test: {
        environment: 'happy-dom',
      },
    }
  },
})
