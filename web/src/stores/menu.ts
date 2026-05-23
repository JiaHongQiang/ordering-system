import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchCategories, fetchProducts, fetchProductDetail } from '../api/menu'
import type { Category, Product, ProductDetail } from '../api/menu'

export const useMenuStore = defineStore('menu', () => {
  const categories = ref<Category[]>([])
  const products = ref<Product[]>([])
  const activeCategoryId = ref<number | null>(null)
  const loading = ref(false)

  const loadCategories = async () => {
    categories.value = await fetchCategories()
    if (categories.value.length > 0 && activeCategoryId.value === null) {
      activeCategoryId.value = categories.value[0].id
    }
  }

  const loadProducts = async (categoryId?: number) => {
    loading.value = true
    try {
      products.value = await fetchProducts(categoryId ?? activeCategoryId.value ?? undefined)
    } finally {
      loading.value = false
    }
  }

  const setActiveCategory = (id: number) => {
    activeCategoryId.value = id
    loadProducts(id)
  }

  const loadProductDetail = async (id: number): Promise<ProductDetail> => {
    return await fetchProductDetail(id)
  }

  return {
    categories, products, activeCategoryId, loading,
    loadCategories, loadProducts, setActiveCategory, loadProductDetail
  }
})
