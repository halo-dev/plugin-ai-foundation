import formkitVariants from '@formkit/themes/unocss'
import { defineConfig, presetWind3, transformerCompileClass } from 'unocss'

export default defineConfig({
  presets: [presetWind3(), formkitVariants()],
  transformers: [transformerCompileClass()],
  shortcuts: {
    'select-default':
      'h-8 rounded-md bg-white text-xs text-gray-700 outline-none transition !border !border-gray-200 !border-solid !py-0 !pl-2 !pr-10 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500',
    'select-workbench':
      'h-8 rounded-md text-xs text-slate-700 outline-none transition !border !border-slate-200 !border-solid !bg-white !py-0 !pl-2 !pr-10 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10',
  },
})
