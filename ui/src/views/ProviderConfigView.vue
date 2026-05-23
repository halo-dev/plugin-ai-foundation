<script setup lang="ts">
import ProviderDetail from './ProviderDetail.vue'
import ProviderList from './components/ProviderList.vue'
</script>

<template>
  <div class=":uno: h-[calc(100vh-6.5rem)] flex flex-col sm:flex-row">
    <div class=":uno: h-64 min-h-0 flex-none p-2 sm:h-auto sm:w-72">
      <ProviderList />
    </div>

    <div class=":uno: min-h-0 min-w-0 flex-1 shrink overflow-auto p-2">
      <ProviderDetail />
    </div>
  </div>
</template>
