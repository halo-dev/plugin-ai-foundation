<script setup lang="ts">
import ProviderDetail from './ProviderDetail.vue'
import ProviderList from './components/ProviderList.vue'
</script>

<template>
  <div
    class=":uno: grid grid-cols-1 h-[calc(100vh-8.25rem)] min-h-[34rem] gap-3 p-2 sm:grid-cols-[20rem_minmax(0,1fr)]"
  >
    <div class=":uno: h-72 min-h-0 sm:h-auto">
      <ProviderList />
    </div>

    <div class=":uno: min-h-0 min-w-0 overflow-auto">
      <ProviderDetail />
    </div>
  </div>
</template>
