<script setup lang="ts">
import { reloadUsageQueries } from '@/composables/use-usage-statistics'
import { aiConsoleApiClient } from '@/api'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, shallowRef, useTemplateRef } from 'vue'

const emit = defineEmits<{
  (event: 'close'): void
}>()

const CONFIRMATION = 'RESET'

const modal = useTemplateRef<InstanceType<typeof VModal>>('modal')
const confirmation = shallowRef('')

const queryClient = useQueryClient()

const confirmed = computed(() => confirmation.value === CONFIRMATION)

const { mutate, isPending } = useMutation({
  mutationFn: async () => {
    const { data } = await aiConsoleApiClient.usageStatistics.resetAiUsageStatistics({
      resetRequest: { confirmation: CONFIRMATION },
    })
    return data
  },
  onSuccess: () => {
    Toast.success('用量统计已重置')
    reloadUsageQueries(queryClient)
    modal.value?.close()
  },
  onError: () => {
    Toast.error('重置失败，请稍后重试')
  },
})

function onSubmit() {
  if (confirmed.value && !isPending.value) {
    mutate()
  }
}
</script>

<template>
  <VModal
    mount-to-body
    title="重置用量统计"
    :centered="false"
    :width="520"
    ref="modal"
    @close="emit('close')"
  >
    <div class=":uno: flex flex-col gap-3 text-sm text-gray-700">
      <p>
        此操作将<span class=":uno: text-red-600 font-semibold">永久删除</span
        >全部调用历史、执行明细与每日汇总，并开始新的统计周期。该操作不可撤销。
      </p>
      <p class=":uno: text-xs text-gray-500">
        请输入 <span class=":uno: font-semibold font-mono">RESET</span> 以确认操作。
      </p>
      <input
        v-model="confirmation"
        type="text"
        aria-label="确认重置"
        placeholder="RESET"
        class=":uno: h-9 border border-gray-200 rounded-md px-2 text-sm"
        @keyup.enter="onSubmit"
      />
    </div>
    <template #footer>
      <VSpace>
        <VButton type="danger" :loading="isPending" :disabled="!confirmed" @click="onSubmit">
          确认重置
        </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
