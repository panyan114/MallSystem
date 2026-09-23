<template>
  <AuthLayout v-if="usesAuthLayout">
    <router-view />
  </AuthLayout>
  <MainLayout v-else-if="!usesAdminLayout">
    <router-view />
  </MainLayout>
  <router-view v-else />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import MainLayout from '@/components/MainLayout.vue'

const route = useRoute()
const usesAuthLayout = computed(() => ['/login', '/register'].includes(route.path))
const usesAdminLayout = computed(() => route.path.startsWith('/admin'))
</script>

<style>
#app {
  min-height: 100vh;
}
</style>
