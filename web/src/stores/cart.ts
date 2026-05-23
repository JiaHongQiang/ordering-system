import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface CartModifier {
  groupId: number
  groupName: string
  modifierId: number
  modifierName: string
  additionalPrice: number
  quantity: number
  sizeOverrides: Record<string, number>
}

export interface CartItem {
  productId: number
  productName: string
  quantity: number
  basePrice: number
  selectedSize: string
  selectedModifiers: CartModifier[]
  notes: string
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const orderNotes = ref('')

  const itemCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))

  const totalPrice = computed(() => {
    return items.value.reduce((sum, item) => {
      const modifierPrice = item.selectedModifiers.reduce((ms, m) => ms + m.additionalPrice * m.quantity, 0)
      return sum + (item.basePrice + modifierPrice) * item.quantity
    }, 0)
  })

  const addItem = (item: CartItem) => {
    const key = makeKey(item)
    const existing = items.value.findIndex(i => makeKey(i) === key)
    if (existing >= 0) {
      items.value[existing].quantity += item.quantity
    } else {
      items.value.push({ ...item })
    }
  }

  const removeItem = (index: number) => {
    items.value.splice(index, 1)
  }

  const updateQuantity = (index: number, quantity: number) => {
    if (quantity <= 0) {
      removeItem(index)
    } else {
      items.value[index].quantity = quantity
    }
  }

  const clearCart = () => {
    items.value = []
    orderNotes.value = ''
  }

  function makeKey(item: CartItem): string {
    const modKey = item.selectedModifiers
      .map(m => `${m.modifierId}x${m.quantity}`)
      .sort()
      .join(',')
    return `${item.productId}:${item.selectedSize}:${modKey}`
  }

  return {
    items, orderNotes, itemCount, totalPrice,
    addItem, removeItem, updateQuantity, clearCart
  }
})
