<script setup lang="ts">
import type {
  AgentProfile,
  AgentRecoveryScenario,
  AgentStepPolicy,
} from '@/utils/model-test-workbench'
import { VSwitch } from '@halo-dev/components'
import RiArrowRightSLine from '~icons/ri/arrow-right-s-line'
import RiRobot2Line from '~icons/ri/robot-2-line'

defineProps<{
  enabled?: boolean
  profile?: AgentProfile
  maxSteps?: number
  stepPolicy?: AgentStepPolicy
  serverToolEnabled?: boolean
  browserToolEnabled?: boolean
  externalToolEnabled?: boolean
  approvalRequired?: boolean
  toolInputStreamEnabled?: boolean
  recoveryScenario?: AgentRecoveryScenario
}>()

const emit = defineEmits<{
  'update:enabled': [value: boolean]
  'update:profile': [value: AgentProfile]
  'update:maxSteps': [value: number]
  'update:stepPolicy': [value: AgentStepPolicy]
  'update:serverToolEnabled': [value: boolean]
  'update:browserToolEnabled': [value: boolean]
  'update:externalToolEnabled': [value: boolean]
  'update:approvalRequired': [value: boolean]
  'update:toolInputStreamEnabled': [value: boolean]
  'update:recoveryScenario': [value: AgentRecoveryScenario]
}>()
</script>

<template>
  <details class=":uno: group border-b border-slate-200 last:border-b-0" :open="enabled">
    <summary
      class=":uno: flex cursor-pointer select-none items-center gap-1.5 py-2 text-sm text-slate-800 font-semibold"
    >
      <RiArrowRightSLine class=":uno: size-4 transition-transform group-open:rotate-90" />
      <RiRobot2Line class=":uno: size-3.5" />
      Agent 运行时
    </summary>

    <div class=":uno: pb-3 pl-5 space-y-3">
      <div class=":uno: flex items-center justify-between gap-3">
        <div>
          <div class=":uno: text-xs text-slate-700 font-medium">启用 Agent 模式</div>
          <div class=":uno: mt-0.5 text-[10px] text-slate-400">
            通过后端公开 Agent API 执行，不改变浏览器聊天协议
          </div>
        </div>
        <VSwitch :model-value="enabled" @update:model-value="emit('update:enabled', $event)" />
      </div>

      <template v-if="enabled">
        <label class=":uno: block text-xs text-slate-600">
          类型化调用配置
          <select
            :value="profile"
            class=":uno: mt-1 w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs"
            @change="
              emit('update:profile', ($event.target as HTMLSelectElement).value as AgentProfile)
            "
          >
            <option value="BALANCED">平衡</option>
            <option value="CONCISE">精简回答</option>
            <option value="EXPLICIT">显式说明工具结果</option>
          </select>
        </label>

        <label class=":uno: block text-xs text-slate-600">
          最大模型步骤
          <input
            type="number"
            :value="maxSteps"
            min="1"
            max="100"
            step="1"
            class=":uno: mt-1 w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs"
            @input="emit('update:maxSteps', Number(($event.target as HTMLInputElement).value))"
          />
          <span class=":uno: mt-1 block text-[10px] text-slate-400"
            >默认 20；没有可继续工具时会提前结束</span
          >
        </label>

        <label class=":uno: block text-xs text-slate-600">
          分步工具策略
          <select
            :value="stepPolicy"
            class=":uno: mt-1 w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs"
            @change="
              emit(
                'update:stepPolicy',
                ($event.target as HTMLSelectElement).value as AgentStepPolicy,
              )
            "
          >
            <option value="ALL_TOOLS">每一步开放全部工具</option>
            <option value="SERVER_THEN_ALL">首步仅服务端，随后全部</option>
            <option value="SERVER_THEN_BROWSER">首步服务端，随后浏览器/外部</option>
          </select>
        </label>

        <div class=":uno: rounded-md bg-slate-50 px-2.5 py-2 space-y-2">
          <label class=":uno: flex items-center gap-2 text-xs text-slate-700">
            <VSwitch
              :model-value="serverToolEnabled"
              @update:model-value="emit('update:serverToolEnabled', $event)"
            />
            服务端执行工具
          </label>
          <label class=":uno: flex items-center gap-2 text-xs text-slate-700">
            <VSwitch
              :model-value="browserToolEnabled"
              @update:model-value="emit('update:browserToolEnabled', $event)"
            />
            浏览器完成工具
          </label>
          <label class=":uno: flex items-center gap-2 text-xs text-slate-700">
            <VSwitch
              :model-value="externalToolEnabled"
              @update:model-value="emit('update:externalToolEnabled', $event)"
            />
            手动外部工具
          </label>
          <label class=":uno: flex items-center gap-2 text-xs text-slate-700">
            <VSwitch
              :model-value="approvalRequired"
              :disabled="!serverToolEnabled"
              @update:model-value="emit('update:approvalRequired', $event)"
            />
            服务端工具需要审批
          </label>
          <label class=":uno: flex items-center gap-2 text-xs text-slate-700">
            <VSwitch
              :model-value="toolInputStreamEnabled"
              @update:model-value="emit('update:toolInputStreamEnabled', $event)"
            />
            流式工具入参生命周期
          </label>
        </div>

        <label class=":uno: block text-xs text-slate-600">
          恢复场景
          <select
            :value="recoveryScenario"
            class=":uno: mt-1 w-full text-slate-700 outline-none !border !border-slate-200 !rounded-md !border-solid !bg-white !px-2 !py-1.5 !text-xs"
            @change="
              emit(
                'update:recoveryScenario',
                ($event.target as HTMLSelectElement).value as AgentRecoveryScenario,
              )
            "
          >
            <option value="NONE">不启用恢复</option>
            <option value="INVALID_INPUT">已知工具参数修复</option>
            <option value="RENAMED_TOOL">旧工具名映射到当前工具</option>
            <option value="FAILED_RECOVERY">恢复到不可用目标（失败诊断）</option>
          </select>
          <span class=":uno: mt-1 block text-[10px] text-slate-400">
            旧名称为 halo_legacy_repair_test_info，当前名称为 halo_repair_test_info
          </span>
        </label>
      </template>
    </div>
  </details>
</template>
