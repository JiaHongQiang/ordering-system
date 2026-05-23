import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createOrder, fetchOrders, fetchOrder, updateOrderStatus } from '../api/order'
import type { OrderResp, CreateOrderRequest } from '../api/order'
import { useCartStore } from './cart'

export const useOrderStore = defineStore('order', () => {
  const orders = ref<OrderResp[]>([])
  const currentOrder = ref<OrderResp | null>(null)
  const loading = ref(false)

  const submitOrder = async (notes: string, createdBy: string) => {
    const cart = useCartStore()
    if (cart.items.length === 0) throw new Error('购物车为空')

    const req: CreateOrderRequest = {
      items: cart.items.map(item => ({
        productId: item.productId,
        productName: item.productName,
        quantity: item.quantity,
        basePrice: item.basePrice,
        selectedSize: item.selectedSize,
        selectedModifiers: item.selectedModifiers,
        notes: item.notes
      })),
      tableNumber: '',
      notes,
      createdBy
    }

    loading.value = true
    try {
      const order = await createOrder(req)
      cart.clearCart()
      return order
    } finally {
      loading.value = false
    }
  }

  const loadOrders = async (status?: string) => {
    loading.value = true
    try {
      orders.value = await fetchOrders(status)
    } finally {
      loading.value = false
    }
  }

  const loadOrder = async (id: number) => {
    currentOrder.value = await fetchOrder(id)
  }

  const changeStatus = async (id: number, status: string) => {
    const updated = await updateOrderStatus(id, status)
    const idx = orders.value.findIndex(o => o.id === id)
    if (idx >= 0) orders.value[idx] = updated
    if (currentOrder.value?.id === id) currentOrder.value = updated
  }

  return {
    orders, currentOrder, loading,
    submitOrder, loadOrders, loadOrder, changeStatus
  }
})
