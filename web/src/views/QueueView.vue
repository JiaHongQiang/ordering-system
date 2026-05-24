<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { fetchOrders } from '../api/order'
import type { OrderResp } from '../api/order'

const readyOrders = ref<OrderResp[]>([])
const preparingOrders = ref<OrderResp[]>([])
let timer: ReturnType<typeof setInterval> | null = null

const loadQueue = async () => {
  try {
    const all = await fetchOrders()
    readyOrders.value = all.filter(o => o.status === 'READY')
    preparingOrders.value = all.filter(o => o.status === 'PREPARING' || o.status === 'PENDING')
  } catch (_: any) {
    // ignore
  }
}

onMounted(() => {
  loadQueue()
  timer = setInterval(loadQueue, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const formatTime = (ts: number) => {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div style="height: 100%; overflow-y: auto; background: #1a1a2e; color: white; padding: 24px">
    <!-- 待取餐 -->
    <div style="margin-bottom: 32px">
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 20px">
        <div style="width: 8px; height: 32px; background: #67C23A; border-radius: 4px"></div>
        <h1 style="margin: 0; font-size: 28px">请取餐</h1>
      </div>

      <el-empty v-if="readyOrders.length === 0" description="暂无待取餐订单" style="color: #999" />

      <div v-else style="display: flex; flex-wrap: wrap; gap: 16px">
        <div
          v-for="order in readyOrders"
          :key="order.id"
          style="background: #67C23A; border-radius: 16px; padding: 24px 32px; min-width: 140px; text-align: center; animation: pulse 2s infinite"
        >
          <div style="font-size: 48px; font-weight: bold; letter-spacing: 4px">
            {{ order.orderNumber }}
          </div>
          <div style="font-size: 14px; opacity: 0.8; margin-top: 4px">
            {{ formatTime(order.createdAt) }}
          </div>
        </div>
      </div>
    </div>

    <!-- 制作中 -->
    <div>
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 20px">
        <div style="width: 8px; height: 32px; background: #E6A23C; border-radius: 4px"></div>
        <h1 style="margin: 0; font-size: 28px; color: #ccc">制作中</h1>
      </div>

      <el-empty v-if="preparingOrders.length === 0" description="暂无制作中订单" style="color: #999" />

      <div v-else style="display: flex; flex-wrap: wrap; gap: 16px">
        <div
          v-for="order in preparingOrders"
          :key="order.id"
          style="background: #333; border-radius: 16px; padding: 24px 32px; min-width: 140px; text-align: center"
        >
          <div style="font-size: 48px; font-weight: bold; letter-spacing: 4px; color: #E6A23C">
            {{ order.orderNumber }}
          </div>
          <div style="font-size: 14px; color: #999; margin-top: 4px">
            {{ formatTime(order.createdAt) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}
</style>
