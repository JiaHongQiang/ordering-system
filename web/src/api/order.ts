import api from './index'

export interface SelectedModifierReq {
  groupId: number
  groupName: string
  modifierId: number
  modifierName: string
  additionalPrice: number
  quantity: number
}

export interface CartItemReq {
  productId: number
  productName: string
  quantity: number
  basePrice: number
  selectedSize: string
  selectedModifiers: SelectedModifierReq[]
  notes: string
}

export interface CreateOrderRequest {
  items: CartItemReq[]
  tableNumber: string
  notes: string
  createdBy: string
}

export interface OrderItemResp {
  id: number
  productId: number
  productName: string
  quantity: number
  priceAtSnap: number
  modifierTotal: number
  lineTotal: number
  appliedModifiersJson: string
  selectedSize: string
  notes: string
}

export interface OrderResp {
  id: number
  orderNumber: string
  tableNumber: string
  status: string
  totalAmount: number
  itemCount: number
  paymentMethod: string
  notes: string
  createdBy: string
  createdAt: number
  completedAt: number | null
  items: OrderItemResp[]
}

export interface TableInfo {
  number: string
  occupied: boolean
  orderId: number | null
}

export const createOrder = (req: CreateOrderRequest) =>
  api.post<OrderResp>('/orders', req)

export const fetchOrders = (status?: string) =>
  api.get<OrderResp[]>('/orders', { params: status ? { status } : {} })

export const fetchOrder = (id: number) =>
  api.get<OrderResp>(`/orders/${id}`)

export const updateOrderStatus = (id: number, status: string) =>
  api.patch<OrderResp>(`/orders/${id}/status`, { status })

export const printOrder = (id: number) =>
  api.post(`/orders/${id}/print`)

export const fetchTables = () =>
  api.get<TableInfo[]>('/tables')

export const fetchTableOrder = (number: string) =>
  api.get(`/tables/${number}/order`)
