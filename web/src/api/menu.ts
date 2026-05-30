import api from './index'

export interface Category {
  id: number
  displayName: string
  sortOrder: number
  colorHex: string
  isActive: boolean
}

export interface Product {
  id: number
  categoryId: number
  name: string
  basePrice: number
  stockWarningThreshold: number
  description: string
  hasModifiers: boolean
  imageUrl: string | null
  isActive: boolean
  sortOrder: number
  currentStock: number
}

export interface ModifierOption {
  id: number
  name: string
  additionalPrice: number
  sizeOverrides: Record<string, number>
  sortOrder: number
}

export interface ModifierGroup {
  id: number
  name: string
  isRequired: boolean
  maxSelection: number
  minSelection: number
  sortOrder: number
  modifiers: ModifierOption[]
}

export interface ProductDetail {
  product: Product
  modifierGroups: ModifierGroup[]
}

export const fetchCategories = () => api.get<Category[]>('/categories')

export const fetchProducts = (categoryId?: number) =>
  api.get<Product[]>('/products', { params: categoryId ? { categoryId } : {} })

export const fetchProductDetail = (id: number) =>
  api.get<ProductDetail>(`/products/${id}`)

export const fetchProductModifiers = (id: number) =>
  api.get<ModifierGroup[]>(`/products/${id}/modifiers`)
