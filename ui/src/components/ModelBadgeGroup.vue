<script setup lang="ts">
import type { AiModel } from '@/api/generated'
import { modelFeatureLabel, modelTypeLabel } from '@/types'
import { VTag } from '@halo-dev/components'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    model: AiModel
    align?: 'start' | 'end'
  }>(),
  {
    align: 'end',
  },
)

const features = computed(() => props.model.spec.features || [])
</script>

<template>
  <div
    class=":uno: flex flex-wrap items-center gap-1.5"
    :class="{ ':uno: justify-end': align === 'end' }"
  >
    <VTag v-if="!model.spec.enabled">已禁用</VTag>
    <VTag>{{ modelTypeLabel(model.spec.modelType) }}</VTag>
    <VTag v-for="feature in features" :key="feature">
      {{ modelFeatureLabel(feature) }}
    </VTag>
  </div>
</template>
