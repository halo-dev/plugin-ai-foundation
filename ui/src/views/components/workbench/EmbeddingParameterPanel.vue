<script lang="ts" setup>
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'

defineProps<{
  embeddingDimensions?: number
  embeddingMaxBatchSize?: number
  embeddingMaxParallelCalls?: number
  embeddingMaxRetries?: number
}>()

const emit = defineEmits<{
  (e: 'update:embeddingDimensions', value: number | undefined): void
  (e: 'update:embeddingMaxBatchSize', value: number | undefined): void
  (e: 'update:embeddingMaxParallelCalls', value: number | undefined): void
  (e: 'update:embeddingMaxRetries', value: number | undefined): void
}>()

type NumberField =
  | 'embeddingDimensions'
  | 'embeddingMaxBatchSize'
  | 'embeddingMaxParallelCalls'
  | 'embeddingMaxRetries'

function updateNumberField(key: NumberField, value: string) {
  const number = value === '' ? undefined : Number(value)
  switch (key) {
    case 'embeddingDimensions':
      emit('update:embeddingDimensions', number)
      break
    case 'embeddingMaxBatchSize':
      emit('update:embeddingMaxBatchSize', number)
      break
    case 'embeddingMaxParallelCalls':
      emit('update:embeddingMaxParallelCalls', number)
      break
    case 'embeddingMaxRetries':
      emit('update:embeddingMaxRetries', number)
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
        嵌入参数
      </summary>
      <div class=":uno: pb-3 pl-5 space-y-3">
        <div class=":uno: grid grid-cols-2 gap-2">
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Dimensions</label>
            <input
              type="number"
              :value="embeddingDimensions"
              min="1"
              step="1"
              placeholder="默认"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField('embeddingDimensions', ($event.target as HTMLInputElement).value)
              "
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Max Batch Size</label>
            <input
              type="number"
              :value="embeddingMaxBatchSize"
              min="1"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField(
                  'embeddingMaxBatchSize',
                  ($event.target as HTMLInputElement).value,
                )
              "
            />
          </div>
        </div>

        <div class=":uno: grid grid-cols-2 gap-2">
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Max Parallel Calls</label>
            <input
              type="number"
              :value="embeddingMaxParallelCalls"
              min="1"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField(
                  'embeddingMaxParallelCalls',
                  ($event.target as HTMLInputElement).value,
                )
              "
            />
          </div>
          <div class=":uno: space-y-1">
            <label class=":uno: text-xs text-slate-600 font-medium">Max Retries</label>
            <input
              type="number"
              :value="embeddingMaxRetries"
              min="0"
              step="1"
              class=":uno: w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs placeholder:text-slate-400 focus:!border-teal-400 placeholder:!text-xs focus:!ring-3 focus:!ring-teal-500/10"
              @input="
                updateNumberField('embeddingMaxRetries', ($event.target as HTMLInputElement).value)
              "
            />
          </div>
        </div>
      </div>
    </details>

  </div>
</template>
