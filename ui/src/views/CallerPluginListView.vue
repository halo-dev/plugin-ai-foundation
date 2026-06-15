<script setup lang="ts">
import type { CallerPluginInfo } from '@/api/generated'
import { useCallerPluginsFetch } from '@/composables/use-caller-plugins-fetch'
import {
  IconRefreshLine,
  VCard,
  VEmpty,
  VEntityContainer,
  VLoading,
  VSpace,
} from '@halo-dev/components'
import { computed } from 'vue'
import MingcuteExternalLinkLine from '~icons/mingcute/external-link-line'

const { data, isLoading, isFetching, refetch } = useCallerPluginsFetch()

const callers = computed(() => data.value || [])
const callerCountText = computed(() => {
  const count = callers.value.length
  return count ? `已观察到 ${count} 个调用插件` : '尚未观察到插件调用'
})

function displayName(caller: CallerPluginInfo) {
  return caller.displayName || caller.pluginName || '未知插件'
}

function metaText(caller: CallerPluginInfo) {
  return [caller.pluginName, caller.version ? `v${caller.version}` : undefined]
    .filter(Boolean)
    .join(' · ')
}

function linkItems(caller: CallerPluginInfo) {
  return [
    { label: '主页', url: caller.homepage },
    { label: '仓库', url: caller.repo },
    { label: '问题', url: caller.issues },
  ].filter((item): item is { label: string; url: string } => !!item.url)
}
</script>

<template>
  <div class=":uno: p-2">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: w-full flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
          <div class=":uno: min-w-0 flex-1">
            <div class=":uno: text-sm text-gray-950 font-semibold">SDK 调用插件</div>
            <div class=":uno: text-xs text-gray-500">{{ callerCountText }}</div>
          </div>
          <VSpace spacing="lg" class=":uno: flex-wrap sm:ml-auto">
            <button
              type="button"
              class=":uno: group size-9 inline-flex cursor-pointer items-center justify-center border border-gray-200 rounded-md bg-white hover:bg-gray-50"
              @click="refetch()"
              v-tooltip="`刷新`"
            >
              <IconRefreshLine
                :class="{ ':uno: animate-spin text-gray-900': isFetching }"
                class=":uno: size-4 text-gray-600 group-hover:text-gray-900"
              />
            </button>
          </VSpace>
        </div>
      </template>

      <VLoading v-if="isLoading" />

      <Transition v-else-if="!callers.length" appear name="fade">
        <VEmpty
          title="暂无调用记录"
          message="触发其他插件调用 AI Foundation SDK 后，这里会显示调用方插件信息"
        />
      </Transition>

      <Transition v-else appear name="fade">
        <VEntityContainer>
          <div
            v-for="caller in callers"
            :key="caller.pluginName || displayName(caller)"
            class=":uno: flex flex-col gap-3 border-b border-gray-100 px-4 py-4 lg:flex-row lg:items-center last:border-b-0"
          >
            <div class=":uno: min-w-0 flex-1">
              <div class=":uno: flex flex-wrap items-center gap-2">
                <span class=":uno: truncate text-sm text-gray-950 font-semibold">
                  {{ displayName(caller) }}
                </span>
                <span
                  class=":uno: h-6 inline-flex items-center rounded-md bg-emerald-50 px-2 text-xs text-emerald-700 font-medium"
                >
                  {{ caller.detectionSource || 'auto' }}
                </span>
              </div>
              <div class=":uno: mt-1 text-xs text-gray-500">{{ metaText(caller) }}</div>
              <div v-if="caller.description" class=":uno: mt-2 text-sm text-gray-600">
                {{ caller.description }}
              </div>
              <div v-if="caller.authorName" class=":uno: mt-2 text-xs text-gray-500">
                作者：{{ caller.authorName }}
                <span v-if="caller.authorWebsite"> · {{ caller.authorWebsite }}</span>
              </div>
            </div>
            <div v-if="linkItems(caller).length" class=":uno: flex flex-wrap gap-2 lg:justify-end">
              <a
                v-for="item in linkItems(caller)"
                :key="item.label"
                :href="item.url"
                target="_blank"
                rel="noreferrer"
                class=":uno: h-8 inline-flex items-center gap-1 border border-gray-200 rounded-md bg-white px-3 text-xs text-gray-700 hover:border-gray-300 hover:bg-gray-50"
              >
                <span>{{ item.label }}</span>
                <MingcuteExternalLinkLine class=":uno: size-3.5" />
              </a>
            </div>
          </div>
        </VEntityContainer>
      </Transition>

      <template #footer>
        <div class=":uno: min-h-9 flex items-center px-1">
          <span class=":uno: text-sm text-gray-500">{{ callerCountText }}</span>
        </div>
      </template>
    </VCard>
  </div>
</template>
