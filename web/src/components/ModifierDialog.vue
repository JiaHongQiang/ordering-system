<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Remove, CirclePlus } from '@element-plus/icons-vue'
import type { ModifierGroup, ProductDetail } from '../api/menu'
import type { CartModifier } from '../stores/cart'

const props = defineProps<{
  visible: boolean
  productDetail: ProductDetail | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'confirm', data: { modifiers: CartModifier[], size: string, notes: string }): void
}>()

// 单选组：Map<groupId, modifierId | null>
const singleSelections = ref<Map<number, number | null>>(new Map())
// 多选组（不限数量）：Map<groupId, Map<modifierId, quantity>>
const multiSelections = ref<Map<number, Map<number, number>>>(new Map())
const selectedSize = ref('')
const notes = ref('')

watch(() => props.visible, (val) => {
  if (val && props.productDetail) {
    singleSelections.value = new Map()
    multiSelections.value = new Map()
    selectedSize.value = ''
    notes.value = ''
    // 自动选中必选单选组的第一个
    props.productDetail.modifierGroups.forEach(group => {
      if (group.maxSelection === 1 && group.isRequired && group.modifiers.length > 0) {
        singleSelections.value.set(group.id, group.modifiers[0].id)
      }
    })
  }
})

const isSingleSelect = (group: ModifierGroup) => group.maxSelection === 1

const selectSingle = (groupId: number, modifierId: number) => {
  singleSelections.value.set(groupId, modifierId)
}

const getSingleSelected = (groupId: number): number | null => {
  return singleSelections.value.get(groupId) ?? null
}

const getMultiQty = (groupId: number, modifierId: number): number => {
  return multiSelections.value.get(groupId)?.get(modifierId) ?? 0
}

const setMultiQty = (groupId: number, modifierId: number, qty: number) => {
  if (!multiSelections.value.has(groupId)) {
    multiSelections.value.set(groupId, new Map())
  }
  const groupMap = multiSelections.value.get(groupId)!
  if (qty <= 0) {
    groupMap.delete(modifierId)
  } else {
    groupMap.set(modifierId, qty)
  }
}

const getMultiTotal = (groupId: number): number => {
  const groupMap = multiSelections.value.get(groupId)
  if (!groupMap) return 0
  let total = 0
  groupMap.forEach(q => total += q)
  return total
}

const isValid = computed(() => {
  if (!props.productDetail) return false
  for (const group of props.productDetail.modifierGroups) {
    if (isSingleSelect(group)) {
      const sel = singleSelections.value.get(group.id)
      if (group.isRequired && sel == null) return false
    } else {
      const total = getMultiTotal(group.id)
      if (group.isRequired && total < group.minSelection) return false
    }
  }
  return true
})

const handleConfirm = () => {
  if (!props.productDetail || !isValid.value) return

  const modifiers: CartModifier[] = []
  for (const group of props.productDetail.modifierGroups) {
    if (isSingleSelect(group)) {
      const modId = singleSelections.value.get(group.id)
      if (modId != null) {
        const mod = group.modifiers.find(m => m.id === modId)
        if (mod) {
          modifiers.push({
            groupId: group.id, groupName: group.name,
            modifierId: mod.id, modifierName: mod.name,
            additionalPrice: mod.additionalPrice, quantity: 1,
            sizeOverrides: mod.sizeOverrides
          })
        }
      }
    } else {
      const groupMap = multiSelections.value.get(group.id)
      if (groupMap) {
        groupMap.forEach((qty, modId) => {
          const mod = group.modifiers.find(m => m.id === modId)
          if (mod && qty > 0) {
            modifiers.push({
              groupId: group.id, groupName: group.name,
              modifierId: mod.id, modifierName: mod.name,
              additionalPrice: mod.additionalPrice, quantity: qty,
              sizeOverrides: mod.sizeOverrides
            })
          }
        })
      }
    }
  }

  emit('confirm', { modifiers, size: selectedSize.value, notes: notes.value })
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="productDetail?.product.name || '选择规格'"
    width="500px"
    destroy-on-close
  >
    <div v-if="productDetail">
      <div style="margin-bottom: 8px; color: #999; font-size: 14px">
        {{ productDetail.product.description }}
      </div>
      <div style="font-size: 20px; color: #f56c6c; margin-bottom: 16px">
        ¥{{ productDetail.product.basePrice.toFixed(2) }}
      </div>

      <div v-for="group in productDetail.modifierGroups" :key="group.id" style="margin-bottom: 16px">
        <div style="margin-bottom: 8px">
          <span style="font-weight: bold">{{ group.name }}</span>
          <el-tag v-if="group.isRequired" type="danger" size="small" style="margin-left: 8px">必选</el-tag>
          <span v-if="isSingleSelect(group) && group.maxSelection > 0" style="color: #999; margin-left: 8px; font-size: 12px">
            单选
          </span>
          <span v-else-if="!isSingleSelect(group)" style="color: #999; margin-left: 8px; font-size: 12px">
            可多选，同一种可加多份
          </span>
        </div>

        <!-- 单选组：按钮式 -->
        <div v-if="isSingleSelect(group)" style="display: flex; flex-wrap: wrap; gap: 8px">
          <el-check-tag
            v-for="mod in group.modifiers"
            :key="mod.id"
            :checked="getSingleSelected(group.id) === mod.id"
            @change="selectSingle(group.id, mod.id)"
          >
            {{ mod.name }}
            <span v-if="mod.additionalPrice > 0" style="color: #f56c6c; margin-left: 4px">
              +¥{{ mod.additionalPrice.toFixed(2) }}
            </span>
          </el-check-tag>
        </div>

        <!-- 多选组（不限数量）：紧凑网格 -->
        <div v-else style="display: flex; flex-wrap: wrap; gap: 8px">
          <div
            v-for="mod in group.modifiers"
            :key="mod.id"
            :style="{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 10px',
              borderRadius: '6px',
              border: '1px solid',
              borderColor: getMultiQty(group.id, mod.id) > 0 ? '#409EFF' : '#e4e7ed',
              background: getMultiQty(group.id, mod.id) > 0 ? '#ecf5ff' : '#f5f7fa'
            }"
          >
            <div style="display: flex; flex-direction: column; align-items: flex-start; margin-right: 2px">
              <span style="font-size: 14px; line-height: 1.2">{{ mod.name }}</span>
              <span v-if="mod.additionalPrice > 0" style="font-size: 11px; color: #f56c6c">
                ¥{{ mod.additionalPrice.toFixed(0) }}/份
              </span>
            </div>
            <el-icon
              :style="{ cursor: 'pointer', color: getMultiQty(group.id, mod.id) > 0 ? '#909399' : '#dcdfe6' }"
              @click="setMultiQty(group.id, mod.id, Math.max(0, getMultiQty(group.id, mod.id) - 1))"
            >
              <Remove />
            </el-icon>
            <span
              style="min-width: 16px; text-align: center; font-weight: bold; font-size: 15px"
              :style="{ color: getMultiQty(group.id, mod.id) > 0 ? '#409EFF' : '#dcdfe6' }"
            >
              {{ getMultiQty(group.id, mod.id) }}
            </span>
            <el-icon
              style="cursor: pointer; color: #409EFF"
              @click="setMultiQty(group.id, mod.id, getMultiQty(group.id, mod.id) + 1)"
            >
              <CirclePlus />
            </el-icon>
          </div>
        </div>
      </div>

      <div style="margin-bottom: 16px">
        <div style="font-weight: bold; margin-bottom: 8px">备注</div>
        <el-input v-model="notes" placeholder="如：少放盐、多放辣等" />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!isValid" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>
