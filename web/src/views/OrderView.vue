<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMenuStore } from '../stores/menu'
import { useCartStore } from '../stores/cart'
import { ElMessage } from 'element-plus'
import ModifierDialog from '../components/ModifierDialog.vue'
import CartPanel from '../components/CartPanel.vue'
import type { Product, ProductDetail } from '../api/menu'
import type { CartItem, CartModifier } from '../stores/cart'

const menuStore = useMenuStore()
const cart = useCartStore()

const modifierVisible = ref(false)
const currentProductDetail = ref<ProductDetail | null>(null)
const currentProduct = ref<Product | null>(null)

onMounted(async () => {
  await menuStore.loadCategories()
  await menuStore.loadProducts()
})

const handleCategoryClick = (id: number) => {
  menuStore.setActiveCategory(id)
}

const handleProductClick = async (product: Product) => {
  if (product.currentStock === 0) {
    ElMessage.warning('该商品已售罄')
    return
  }
  currentProduct.value = product
  try {
    if (product.hasModifiers) {
      currentProductDetail.value = await menuStore.loadProductDetail(product.id)
      if (currentProductDetail.value.modifierGroups.length > 0) {
        modifierVisible.value = true
      } else {
        addToCart({ modifiers: [], size: '', notes: '' })
      }
    } else {
      addToCart({ modifiers: [], size: '', notes: '' })
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载商品详情失败')
  }
}

const addToCart = (data: { modifiers: CartModifier[], size: string, notes: string }) => {
  if (!currentProduct.value) return
  const p = currentProduct.value
  cart.addItem({
    productId: p.id,
    productName: p.name,
    quantity: 1,
    basePrice: p.basePrice,
    selectedSize: data.size,
    selectedModifiers: data.modifiers,
    notes: data.notes
  })
  ElMessage.success(`${p.name} 已加入购物车`)
}

const getStockTag = (product: Product) => {
  if (product.currentStock === 0) return { text: '售罄', type: 'info' }
  if (product.currentStock > 0 && product.currentStock <= product.stockWarningThreshold) {
    return { text: `仅剩${product.currentStock}`, type: 'warning' }
  }
  return null
}
</script>

<template>
  <div style="display: flex; height: 100%">
    <!-- 左侧分类 -->
    <div style="width: 100px; background: #fafafa; border-right: 1px solid #eee; overflow-y: auto">
      <div
        v-for="cat in menuStore.categories"
        :key="cat.id"
        :style="{
          padding: '16px 12px',
          textAlign: 'center',
          cursor: 'pointer',
          borderLeft: menuStore.activeCategoryId === cat.id ? '3px solid #409EFF' : '3px solid transparent',
          background: menuStore.activeCategoryId === cat.id ? 'white' : 'transparent',
          fontWeight: menuStore.activeCategoryId === cat.id ? 'bold' : 'normal',
          color: menuStore.activeCategoryId === cat.id ? '#409EFF' : '#333'
        }"
        @click="handleCategoryClick(cat.id)"
      >
        {{ cat.displayName }}
      </div>
    </div>

    <!-- 中间商品列表 -->
    <div style="flex: 1; overflow-y: auto; padding: 16px; background: #f5f5f5">
      <el-skeleton v-if="menuStore.loading" :rows="6" animated />
      <div v-else style="display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px">
        <div
          v-for="product in menuStore.products"
          :key="product.id"
          style="background: white; border-radius: 8px; overflow: hidden; cursor: pointer; transition: box-shadow 0.2s"
          @click="handleProductClick(product)"
        >
          <div style="height: 120px; background: #f0f0f0; display: flex; align-items: center; justify-content: center">
            <img
              v-if="product.imageUrl"
              :src="product.imageUrl"
              :alt="product.name"
              style="width: 100%; height: 100%; object-fit: cover"
            />
            <span v-else style="font-size: 40px">🍜</span>
          </div>
          <div style="padding: 10px">
            <div style="font-weight: bold; margin-bottom: 4px; display: flex; justify-content: space-between; align-items: center">
              <span>{{ product.name }}</span>
              <el-tag v-if="getStockTag(product)" :type="getStockTag(product)!.type" size="small">
                {{ getStockTag(product)!.text }}
              </el-tag>
            </div>
            <div style="font-size: 12px; color: #999; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
              {{ product.description }}
            </div>
            <div style="color: #f56c6c; font-weight: bold; font-size: 16px">
              ¥{{ product.basePrice.toFixed(2) }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧购物车 -->
    <div style="width: 320px; border-left: 1px solid #eee">
      <CartPanel />
    </div>

    <!-- 修饰符弹窗 -->
    <ModifierDialog
      v-model:visible="modifierVisible"
      :product-detail="currentProductDetail"
      @confirm="addToCart"
    />
  </div>
</template>
