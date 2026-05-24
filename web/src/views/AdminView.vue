<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete } from '@element-plus/icons-vue'
import { fetchOrders, updateOrderStatus, printOrder } from '../api/order'
import type { OrderResp } from '../api/order'
import api from '../api/index'

const activeTab = ref('orders')

// ==================== 订单 ====================
const orders = ref<OrderResp[]>([])
const orderFilter = ref('')
const dateFilter = ref('today')
const loading = ref(false)
const orderDetailVisible = ref(false)
const orderDetail = ref<OrderResp | null>(null)

const statusMap: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: '#E6A23C' },
  PREPARING: { label: '制作中', color: '#F56C6C' },
  READY: { label: '待取餐', color: '#67C23A' },
  COMPLETED: { label: '已完成', color: '#909399' },
  CANCELLED: { label: '已取消', color: '#909399' }
}

const filteredOrders = computed(() => {
  let list = orders.value
  // 日期筛选
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const dayOfWeek = now.getDay() || 7
  const weekStart = todayStart - (dayOfWeek - 1) * 86400000
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  if (dateFilter.value === 'today') list = list.filter(o => o.createdAt >= todayStart)
  else if (dateFilter.value === 'week') list = list.filter(o => o.createdAt >= weekStart)
  else if (dateFilter.value === 'month') list = list.filter(o => o.createdAt >= monthStart)
  // 状态筛选
  if (orderFilter.value) list = list.filter(o => o.status === orderFilter.value)
  return list
})

const orderStats = computed(() => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const todayOrders = orders.value.filter(o => o.createdAt >= todayStart)
  const activeStatuses = ['PENDING', 'PREPARING', 'READY']
  const completedToday = todayOrders.filter(o => o.status === 'COMPLETED')
  return {
    todayCount: todayOrders.length,
    todayRevenue: todayOrders.filter(o => o.status !== 'CANCELLED').reduce((s, o) => s + o.totalAmount, 0),
    activeCount: orders.value.filter(o => activeStatuses.includes(o.status)).length,
    avgAmount: todayOrders.length > 0 ? todayOrders.filter(o => o.status !== 'CANCELLED').reduce((s, o) => s + o.totalAmount, 0) / Math.max(todayOrders.filter(o => o.status !== 'CANCELLED').length, 1) : 0
  }
})

const loadOrders = async () => { loading.value = true; try { orders.value = await fetchOrders() } finally { loading.value = false } }

const viewOrderDetail = async (order: OrderResp) => {
  try {
    orderDetail.value = await api.get(`/orders/${order.id}`)
    orderDetailVisible.value = true
  } catch (e: any) { ElMessage.error(e.message) }
}

const parseMods = (json: string) => {
  try { return JSON.parse(json) } catch (_) { return [] }
}

const handleAdvance = async (order: OrderResp) => {
  const flow: Record<string, string> = { PENDING: 'PREPARING', PREPARING: 'READY', READY: 'COMPLETED' }
  const next = flow[order.status]; if (!next) return
  await updateOrderStatus(order.id, next); ElMessage.success(`${order.orderNumber} → ${statusMap[next].label}`); loadOrders()
}

const handleCancel = async (order: OrderResp) => {
  await ElMessageBox.confirm(`取消 ${order.orderNumber}？`, '确认'); await updateOrderStatus(order.id, 'CANCELLED'); loadOrders()
}

const handlePrint = async (order: OrderResp) => {
  try { await printOrder(order.id); ElMessage.success('已发送') } catch (e: any) { ElMessage.error(e.message) }
}

const formatTime = (ts: number) => new Date(ts).toLocaleString('zh-CN')

// ==================== 数据类型 ====================
interface Category { id: number; displayName: string; sortOrder: number; colorHex: string; isActive: boolean }
interface Product { id: number; categoryId: number; name: string; basePrice: number; description: string; imageUrl: string | null; isActive: boolean; hasModifiers: boolean; sortOrder: number; currentStock: number }
interface ModGroup { id: number; name: string; isRequired: boolean; isDefault: boolean; maxSelection: number; minSelection: number; sortOrder: number; isActive: boolean; productIds: number[]; modifiers: ModItem[] }
interface ModItem { id: number; groupId: number; name: string; additionalPrice: number; sortOrder: number; isActive: boolean }

// ==================== 数据加载 ====================
const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const modGroups = ref<ModGroup[]>([])
const selectedCatId = ref<number | null>(null)
const selectedProductId = ref<number | null>(null)

const loadAll = async () => {
  categories.value = await api.get('/admin/categories')
  products.value = await api.get('/products', { params: { all: 'true' } })
  modGroups.value = await api.get('/modifier-groups', { params: { all: 'true' } })
  if (categories.value.length > 0 && selectedCatId.value === null) selectedCatId.value = categories.value[0].id
}

const filteredProducts = computed(() => selectedCatId.value ? products.value.filter(p => p.categoryId === selectedCatId.value) : products.value)
const getCatName = (id: number) => categories.value.find(c => c.id === id)?.displayName || ''
const selectedProduct = computed(() => products.value.find(p => p.id === selectedProductId.value))
const productModGroups = computed(() => modGroups.value.filter(g => g.productIds.includes(selectedProductId.value || -1)))
const availableGroups = computed(() => modGroups.value.filter(g => g.isActive && !g.productIds.includes(selectedProductId.value || -1)))

// ==================== 分类操作 ====================
const catManageVisible = ref(false)
const catDialogVisible = ref(false)
const catIsEdit = ref(false)
const catForm = ref({ id: 0, displayName: '', colorHex: '#FF6B35', sortOrder: 0, isActive: true })

const openAddCategory = () => { catIsEdit.value = false; catForm.value = { id: 0, displayName: '', colorHex: '#FF6B35', sortOrder: categories.value.length, isActive: true }; catDialogVisible.value = true }
const openEditCategory = (c: Category) => { catIsEdit.value = true; catForm.value = { ...c }; catDialogVisible.value = true }

const saveCategory = async () => {
  if (!catForm.value.displayName) { ElMessage.warning('请填写分类名'); return }
  try {
    if (catIsEdit.value) await api.put(`/categories/${catForm.value.id}`, catForm.value)
    else await api.post('/categories', catForm.value)
    ElMessage.success('保存成功'); catDialogVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message) }
}

const deleteCategory = async (c: Category) => {
  await ElMessageBox.confirm(`删除「${c.displayName}」？`, '确认', { type: 'warning' })
  await api.delete(`/categories/${c.id}`); ElMessage.success('已删除'); loadAll()
}

// ==================== 商品操作 ====================
const productDialogVisible = ref(false)
const productIsEdit = ref(false)
const productForm = ref({ id: 0, categoryId: 0, name: '', basePrice: 0, description: '', imageUrl: '', isActive: true, hasModifiers: true, sortOrder: 0 })

const openAddProduct = () => {
  productIsEdit.value = false
  productForm.value = { id: 0, categoryId: selectedCatId.value || 0, name: '', basePrice: 0, description: '', imageUrl: '', isActive: true, hasModifiers: true, sortOrder: 0 }
  productDialogVisible.value = true
}

const openEditProduct = (p: Product) => {
  productIsEdit.value = true
  productForm.value = { id: p.id, categoryId: p.categoryId, name: p.name, basePrice: p.basePrice, description: p.description, imageUrl: p.imageUrl || '', isActive: p.isActive, hasModifiers: p.hasModifiers, sortOrder: p.sortOrder }
  productDialogVisible.value = true
}

const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) ElMessage.error('只能上传图片')
  if (!isLt2M) ElMessage.error('图片不能超过 2MB')
  return isImage && isLt2M
}

const handleUploadSuccess = (res: any) => {
  productForm.value.imageUrl = res.url
  ElMessage.success('上传成功')
}

const saveProduct = async () => {
  const f = productForm.value
  if (!f.name || !f.basePrice) { ElMessage.warning('请填写名称和价格'); return }
  try {
    if (productIsEdit.value) await api.put(`/products/${f.id}`, f)
    else await api.post('/products', f)
    ElMessage.success('保存成功'); productDialogVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message) }
}

const toggleProduct = async (p: Product) => {
  const res = await api.patch(`/products/${p.id}/toggle`) as any; p.isActive = res.isActive
  ElMessage.success(res.isActive ? '已上架' : '已下架')
}

const deleteProduct = async (p: Product) => {
  await ElMessageBox.confirm(`删除「${p.name}」？`, '确认', { type: 'warning' })
  await api.delete(`/products/${p.id}`); ElMessage.success('已删除'); if (selectedProductId.value === p.id) selectedProductId.value = null; loadAll()
}

// ==================== 小料组操作 ====================
const groupDialogVisible = ref(false)
const groupIsEdit = ref(false)
const groupForm = ref({ id: 0, name: '', isRequired: false, isDefault: false, maxSelection: 0, minSelection: 0, sortOrder: 0, isActive: true, productIds: [] as number[] })

const openAddGroup = () => {
  groupIsEdit.value = false
  groupForm.value = { id: 0, name: '', isRequired: false, isDefault: false, maxSelection: 0, minSelection: 0, sortOrder: modGroups.value.length, isActive: true, productIds: selectedProductId.value ? [selectedProductId.value] : [] }
  groupDialogVisible.value = true
}

const openEditGroup = (g: ModGroup) => {
  groupIsEdit.value = true
  groupForm.value = { id: g.id, name: g.name, isRequired: g.isRequired, isDefault: g.isDefault, maxSelection: g.maxSelection, minSelection: g.minSelection, sortOrder: g.sortOrder, isActive: g.isActive, productIds: [...g.productIds] }
  groupDialogVisible.value = true
}

const saveGroup = async () => {
  if (!groupForm.value.name) { ElMessage.warning('请填写名称'); return }
  try {
    if (groupIsEdit.value) await api.put(`/modifier-groups/${groupForm.value.id}`, groupForm.value)
    else await api.post('/modifier-groups', groupForm.value)
    ElMessage.success('保存成功'); groupDialogVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message) }
}

const linkGroupToProduct = async (groupId: number) => {
  if (!selectedProductId.value) return
  const g = modGroups.value.find(g => g.id === groupId)
  if (!g) return
  const newProductIds = [...g.productIds, selectedProductId.value]
  await api.put(`/modifier-groups/${groupId}`, { ...g, productIds: newProductIds })
  ElMessage.success(`已关联「${g.name}」`)
  loadAll()
}

const unlinkGroup = async (g: ModGroup) => {
  if (!selectedProductId.value) return
  await ElMessageBox.confirm(`确认从当前商品移除「${g.name}」？`, '移除关联')
  await api.delete(`/products/${selectedProductId.value}/modifier-groups/${g.id}`)
  ElMessage.success('已移除'); loadAll()
}

const deleteGroup = async (g: ModGroup) => {
  await ElMessageBox.confirm(`删除「${g.name}」会同时删除所有小料和商品关联，确认？`, '删除', { type: 'warning' })
  await api.delete(`/modifier-groups/${g.id}`); ElMessage.success('已删除'); loadAll()
}

// ==================== 小料项操作 ====================
const modDialogVisible = ref(false)
const modIsEdit = ref(false)
const modForm = ref({ id: 0, groupId: 0, name: '', additionalPrice: 0, sortOrder: 0, isActive: true })

const openAddMod = (groupId: number) => {
  modIsEdit.value = false
  const group = modGroups.value.find(g => g.id === groupId)
  modForm.value = { id: 0, groupId, name: '', additionalPrice: 0, sortOrder: group?.modifiers.length || 0, isActive: true }
  modDialogVisible.value = true
}

const openEditMod = (m: ModItem) => { modIsEdit.value = true; modForm.value = { ...m }; modDialogVisible.value = true }

const saveMod = async () => {
  if (!modForm.value.name) { ElMessage.warning('请填写名称'); return }
  try {
    if (modIsEdit.value) await api.put(`/modifiers/${modForm.value.id}`, modForm.value)
    else await api.post('/modifiers', modForm.value)
    ElMessage.success('保存成功'); modDialogVisible.value = false; loadAll()
  } catch (e: any) { ElMessage.error(e.message) }
}

const deleteMod = async (m: ModItem) => {
  await ElMessageBox.confirm(`删除「${m.name}」？`, '确认', { type: 'warning' })
  await api.delete(`/modifiers/${m.id}`); ElMessage.success('已删除'); loadAll()
}

const getSelectionLabel = (g: ModGroup) => {
  if (g.maxSelection === 1) return '单选'
  if (g.maxSelection === 0) return '不限'
  return `最多${g.maxSelection}项`
}

// ==================== 店铺设置 ====================
const settingsForm = ref({ storeName: '', storePhone: '', storeTagline: '', receiptFooter: '' })

const loadSettings = async () => {
  try {
    const s = await api.get('/settings') as any
    settingsForm.value = { storeName: s.storeName || '', storePhone: s.storePhone || '', storeTagline: s.storeTagline || '', receiptFooter: s.receiptFooter || '' }
  } catch (_: any) {}
}

const saveSettings = async () => {
  try {
    await api.put('/settings', settingsForm.value)
    ElMessage.success('保存成功')
  } catch (e: any) { ElMessage.error(e.message) }
}

onMounted(() => { loadOrders(); loadAll(); loadSettings() })
</script>

<template>
  <div style="height: 100%; display: flex; flex-direction: column; background: #f0f2f5">
    <div style="padding: 12px 24px; background: white; border-bottom: 1px solid #eee">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="订单管理" name="orders" />
        <el-tab-pane label="菜单管理" name="menu" />
        <el-tab-pane label="小料配置" name="modifiers" />
        <el-tab-pane label="店铺设置" name="settings" />
      </el-tabs>
    </div>

    <!-- ========== 订单管理 ========== -->
    <div v-if="activeTab === 'orders'" style="flex: 1; overflow-y: auto; padding: 16px 24px">
      <!-- 统计卡片 -->
      <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px">
        <el-card shadow="never" body-style="padding: 16px">
          <div style="color: #999; font-size: 13px; margin-bottom: 8px">今日订单</div>
          <div style="font-size: 28px; font-weight: bold">{{ orderStats.todayCount }}</div>
        </el-card>
        <el-card shadow="never" body-style="padding: 16px">
          <div style="color: #999; font-size: 13px; margin-bottom: 8px">今日营收</div>
          <div style="font-size: 28px; font-weight: bold; color: #f56c6c">¥{{ orderStats.todayRevenue.toFixed(2) }}</div>
        </el-card>
        <el-card shadow="never" body-style="padding: 16px">
          <div style="color: #999; font-size: 13px; margin-bottom: 8px">进行中</div>
          <div style="font-size: 28px; font-weight: bold; color: #E6A23C">{{ orderStats.activeCount }}</div>
        </el-card>
        <el-card shadow="never" body-style="padding: 16px">
          <div style="color: #999; font-size: 13px; margin-bottom: 8px">客单价</div>
          <div style="font-size: 28px; font-weight: bold; color: #409EFF">¥{{ orderStats.avgAmount.toFixed(2) }}</div>
        </el-card>
      </div>

      <!-- 筛选栏 -->
      <div style="margin-bottom: 16px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
        <el-radio-group v-model="dateFilter" size="small">
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-select v-model="orderFilter" placeholder="订单状态" clearable size="small" style="width: 130px">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button size="small" @click="loadOrders">刷新</el-button>
        <span style="color: #999; font-size: 13px">共 {{ filteredOrders.length }} 单</span>
      </div>
      <el-table :data="filteredOrders" stripe v-loading="loading">
        <el-table-column label="取餐号" width="100"><template #default="{ row }"><span style="font-size: 20px; font-weight: bold">{{ row.orderNumber }}</span></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :color="statusMap[row.status]?.color" effect="dark" size="small">{{ statusMap[row.status]?.label }}</el-tag></template></el-table-column>
        <el-table-column label="金额" width="100" align="right"><template #default="{ row }"><span style="color: #f56c6c; font-weight: bold">¥{{ row.totalAmount.toFixed(2) }}</span></template></el-table-column>
        <el-table-column label="件数" width="70" prop="itemCount" align="center" />
        <el-table-column label="备注" prop="notes" min-width="120" show-overflow-tooltip />
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewOrderDetail(row)">详情</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED'].includes(row.status)" type="primary" size="small" @click="handleAdvance(row)">{{ { PENDING:'制作', PREPARING:'出餐', READY:'完成' }[row.status] }}</el-button>
            <el-button size="small" @click="handlePrint(row)">打印</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED'].includes(row.status)" type="danger" text size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="orderDetailVisible" title="订单详情" width="550px">
      <div v-if="orderDetail">
        <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
          <span style="font-size: 24px; font-weight: bold">{{ orderDetail.orderNumber }}</span>
          <el-tag :color="statusMap[orderDetail.status]?.color" effect="dark">{{ statusMap[orderDetail.status]?.label }}</el-tag>
        </div>
        <div style="color: #999; margin-bottom: 16px">{{ formatTime(orderDetail.createdAt) }}</div>

        <el-table :data="orderDetail.items" stripe size="small">
          <el-table-column label="商品" min-width="120">
            <template #default="{ row }">
              <div style="font-weight: bold">{{ row.productName }}</div>
              <div v-if="row.selectedSize" style="font-size: 12px; color: #999">{{ row.selectedSize }}</div>
            </template>
          </el-table-column>
          <el-table-column label="小料/备注" min-width="150">
            <template #default="{ row }">
              <div v-for="mod in parseMods(row.appliedModifiersJson)" :key="mod.modifierId" style="font-size: 12px; color: #666">
                {{ mod.groupName }}: {{ mod.modifierName }}
                <span v-if="mod.quantity > 1" style="font-weight: bold">×{{ mod.quantity }}</span>
                <span v-if="mod.price > 0" style="color: #f56c6c"> ¥{{ (mod.price * mod.quantity).toFixed(2) }}</span>
              </div>
              <div v-if="row.notes" style="font-size: 12px; color: #e6a23c">※ {{ row.notes }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="70" align="center" prop="quantity" />
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }"><span style="color: #f56c6c; font-weight: bold">¥{{ row.lineTotal.toFixed(2) }}</span></template>
          </el-table-column>
        </el-table>

        <div v-if="orderDetail.notes" style="margin-top: 12px; color: #e6a23c">
          备注：{{ orderDetail.notes }}
        </div>

        <div style="margin-top: 16px; text-align: right; font-size: 20px; font-weight: bold">
          合计：<span style="color: #f56c6c">¥{{ orderDetail.totalAmount.toFixed(2) }}</span>
        </div>
      </div>
    </el-dialog>

    <!-- ========== 菜单管理（融合版） ========== -->
    <div v-if="activeTab === 'menu'" style="flex: 1; overflow: hidden; display: flex">
      <!-- 左栏：分类 + 商品列表 -->
      <div style="width: 380px; flex-shrink: 0; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; background: white">
        <!-- 分类选择 -->
        <div style="padding: 10px 16px; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center">
          <div style="flex: 1; overflow-x: auto; white-space: nowrap">
            <el-radio-group v-model="selectedCatId" size="small">
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.displayName }}</el-radio-button>
            </el-radio-group>
          </div>
          <el-button size="small" text type="primary" style="flex-shrink: 0; margin-left: 8px" @click="catManageVisible = true">分类管理</el-button>
        </div>

        <!-- 商品列表 -->
        <div style="flex: 1; overflow-y: auto; padding: 8px 12px">
          <div
            v-for="p in filteredProducts" :key="p.id"
            :style="{ padding: '10px 12px', marginBottom: '6px', borderRadius: '8px', cursor: 'pointer', border: '1px solid', borderColor: selectedProductId === p.id ? '#409EFF' : 'transparent', background: selectedProductId === p.id ? '#ecf5ff' : '#fafafa' }"
            @click="selectedProductId = p.id"
          >
            <div style="display: flex; justify-content: space-between; align-items: center">
              <div style="display: flex; align-items: center; gap: 8px">
                <span style="font-weight: 600; font-size: 15px">{{ p.name }}</span>
                <el-tag :type="p.isActive ? 'success' : 'info'" size="small" @click.stop="toggleProduct(p)" style="cursor: pointer">{{ p.isActive ? '上架' : '下架' }}</el-tag>
                <el-tag v-if="p.hasModifiers" type="warning" size="small">小料</el-tag>
              </div>
              <span style="color: #f56c6c; font-weight: bold; font-size: 15px">¥{{ p.basePrice.toFixed(2) }}</span>
            </div>
            <div style="font-size: 12px; color: #999; margin-top: 4px; display: flex; justify-content: space-between">
              <span>{{ getCatName(p.categoryId) }}</span>
              <span>{{ p.description }}</span>
            </div>
          </div>

          <div style="padding: 8px; text-align: center">
            <el-button size="small" type="primary" plain @click="openAddProduct">+ 新增商品</el-button>
          </div>
        </div>
      </div>

      <!-- 右栏：商品详情 + 小料管理 -->
      <div style="flex: 1; overflow-y: auto; padding: 20px 24px">
        <!-- 未选商品 -->
        <div v-if="!selectedProduct" style="display: flex; align-items: center; justify-content: center; height: 100%; color: #c0c4cc">
          <div style="text-align: center">
            <div style="font-size: 48px; margin-bottom: 12px">🍜</div>
            <div>点击左侧商品查看详情和管理小料</div>
          </div>
        </div>

        <!-- 已选商品 -->
        <template v-else>
          <!-- 商品信息卡片 -->
          <el-card shadow="never" style="margin-bottom: 20px">
            <div style="display: flex; justify-content: space-between; align-items: flex-start">
              <div>
                <div style="font-size: 20px; font-weight: bold; margin-bottom: 4px">{{ selectedProduct.name }}</div>
                <div style="color: #999; margin-bottom: 8px">{{ selectedProduct.description }}</div>
                <div style="display: flex; gap: 16px; font-size: 14px">
                  <span>分类：<b>{{ getCatName(selectedProduct.categoryId) }}</b></span>
                  <span>价格：<b style="color: #f56c6c">¥{{ selectedProduct.basePrice.toFixed(2) }}</b></span>
                  <span>库存：<b>{{ selectedProduct.currentStock < 0 ? '不限' : selectedProduct.currentStock }}</b></span>
                </div>
              </div>
              <div style="display: flex; gap: 8px">
                <el-button size="small" @click="openEditProduct(selectedProduct)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteProduct(selectedProduct)">删除</el-button>
              </div>
            </div>
          </el-card>

          <!-- 小料组 -->
          <template v-if="selectedProduct.hasModifiers">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
              <span style="font-size: 16px; font-weight: bold">小料配置</span>
              <el-select
                placeholder="关联小料组"
                size="small"
                style="width: 180px"
                :disabled="availableGroups.length === 0"
                @change="linkGroupToProduct"
              >
                <el-option v-for="g in availableGroups" :key="g.id" :label="g.name" :value="g.id" />
              </el-select>
            </div>

            <div v-if="productModGroups.length === 0" style="color: #c0c4cc; text-align: center; padding: 40px 0">
              暂未关联小料组，请从上方选择添加
            </div>

            <el-card v-for="g in productModGroups" :key="g.id" shadow="never" style="margin-bottom: 16px">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <div style="display: flex; align-items: center; gap: 8px">
                  <span style="font-weight: bold; font-size: 15px">{{ g.name }}</span>
                  <el-tag v-if="g.isRequired" type="danger" size="small">必选</el-tag>
                  <el-tag size="small" type="info">{{ getSelectionLabel(g) }}</el-tag>
                  <span style="color: #999; font-size: 12px">编辑请到「小料配置」页</span>
                </div>
                <el-button text type="warning" size="small" @click="unlinkGroup(g)">移除关联</el-button>
              </div>
            </template>
            <div style="display: flex; flex-wrap: wrap; gap: 8px">
              <div
                v-for="m in g.modifiers" :key="m.id"
                style="padding: 6px 12px; background: #f5f7fa; border-radius: 6px; border: 1px solid #e4e7ed"
              >
                <span>{{ m.name }}</span>
                <span v-if="m.additionalPrice > 0" style="color: #f56c6c; margin-left: 6px; font-size: 13px">¥{{ m.additionalPrice.toFixed(2) }}</span>
              </div>
            </div>
          </el-card>
          </template>
          <div v-else style="color: #c0c4cc; text-align: center; padding: 40px 0; background: #fafafa; border-radius: 8px">
            该商品未启用小料配置，<el-button type="primary" text size="small" @click="openEditProduct(selectedProduct)">去开启</el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- ========== 弹窗 ========== -->
    <!-- 分类管理 -->
    <el-dialog v-model="catManageVisible" title="分类管理" width="550px">
      <div style="margin-bottom: 12px; text-align: right">
        <el-button size="small" type="primary" @click="openAddCategory">+ 新增分类</el-button>
      </div>
      <el-table :data="categories" stripe size="small">
        <el-table-column label="名称" prop="displayName" min-width="100" />
        <el-table-column label="颜色" width="80" align="center">
          <template #default="{ row }"><div :style="{ width: '20px', height: '20px', borderRadius: '4px', background: row.colorHex, margin: '0 auto' }"></div></template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="70" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openEditCategory(row); catManageVisible = false">编辑</el-button>
            <el-button type="danger" text size="small" @click="deleteCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 分类编辑 -->
    <el-dialog v-model="catDialogVisible" :title="catIsEdit ? '编辑分类' : '新增分类'" width="400px" destroy-on-close>
      <el-form label-width="60px"><el-form-item label="名称"><el-input v-model="catForm.displayName" /></el-form-item><el-form-item label="颜色"><el-color-picker v-model="catForm.colorHex" /></el-form-item><el-form-item label="排序"><el-input-number v-model="catForm.sortOrder" :min="0" /></el-form-item></el-form>
      <template #footer><el-button @click="catDialogVisible = false">取消</el-button><el-button type="primary" @click="saveCategory">保存</el-button></template>
    </el-dialog>

    <!-- 商品 -->
    <el-dialog v-model="productDialogVisible" :title="productIsEdit ? '编辑商品' : '新增商品'" width="500px" destroy-on-close>
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="productForm.name" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="productForm.categoryId" style="width:100%"><el-option v-for="cat in categories" :key="cat.id" :label="cat.displayName" :value="cat.id" /></el-select></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="productForm.basePrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="productForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="productForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="启用小料"><el-switch v-model="productForm.hasModifiers" /><span style="color: #999; margin-left: 8px; font-size: 12px">开启后点餐时可选小料</span></el-form-item>
        <el-form-item label="图片">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-upload
              action="/api/upload"
              :show-file-list="false"
              :on-success="handleUploadSuccess"
              :before-upload="beforeUpload"
              accept="image/*"
            >
              <el-button size="small">上传图片</el-button>
            </el-upload>
            <el-input v-model="productForm.imageUrl" placeholder="或输入图片URL" style="flex: 1" size="small" />
          </div>
          <div v-if="productForm.imageUrl" style="margin-top: 8px">
            <el-image :src="productForm.imageUrl" style="width: 100px; height: 100px; border-radius: 6px" fit="cover" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="productDialogVisible = false">取消</el-button><el-button type="primary" @click="saveProduct">保存</el-button></template>
    </el-dialog>

    <!-- 小料组 -->
    <el-dialog v-model="groupDialogVisible" :title="groupIsEdit ? '编辑小料组' : '新增小料组'" width="500px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="groupForm.name" placeholder="如：卤味、辣度" /></el-form-item>
        <el-form-item label="必选"><el-switch v-model="groupForm.isRequired" /></el-form-item>
        <el-form-item label="默认组"><el-switch v-model="groupForm.isDefault" /><span style="color: #999; margin-left: 8px; font-size: 12px">新商品自动关联</span></el-form-item>
        <el-form-item label="选择方式"><el-radio-group v-model="groupForm.maxSelection"><el-radio :value="0">不限数量</el-radio><el-radio :value="1">单选</el-radio></el-radio-group></el-form-item>
        <el-form-item label="最少选"><el-input-number v-model="groupForm.minSelection" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="groupForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="关联商品"><el-select v-model="groupForm.productIds" multiple collapse-tags collapse-tags-tooltip style="width:100%"><el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="groupDialogVisible = false">取消</el-button><el-button type="primary" @click="saveGroup">保存</el-button></template>
    </el-dialog>

    <!-- 小料配置 -->
    <div v-if="activeTab === 'modifiers'" style="flex: 1; overflow-y: auto; padding: 24px">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
        <span style="font-size: 16px; font-weight: bold">小料组管理</span>
        <el-button type="primary" @click="openAddGroup">+ 新增小料组</el-button>
      </div>

      <el-table :data="modGroups" stripe style="width: 100%">
        <el-table-column label="名称" prop="name" min-width="120" />
        <el-table-column label="必选" width="80" align="center">
          <template #default="{ row }"><el-tag :type="row.isRequired ? 'danger' : 'info'" size="small">{{ row.isRequired ? '必选' : '可选' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }"><el-tag v-if="row.isDefault" type="warning" size="small">默认</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="选择方式" width="100" align="center">
          <template #default="{ row }">{{ row.maxSelection === 1 ? '单选' : '不限' }}</template>
        </el-table-column>
        <el-table-column label="小料数" width="80" align="center">
          <template #default="{ row }">{{ row.modifiers.length }}</template>
        </el-table-column>
        <el-table-column label="关联商品" width="100" align="center">
          <template #default="{ row }">{{ row.productIds.length }} 个</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openEditGroup(row)">编辑</el-button>
            <el-button type="danger" text size="small" @click="deleteGroup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 展开行显示小料详情 -->
      <div style="margin-top: 24px">
        <div v-for="g in modGroups" :key="g.id" style="margin-bottom: 16px">
          <el-card shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span style="font-weight: bold">{{ g.name }} <span style="color: #999; font-weight: normal">({{ g.modifiers.length }} 个小料)</span></span>
                <el-button size="small" @click="openAddMod(g.id)">+ 添加小料</el-button>
              </div>
            </template>
            <div style="display: flex; flex-wrap: wrap; gap: 8px">
              <div v-for="m in g.modifiers" :key="m.id" style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #f5f7fa; border-radius: 6px; border: 1px solid #e4e7ed">
                <span style="font-weight: 500">{{ m.name }}</span>
                <span v-if="m.additionalPrice > 0" style="color: #f56c6c; font-size: 13px">¥{{ m.additionalPrice.toFixed(2) }}</span>
                <el-icon style="cursor: pointer; color: #909399; font-size: 14px" @click="openEditMod(m)"><Edit /></el-icon>
                <el-icon style="cursor: pointer; color: #F56C6C; font-size: 14px" @click="deleteMod(m)"><Delete /></el-icon>
              </div>
              <el-empty v-if="g.modifiers.length === 0" description="暂无小料" :image-size="40" />
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 店铺设置 -->
    <div v-if="activeTab === 'settings'" style="flex: 1; overflow-y: auto; padding: 24px; display: flex; justify-content: center">
      <el-card style="width: 500px" shadow="never">
        <template #header><span style="font-weight: bold; font-size: 16px">店铺信息</span></template>
        <el-form label-width="80px">
          <el-form-item label="店名"><el-input v-model="settingsForm.storeName" placeholder="如：老李板面馆" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="settingsForm.storePhone" placeholder="如：0371-8888-6666" /></el-form-item>
          <el-form-item label="标语"><el-input v-model="settingsForm.storeTagline" placeholder="如：正宗手擀板面" /></el-form-item>
          <el-form-item label="小票尾语"><el-input v-model="settingsForm.receiptFooter" placeholder="如：感谢光临，欢迎再来!" /></el-form-item>
          <el-form-item><el-button type="primary" @click="saveSettings">保存设置</el-button></el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 小料项 -->
    <el-dialog v-model="modDialogVisible" :title="modIsEdit ? '编辑小料' : '新增小料'" width="400px" destroy-on-close>
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="modForm.name" placeholder="如：卤蛋、鸡腿" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="modForm.additionalPrice" :min="0" :precision="1" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="modForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="modDialogVisible = false">取消</el-button><el-button type="primary" @click="saveMod">保存</el-button></template>
    </el-dialog>
  </div>
</template>
