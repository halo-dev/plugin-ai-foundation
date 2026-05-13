<script setup lang="ts">
import type { AiProvider } from '@/api/generated'
import { useProviderTypes } from '@/composables/useProviderTypes'
import { VButton, VCard, VEmpty, VLoading, VStatusDot, VTag } from '@halo-dev/components'
import { computed, ref } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiSearchLine from '~icons/ri/search-line'

const props = defineProps<{
  providers: AiProvider[]
  loading: boolean
  selectedName?: string
}>()

const emit = defineEmits<{
  (e: 'select', provider: AiProvider): void
  (e: 'edit', provider: AiProvider): void
  (e: 'delete', provider: AiProvider): void
}>()

const { data: providerTypes } = useProviderTypes()
const searchQuery = ref('')

function providerTypeLabel(providerType: string): string {
  const info = providerTypes.value?.find((t) => t.providerType === providerType)
  return info?.displayName || providerType
}

const filteredProviders = computed(() => {
  if (!searchQuery.value) return props.providers
  const q = searchQuery.value.toLowerCase()
  return props.providers.filter(
    (p) =>
      p.spec.displayName.toLowerCase().includes(q) ||
      p.spec.providerType.toLowerCase().includes(q) ||
      providerTypeLabel(p.spec.providerType).toLowerCase().includes(q),
  )
})

function statusPhase(phase?: string) {
  switch (phase) {
    case 'OK':
      return 'success'
    case 'ERROR':
      return 'error'
    default:
      return 'default'
  }
}
</script>

<template>
  <div class="provider-list">
    <div class="provider-list__search">
      <div class="search-input-wrapper">
        <RiSearchLine class="search-icon" />
        <input v-model="searchQuery" type="text" placeholder="搜索供应商..." class="search-input" />
      </div>
    </div>

    <VLoading v-if="loading" />

    <div v-else-if="filteredProviders.length === 0" class="provider-list__empty">
      <VEmpty title="暂无供应商" />
    </div>

    <div v-else class="provider-list__items">
      <VCard
        v-for="provider in filteredProviders"
        :key="provider.metadata.name"
        :class="[
          'provider-card',
          { 'provider-card--active': selectedName === provider.metadata.name },
        ]"
        @click="emit('select', provider)"
      >
        <div class="provider-card__header">
          <div class="provider-card__title">
            <span class="font-medium">{{ provider.spec.displayName }}</span>
            <VTag size="sm">
              {{ providerTypeLabel(provider.spec.providerType) }}
            </VTag>
          </div>
          <div class="provider-card__actions" @click.stop>
            <VButton type="default" size="sm" @click="emit('edit', provider)">
              <RiEditLine />
            </VButton>
            <VButton type="danger" size="sm" @click="emit('delete', provider)">
              <RiDeleteBinLine />
            </VButton>
          </div>
        </div>
        <div class="provider-card__status">
          <VStatusDot :state="statusPhase(provider.status?.phase)" />
          <span class="text-xs text-gray-500">{{ provider.status?.phase || 'UNKNOWN' }}</span>
          <VTag v-if="!provider.spec.enabled" size="sm" style="margin-left: 8px"> 已禁用 </VTag>
        </div>
      </VCard>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.provider-list {
  &__search {
    padding: 12px;
    border-bottom: 1px solid #e5e7eb;
  }

  &__items {
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__empty {
    padding: 32px 16px;
  }
}

.search-input-wrapper {
  position: relative;

  .search-icon {
    position: absolute;
    left: 10px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: #9ca3af;
  }

  .search-input {
    width: 100%;
    padding: 8px 12px 8px 32px;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    font-size: 14px;
    outline: none;

    &:focus {
      border-color: #3b82f6;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
    }
  }
}

.provider-card {
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
  }

  &--active {
    border-color: #3b82f6;
    background: #eff6ff;
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 8px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__actions {
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover &__actions {
    opacity: 1;
  }

  &__status {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}
</style>
