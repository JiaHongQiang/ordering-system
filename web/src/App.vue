<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from './api/index'

const router = useRouter()
const route = useRoute()
const storeName = ref('点餐系统')

onMounted(async () => {
  try {
    const settings = await api.get('/settings') as any
    storeName.value = settings.storeName || '点餐系统'
    document.title = storeName.value ? storeName.value + ' - 点餐系统' : '点餐系统'
  } catch (_: any) {
    // ignore
  }
})

const navItems = [
  { path: '/', label: '点餐', icon: '🍜' },
  { path: '/orders', label: '订单', icon: '📋' },
  { path: '/queue', label: '叫号', icon: '🔔' },
  { path: '/admin', label: '后台', icon: '⚙' }
]
</script>

<template>
  <el-container style="height: 100vh">
    <el-header style="display: flex; align-items: center; padding: 0 20px; background: #409EFF; color: white">
      <span style="font-size: 20px; font-weight: bold; margin-right: 40px">{{ storeName }}</span>
      <el-menu
        :default-active="route.path"
        mode="horizontal"
        :ellipsis="false"
        background-color="#409EFF"
        text-color="#fff"
        active-text-color="#ffd04b"
        @select="(path: string) => router.push(path)"
        style="border: none; flex: 1"
      >
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          {{ item.icon }} {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-header>
    <el-main style="padding: 0; overflow: hidden">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
</style>
