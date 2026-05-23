<script setup lang="ts">
import { ref } from 'vue'
import { useCartStore } from '../stores/cart'
import { useOrderStore } from '../stores/order'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { OrderResp } from '../api/order'

const cart = useCartStore()
const orderStore = useOrderStore()

const lastOrder = ref<OrderResp | null>(null)
const showSuccess = ref(false)

const handleSubmit = async () => {
  if (cart.items.length === 0) {
    ElMessage.warning('请先添加商品')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认提交订单？共 ${cart.itemCount} 件，合计 ¥${cart.totalPrice.toFixed(2)}`,
      '确认下单'
    )
    const order = await orderStore.submitOrder(cart.orderNotes, '收银员')
    lastOrder.value = order
    showSuccess.value = true
    ElMessage.success('下单成功！')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '下单失败')
    }
  }
}

const handleNewOrder = () => {
  showSuccess.value = false
  lastOrder.value = null
}
</script>

<template>
  <div style="display: flex; flex-direction: column; height: 100%; background: #f5f5f5">
    <!-- 下单成功：显示取餐号 -->
    <div v-if="showSuccess && lastOrder" style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; background: white; padding: 40px">
      <div style="font-size: 16px; color: #999; margin-bottom: 16px">取餐号</div>
      <div style="font-size: 72px; font-weight: bold; color: #409EFF; letter-spacing: 8px; margin-bottom: 8px">
        {{ lastOrder.orderNumber }}
      </div>
      <div style="font-size: 14px; color: #999; margin-bottom: 32px">
        请留意叫号，凭此号码取餐
      </div>
      <div style="font-size: 24px; color: #f56c6c; font-weight: bold; margin-bottom: 32px">
        ¥{{ lastOrder.totalAmount.toFixed(2) }}
      </div>
      <el-button type="primary" size="large" @click="handleNewOrder">
        继续点餐
      </el-button>
    </div>

    <!-- 购物车 -->
    <template v-else>
      <div style="padding: 16px; background: white; border-bottom: 1px solid #eee">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
          <span style="font-size: 18px; font-weight: bold">购物车</span>
          <el-button
            v-if="cart.items.length > 0"
            type="danger"
            text
            size="small"
            @click="cart.clearCart()"
          >清空</el-button>
        </div>
        <el-input v-model="cart.orderNotes" placeholder="备注（如：不要香菜、少放盐）" size="small" />
      </div>

      <div style="flex: 1; overflow-y: auto; padding: 8px">
        <el-empty v-if="cart.items.length === 0" description="还未添加商品" :image-size="80" />

        <div
          v-for="(item, index) in cart.items"
          :key="index"
          style="background: white; border-radius: 8px; padding: 12px; margin-bottom: 8px"
        >
          <div style="display: flex; justify-content: space-between; align-items: flex-start">
            <div>
              <div style="font-weight: bold">{{ item.productName }}</div>
              <div v-if="item.selectedSize" style="font-size: 12px; color: #999">规格: {{ item.selectedSize }}</div>
              <div
                v-for="mod in item.selectedModifiers"
                :key="mod.modifierId"
                style="font-size: 12px; color: #666"
              >
                {{ mod.groupName }}: {{ mod.modifierName }}
                <span v-if="mod.quantity > 1" style="font-weight: bold"> x{{ mod.quantity }}</span>
                <span v-if="mod.additionalPrice > 0" style="color: #f56c6c"> +¥{{ (mod.additionalPrice * mod.quantity).toFixed(2) }}</span>
              </div>
              <div v-if="item.notes" style="font-size: 12px; color: #e6a23c">备注: {{ item.notes }}</div>
            </div>
            <div style="text-align: right">
              <div style="color: #f56c6c; font-weight: bold">
                ¥{{ ((item.basePrice + item.selectedModifiers.reduce((s, m) => s + m.additionalPrice * m.quantity, 0)) * item.quantity).toFixed(2) }}
              </div>
            </div>
          </div>
          <div style="display: flex; justify-content: flex-end; align-items: center; margin-top: 8px">
            <el-input-number
              :model-value="item.quantity"
              @update:model-value="cart.updateQuantity(index, $event)"
              :min="0"
              :max="99"
              size="small"
            />
          </div>
        </div>
      </div>

      <div style="padding: 16px; background: white; border-top: 1px solid #eee">
        <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
          <span>共 {{ cart.itemCount }} 件</span>
          <span style="font-size: 20px; color: #f56c6c; font-weight: bold">
            ¥{{ cart.totalPrice.toFixed(2) }}
          </span>
        </div>
        <el-button
          type="primary"
          style="width: 100%"
          size="large"
          :loading="orderStore.loading"
          :disabled="cart.items.length === 0"
          @click="handleSubmit"
        >
          提交订单
        </el-button>
      </div>
    </template>
  </div>
</template>
