<script setup lang="ts">
import { IconArrowRight, VCard } from '@halo-dev/components'
import { CollapsibleContent, CollapsibleRoot, CollapsibleTrigger } from 'reka-ui'
import { onMounted, shallowRef } from 'vue'

withDefaults(
  defineProps<{
    title?: string
    sourceLabel?: string
  }>(),
  { title: '高级设置' },
)

// Reka 2.10.1 stores the generated content ID in a non-reactive context.
// Refresh the trigger once after its sibling content has registered that ID.
const contentReady = shallowRef(false)
const isOpen = shallowRef(false)
onMounted(() => {
  contentReady.value = true
})
</script>

<template>
  <CollapsibleRoot v-model:open="isOpen" class=":uno: mt-4" :unmount-on-hide="false">
    <VCard :body-class="['!p-0']">
      <CollapsibleTrigger
        :data-content-ready="contentReady || undefined"
        class=":uno: group min-h-11 w-full flex items-center justify-between gap-3 px-4 py-3 text-left text-sm text-gray-700 transition-colors hover:bg-gray-50"
      >
        <span class=":uno: flex items-center gap-2 font-medium">
          <IconArrowRight
            class=":uno: h-4 w-4 text-gray-500 transition-transform group-data-[state=open]:rotate-90"
          />
          {{ title }}
        </span>
        <span v-if="sourceLabel" class=":uno: text-xs text-gray-500">来源：{{ sourceLabel }}</span>
      </CollapsibleTrigger>
      <CollapsibleContent
        class=":uno: border-t border-gray-100 px-4 py-4 space-y-4"
        :style="{ display: isOpen ? undefined : 'none' }"
      >
        <slot />
      </CollapsibleContent>
    </VCard>
  </CollapsibleRoot>
</template>
