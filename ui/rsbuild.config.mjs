import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit'
import { pluginSass } from '@rsbuild/plugin-sass'
import { UnoCSSRspackPlugin } from '@unocss/webpack/rspack'
import Icons from 'unplugin-icons/rspack'

const MANIFEST_PATH = '../app/src/main/resources/plugin.yaml'
const OUT_DIR_PROD = './build/dist'
const OUT_DIR_DEV = '../app/build/resources/main/console'

export default rsbuildConfig({
  manifestPath: MANIFEST_PATH,
  rsbuild: ({ envMode }) => {
    const isProduction = envMode === 'production'
    const outDir = isProduction ? OUT_DIR_PROD : OUT_DIR_DEV
    return {
      resolve: {
        alias: {
          '@': './src',
        },
      },
      output: {
        distPath: {
          root: outDir,
        },
      },
      plugins: [pluginSass()],
      tools: {
        rspack: {
          cache: false,
          plugins: [
            Icons({ compiler: 'vue3' }),
            UnoCSSRspackPlugin({
              configFile: './uno.config.mjs',
            }),
          ],
        },
      },
    }
  },
})
