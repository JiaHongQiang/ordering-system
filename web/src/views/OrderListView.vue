<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useOrderStore } from '../stores/order'
import { ElMessage, ElMessageBox } from 'element-plus'
import { printOrder } from '../api/order'
import type { OrderResp } from '../api/order'

const orderStore = useOrderStore()
const activeTab = ref('all')

const statusMap: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待支付', color: '#E6A23C' },
  PAID: { label: '已支付', color: '#409EFF' },
  PREPARING: { label: '制作中', color: '#F56C6C' },
  READY: { label: '待取餐', color: '#67C23A' },
  COMPLETED: { label: '已完成', color: '#909399' },
  CANCELLED: { label: '已取消', color: '#909399' }
}

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'PENDING', label: '待支付' },
  { key: 'PAID', label: '已支付' },
  { key: 'PREPARING', label: '制作中' },
  { key: 'READY', label: '待取餐' },
  { key: 'COMPLETED', label: '已完成' }
]

const filteredOrders = computed(() => {
  if (activeTab.value === 'all') return orderStore.orders
  return orderStore.orders.filter(o => o.status === activeTab.value)
})

onMounted(() => {
  orderStore.loadOrders()
})

const handleTabChange = (tab: string) => {
  activeTab.value = tab
  orderStore.loadOrders(tab === 'all' ? undefined : tab)
}

const getNextStatus = (status: string): string | null => {
  const flow: Record<string, string> = {
    PENDING: 'PAID',
    PAID: 'PREPARING',
    PREPARING: 'READY',
    READY: 'COMPLETED'
  }
  return flow[status] || null
}

const getNextStatusLabel = (status: string): string | null => {
  const next = getNextStatus(status)
  return next ? statusMap[next]?.label : null
}

const handleAdvanceStatus = async (order: OrderResp) => {
  const next = getNextStatus(order.status)
  if (!next) return
  try {
    await ElMessageBox.confirm(
      `确认将 ${order.orderNumber} 状态改为"${statusMap[next].label}"？`,
      '状态变更'
    )
    await orderStore.changeStatus(order.id, next)
    ElMessage.success('状态已更新')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

const handleCancel = async (order: OrderResp) => {
  try {
    await ElMessageBox.confirm(`确认取消 ${order.orderNumber}？`, '取消订单', { type: 'warning' })
    await orderStore.changeStatus(order.id, 'CANCELLED')
    ElMessage.success('订单已取消')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

const handlePrint = async (order: OrderResp) => {
  try {
    await printOrder(order.id)
    ElMessage.success('打印指令已发送')
  } catch (e: any) {
    ElMessage.error(e.message || '打印失败')
  }
}

const formatTime = (ts: number) => {
  return new Date(ts).toLocaleString('zh-CN')
}
</script>

<template>
  <div style="height: 100%; display: flex; flex-direction: column">
    <div style="padding: 16px 20px; background: white; border-bottom: 1px solid #eee">
      <el-tabs :model-value="activeTab" @update:model-value="handleTabChange">
        <el-tab-pane v-for="tab in statusTabs" :key="tab.key" :label="tab.label" :name="tab.key" />
      </el-tabs>
    </div>

    <div style="flex: 1; overflow-y: auto; padding: 16px 20px; background: #f5f5f5">
      <el-skeleton v-if="orderStore.loading" :rows="4" animated />
      <el-empty v-else-if="filteredOrders.length === 0" description="暂无订单" />

      <div v-else style="display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px">
        <el-card v-for="order in filteredOrders" :key="order.id" shadow="hover" body-style="display: flex; flex-direction: column; height: 100%">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-size: 24px; font-weight: bold; letter-spacing: 2px">{{ order.orderNumber }}</span>
              <el-tag :color="statusMap[order.status]?.color" effect="dark" size="small">
                {{ statusMap[order.status]?.label }}
              </el-tag>
            </div>
          </template>

          <div style="flex: 1">
            <div style="margin-bottom: 12px">
              <div style="color: #999; font-size: 13px">{{ formatTime(order.createdAt) }}</div>
              <div v-if="order.notes" style="color: #e6a23c; font-size: 13px; margin-top: 4px">
                备注: {{ order.notes }}
              </div>
            </div>

            <div style="display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px">
              <span>共 {{ order.itemCount }} 件</span>
              <span style="font-size: 20px; color: #f56c6c; font-weight: bold">
                ¥{{ order.totalAmount.toFixed(2) }}
              </span>
            </div>
          </div>

          <div style="display: flex; gap: 8px; padding-top: 12px; border-top: 1px solid #f0f0f0">
            <el-button
              v-if="getNextStatus(order.status)"
              type="primary"
              size="small"
              @click="handleAdvanceStatus(order)"
            >
              {{ getNextStatusLabel(order.status) }}
            </el-button>
            <el-button
              v-if="order.status !== 'COMPLETED' && order.status !== 'CANCELLED'"
              size="small"
              @click="handlePrint(order)"
            >
              打印
            </el-button>
            <el-button
              v-if="order.status !== 'COMPLETED' && order.status !== 'CANCELLED'"
              type="danger"
              text
              size="small"
              @click="handleCancel(order)"
            >
              取消
            </el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>
