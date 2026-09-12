<template>
  <van-nav-bar
    v-if="showNavBar"
    :title="title"
    :left-arrow="showBack"
    fixed
    placeholder
    safe-area-inset-top
    @click-left="onBack"
  />

  <div class="app-body">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>
  </div>

  <van-tabbar v-if="showTabBar" route fixed placeholder safe-area-inset-bottom>
    <van-tabbar-item v-for="t in tabs" :key="t.pagePath" :to="'/' + t.pagePath">
      {{ t.text }}
    </van-tabbar-item>
  </van-tabbar>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@shared/store/auth'
import pagesJson from './pages.json'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const tabs = pagesJson.tabBar?.list || []

const authPages = ['/pages/login/index', '/pages/register/index']
const showNavBar = computed(() => !authPages.includes(route.path))
const showBack = computed(() => route.meta.isTab !== true)
const showTabBar = computed(() => route.meta.isTab === true)
const title = computed(() => route.meta.title || '共享汽车')

const onBack = () => router.back()

onMounted(async () => {
  if (auth.isLogin && !auth.user) {
    try {
      await auth.fetchCurrent()
    } catch (e) {
      // 401 时 request 层会自动跳登录
    }
  }
})
</script>
