<script lang="ts" setup>
import { computed } from 'vue'
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'

type ImageResponseFormat = 'DEFAULT' | 'URL' | 'BASE64'

const props = defineProps<{
  imageN?: number
  imageWidth?: number
  imageHeight?: number
  imageAspectRatio?: string
  imageSeed?: number
  imageResponseFormat?: ImageResponseFormat
  imageMaxRetries?: number
  imageMaxParallelCalls?: number
  imageHeadersText?: string
  imageHeadersError?: string
}>()

const emit = defineEmits<{
  (e: 'update:imageN', value: number | undefined): void
  (e: 'update:imageWidth', value: number | undefined): void
  (e: 'update:imageHeight', value: number | undefined): void
  (e: 'update:imageAspectRatio', value: string): void
  (e: 'update:imageSeed', value: number | undefined): void
  (e: 'update:imageResponseFormat', value: ImageResponseFormat): void
  (e: 'update:imageMaxRetries', value: number | undefined): void
  (e: 'update:imageMaxParallelCalls', value: number | undefined): void
  (e: 'update:imageHeadersText', value: string): void
}>()

const imageHeadersHelp = computed(
  () => props.imageHeadersError || '请求级 headers，当前 provider 不支持时会返回 warning 或错误',
)

type NumberField =
  | 'imageN'
  | 'imageWidth'
  | 'imageHeight'
  | 'imageSeed'
  | 'imageMaxRetries'
  | 'imageMaxParallelCalls'

function updateNumberField(key: NumberField, value: string) {
  const number = value === '' ? undefined : Number(value)
  switch (key) {
    case 'imageN':
      emit('update:imageN', number)
      break
    case 'imageWidth':
      emit('update:imageWidth', number)
      break
    case 'imageHeight':
      emit('update:imageHeight', number)
      break
    case 'imageSeed':
      emit('update:imageSeed', number)
      break
    case 'imageMaxRetries':
      emit('update:imageMaxRetries', number)
      break
    case 'imageMaxParallelCalls':
      emit('update:imageMaxParallelCalls', number)
  }
}
</script>

<template>
  <div>
    <details class=":uno: group border-b border-slate-200 last:border-b-0" open>
      <summary
        class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
      >
        <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
        图片参数
      </summary>
      <div class=":uno: pb-3 pl-5 space-y-3">
        <div class=":uno: grid grid-cols-2 gap-2">
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">数量</label>
            <input
              type="number"
              :value="imageN"
              min="1"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="updateNumberField('imageN', ($event.target as HTMLInputElement).value)"
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">响应格式</label>
            <select
              :value="imageResponseFormat || 'DEFAULT'"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @change="
                emit(
                  'update:imageResponseFormat',
                  ($event.target as HTMLSelectElement).value as ImageResponseFormat,
                )
              "
            >
              <option value="DEFAULT">默认</option>
              <option value="URL">URL</option>
              <option value="BASE64">Base64</option>
            </select>
          </div>
        </div>

        <div class=":uno: grid grid-cols-2 gap-2">
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Width</label>
            <input
              type="number"
              :value="imageWidth"
              min="1"
              step="1"
              placeholder="默认"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="updateNumberField('imageWidth', ($event.target as HTMLInputElement).value)"
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Height</label>
            <input
              type="number"
              :value="imageHeight"
              min="1"
              step="1"
              placeholder="默认"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="updateNumberField('imageHeight', ($event.target as HTMLInputElement).value)"
            />
          </div>
        </div>

        <div class=":uno: space-y-1">
          <label class=":uno: text-xs text-slate-600 font-medium">Aspect Ratio</label>
          <input
            :value="imageAspectRatio"
            placeholder="例如 16:9"
            class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
            @input="emit('update:imageAspectRatio', ($event.target as HTMLInputElement).value)"
          />
        </div>

        <div class=":uno: grid grid-cols-3 gap-2">
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Seed</label>
            <input
              type="number"
              :value="imageSeed"
              step="1"
              placeholder="随机"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="updateNumberField('imageSeed', ($event.target as HTMLInputElement).value)"
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Retries</label>
            <input
              type="number"
              :value="imageMaxRetries"
              min="0"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField('imageMaxRetries', ($event.target as HTMLInputElement).value)
              "
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Parallel</label>
            <input
              type="number"
              :value="imageMaxParallelCalls"
              min="1"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField(
                  'imageMaxParallelCalls',
                  ($event.target as HTMLInputElement).value,
                )
              "
            />
          </div>
        </div>
      </div>
    </details>

    <details class=":uno: group border-b border-slate-200 last:border-b-0">
      <summary
        class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
      >
        <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
        Headers
      </summary>
      <div class=":uno: pb-3 pl-5">
        <textarea
          :value="imageHeadersText"
          rows="4"
          :class="{ ':uno: !border-rose-300': imageHeadersError }"
          class=":uno: w-full text-slate-700 leading-relaxed font-mono outline-none transition-colors !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
          @input="emit('update:imageHeadersText', ($event.target as HTMLTextAreaElement).value)"
        />
        <div
          class=":uno: mt-1 text-[10px]"
          :class="imageHeadersError ? ':uno: text-rose-500' : ':uno: text-slate-400'"
        >
          {{ imageHeadersHelp }}
        </div>
      </div>
    </details>
  </div>
</template>
