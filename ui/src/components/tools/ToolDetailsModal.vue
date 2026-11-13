<script setup lang="ts">
import type { ToolDescriptor } from '@/api/generated'
import { toolDisplayName } from '@/utils/tools'
import { VButton, VModal } from '@halo-dev/components'
import { ref } from 'vue'
import ToolDetailsContent from './ToolDetailsContent.vue'

defineProps<{ tool: ToolDescriptor }>()
const emit = defineEmits<{ close: [] }>()
const modal = ref<InstanceType<typeof VModal>>()
</script>

<template>
  <VModal
    ref="modal"
    :title="toolDisplayName(tool)"
    :width="760"
    mount-to-body
    @close="emit('close')"
  >
    <div class=":uno: max-h-[70vh] min-w-0 overflow-y-auto">
      <ToolDetailsContent :tool="tool" />
    </div>
    <template #footer><VButton @click="modal?.close()">关闭</VButton></template>
  </VModal>
</template>
