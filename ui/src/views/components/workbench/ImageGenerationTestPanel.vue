<script lang="ts" setup>
import type { TestGeneratedFile, TestImageGenerationResponse } from '@/api/generated'
import { VButton, VTag } from '@halo-dev/components'
import { filePartFromFile } from '@halo-dev/ai-foundation-sdk'
import { ref } from 'vue'
import RiImageAddLine from '~icons/ri/image-add-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'

const props = defineProps<{
  prompt: string
  inputUrl: string
  inputData: string
  inputMediaType: string
  maskUrl: string
  maskData: string
  maskMediaType: string
  result?: TestImageGenerationResponse
  error?: string
  isLoading: boolean
  disabled: boolean
}>()

const emit = defineEmits<{
  (e: 'update:prompt', value: string): void
  (e: 'update:inputUrl', value: string): void
  (e: 'update:inputData', value: string): void
  (e: 'update:inputMediaType', value: string): void
  (e: 'update:maskUrl', value: string): void
  (e: 'update:maskData', value: string): void
  (e: 'update:maskMediaType', value: string): void
  (e: 'run'): void
}>()

const inputFileRef = ref<HTMLInputElement | null>(null)
const maskFileRef = ref<HTMLInputElement | null>(null)
const fileError = ref('')

function resultImageUrl(image: TestGeneratedFile) {
  if (image.url) {
    return image.url
  }
  if (image.base64) {
    return `data:${image.mediaType || 'image/png'};base64,${image.base64}`
  }
  return undefined
}

function imageTitle(image: TestGeneratedFile) {
  return image.title || image.filename || image.id || `Image ${(image.index ?? 0) + 1}`
}

function openFilePicker(target: 'input' | 'mask') {
  if (props.disabled || props.isLoading) {
    return
  }
  if (target === 'input') {
    inputFileRef.value?.click()
  } else {
    maskFileRef.value?.click()
  }
}

async function handleFileChange(e: Event, target: 'input' | 'mask') {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  fileError.value = ''
  try {
    const part = await filePartFromFile(file)
    if (target === 'input') {
      emit('update:inputUrl', '')
      emit('update:inputData', typeof part.data === 'string' ? part.data : '')
      emit('update:inputMediaType', part.mediaType || file.type || 'image/png')
    } else {
      emit('update:maskUrl', '')
      emit('update:maskData', typeof part.data === 'string' ? part.data : '')
      emit('update:maskMediaType', part.mediaType || file.type || 'image/png')
    }
  } catch (error) {
    fileError.value = error instanceof Error ? error.message : '文件读取失败'
  } finally {
    input.value = ''
  }
}
</script>

<template>
  <div
    class=":uno: min-h-0 flex-1 overflow-y-auto bg-[radial-gradient(circle_at_top_left,rgba(14,165,233,0.10),transparent_28%),linear-gradient(180deg,#f8fafc_0%,#f1f5f9_100%)] px-4 py-5"
  >
    <div class=":uno: mx-auto max-w-5xl space-y-3">
      <div class=":uno: grid gap-3 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
          <label class=":uno: text-xs text-slate-600 font-medium">Prompt</label>
          <textarea
            :value="prompt"
            rows="8"
            placeholder="描述需要生成的图片..."
            class=":uno: mt-2 w-full resize-none text-sm text-slate-900 leading-relaxed outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 placeholder:text-slate-400 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
            :disabled="disabled || isLoading"
            @input="$emit('update:prompt', ($event.target as HTMLTextAreaElement).value)"
          />
          <div class=":uno: mt-3 flex justify-end">
            <VButton
              type="primary"
              :loading="isLoading"
              :disabled="disabled || !prompt.trim()"
              @click="$emit('run')"
            >
              <template #icon><RiSendPlaneLine /></template>
              生成图片
            </VButton>
          </div>
        </div>

        <div class=":uno: grid gap-3">
          <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
            <div class=":uno: flex items-center justify-between gap-3">
              <label class=":uno: text-xs text-slate-600 font-medium">参考图</label>
              <VButton size="sm" type="secondary" :disabled="disabled || isLoading" @click="openFilePicker('input')">
                <template #icon><RiImageAddLine /></template>
                选择文件
              </VButton>
            </div>
            <input
              ref="inputFileRef"
              type="file"
              accept="image/*"
              class=":uno: hidden"
              @change="handleFileChange($event, 'input')"
            />
            <input
              :value="inputUrl"
              placeholder="https://..."
              class=":uno: mt-2 w-full text-xs text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
              :disabled="disabled || isLoading"
              @input="$emit('update:inputUrl', ($event.target as HTMLInputElement).value)"
            />
            <div class=":uno: grid grid-cols-[minmax(0,1fr)_8rem] mt-2 gap-2">
              <textarea
                :value="inputData"
                rows="3"
                placeholder="base64"
                class=":uno: w-full resize-none text-xs text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
                :disabled="disabled || isLoading"
                @input="$emit('update:inputData', ($event.target as HTMLTextAreaElement).value)"
              />
              <input
                :value="inputMediaType"
                class=":uno: h-9 w-full text-xs text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
                :disabled="disabled || isLoading"
                @input="$emit('update:inputMediaType', ($event.target as HTMLInputElement).value)"
              />
            </div>
          </div>

          <div class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
            <div class=":uno: flex items-center justify-between gap-3">
              <label class=":uno: text-xs text-slate-600 font-medium">Mask</label>
              <VButton size="sm" type="secondary" :disabled="disabled || isLoading" @click="openFilePicker('mask')">
                <template #icon><RiImageAddLine /></template>
                选择文件
              </VButton>
            </div>
            <input
              ref="maskFileRef"
              type="file"
              accept="image/*"
              class=":uno: hidden"
              @change="handleFileChange($event, 'mask')"
            />
            <input
              :value="maskUrl"
              placeholder="https://..."
              class=":uno: mt-2 w-full text-xs text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
              :disabled="disabled || isLoading"
              @input="$emit('update:maskUrl', ($event.target as HTMLInputElement).value)"
            />
            <div class=":uno: grid grid-cols-[minmax(0,1fr)_8rem] mt-2 gap-2">
              <textarea
                :value="maskData"
                rows="3"
                placeholder="base64"
                class=":uno: w-full resize-none text-xs text-slate-700 font-mono outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-3 !py-2 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
                :disabled="disabled || isLoading"
                @input="$emit('update:maskData', ($event.target as HTMLTextAreaElement).value)"
              />
              <input
                :value="maskMediaType"
                class=":uno: h-9 w-full text-xs text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1 focus:!border-sky-400 focus:!ring-3 focus:!ring-sky-500/10"
                :disabled="disabled || isLoading"
                @input="$emit('update:maskMediaType', ($event.target as HTMLInputElement).value)"
              />
            </div>
          </div>
        </div>
      </div>

      <div
        v-if="error || fileError"
        class=":uno: border border-rose-200 rounded-lg bg-rose-50 text-sm text-rose-700 shadow-sm !px-4 !py-3"
      >
        {{ error || fileError }}
      </div>

      <div
        v-if="!result && !error"
        class=":uno: border border-slate-200 rounded-lg border-dashed bg-white text-center shadow-sm !px-6 !py-12"
      >
        <div
          class=":uno: mx-auto mb-3 size-12 flex items-center justify-center border border-slate-200 rounded-lg bg-sky-50"
        >
          <RiImageAddLine class=":uno: size-5 text-sky-600" />
        </div>
        <div class=":uno: text-sm text-slate-900 font-semibold">暂无图片生成结果</div>
        <div class=":uno: mt-1 text-xs text-slate-500">填写 Prompt 后点击生成图片</div>
      </div>

      <div v-if="result" class=":uno: border border-slate-200 rounded-lg bg-white shadow-sm !p-4">
        <div class=":uno: flex flex-wrap items-center gap-2">
          <span class=":uno: text-sm text-slate-950 font-semibold">图片结果</span>
          <VTag size="sm">{{ result.imagesCount || result.images?.length || 0 }} 张</VTag>
          <VTag v-if="result.usage?.imageCount !== undefined" size="sm">
            {{ result.usage.imageCount }} images
          </VTag>
        </div>

        <div class=":uno: grid mt-3 gap-3 md:grid-cols-2 xl:grid-cols-3">
          <div
            v-for="image in result.images || []"
            :key="image.id || image.index"
            class=":uno: overflow-hidden border border-slate-200 rounded-lg bg-slate-50"
          >
            <img
              v-if="resultImageUrl(image)"
              :src="resultImageUrl(image)"
              :alt="imageTitle(image)"
              class=":uno: aspect-square w-full bg-white object-contain"
            />
            <div class=":uno: px-3 py-2">
              <div class=":uno: truncate text-xs text-slate-800 font-medium">
                {{ imageTitle(image) }}
              </div>
              <div class=":uno: mt-0.5 truncate text-[11px] text-slate-500">
                {{ image.mediaType || image.url || 'base64' }}
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="result.warnings?.length"
          class=":uno: mt-3 border border-amber-200 rounded-lg bg-amber-50 !px-3 !py-2"
        >
          <div class=":uno: text-xs text-amber-800 font-medium">Warnings</div>
          <ul class=":uno: mt-1 text-xs text-amber-700 space-y-0.5">
            <li v-for="warning in result.warnings" :key="`${warning.code}-${warning.message}`">
              <span class=":uno: font-mono">{{ warning.code }}</span>
              <span v-if="warning.message">: {{ warning.message }}</span>
            </li>
          </ul>
        </div>

        <details class=":uno: mt-3 text-xs text-slate-600">
          <summary class=":uno: cursor-pointer select-none font-medium">诊断信息</summary>
          <pre class=":uno: mt-2 overflow-auto rounded-md bg-slate-950 text-xs text-slate-100 !p-3">{{
            JSON.stringify(
              {
                responses: result.responses,
                providerMetadata: result.providerMetadata,
                usage: result.usage,
              },
              null,
              2,
            )
          }}</pre>
        </details>
      </div>
    </div>
  </div>
</template>
