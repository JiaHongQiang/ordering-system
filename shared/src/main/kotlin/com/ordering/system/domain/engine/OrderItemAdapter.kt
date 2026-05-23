package com.ordering.system.domain.engine

import com.ordering.system.domain.model.LineItemPricing
import com.ordering.system.domain.model.ModifierPricingDetail
import com.ordering.system.domain.model.OrderItem
import com.ordering.system.domain.model.SelectedModifier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 订单明细适配器。
 *
 * 负责将 [PricingEngine] 的计算结果及用户选择的加料快照转化为
 * [OrderItem] 数据库实体，确保下单时的价格和加料信息完整冻结，
 * 不受后续后台调价影响。
 */
class OrderItemAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 将定价结果转化为 [OrderItem] 数据库实体。
     *
     * 转化逻辑：
     * 1. 从 [LineItemPricing] 提取价格快照（price_at_snap、tax_rate、modifier_total 等）
     * 2. 将 [selectedModifiers] 序列化为 JSON 快照（applied_modifiers_json），
     *    包含加料组名称、加料项名称及当时价格
     * 3. 所有金额字段均为下单时的冻结值，后续调价不影响历史订单
     *
     * @param pricing [PricingEngine.calculateLineItem] 的计算结果
     * @param selectedModifiers 用户实际选择的加料列表
     * @param selectedSize 所选规格标识
     * @param notes 单品备注
     * @param createdBy 创建人标识
     * @return 可直接写入数据库的 [OrderItem] 实体
     */
    fun toOrderItem(
        pricing: LineItemPricing,
        selectedModifiers: List<SelectedModifier>,
        selectedSize: String = "",
        notes: String = "",
        createdBy: String = ""
    ): OrderItem {
        val appliedModifiersJson = buildAppliedModifiersJson(selectedModifiers)

        return OrderItem(
            orderId = 0,
            productId = pricing.productId,
            productName = pricing.productName,
            quantity = pricing.quantity,
            priceAtSnap = pricing.basePrice,
            modifierTotal = pricing.modifierTotal,
            lineTotal = pricing.lineTotal,
            appliedModifiersJson = appliedModifiersJson,
            selectedSize = selectedSize,
            notes = notes,
            createdBy = createdBy
        )
    }

    /**
     * 将定价结果转化为 [OrderItem]（简化版，从 CartItem 提取选择信息）。
     *
     * @param pricing 定价结果
     * @param productName 单品名称快照
     * @param selectedSize 所选规格
     * @param notes 备注
     * @param createdBy 创建人
     * @return [OrderItem] 实体
     */
    fun toOrderItem(
        pricing: LineItemPricing,
        productName: String = "",
        selectedSize: String = "",
        notes: String = "",
        createdBy: String = ""
    ): OrderItem {
        return OrderItem(
            orderId = 0,
            productId = pricing.productId,
            productName = productName.ifBlank { pricing.productName },
            quantity = pricing.quantity,
            priceAtSnap = pricing.basePrice,
            modifierTotal = pricing.modifierTotal,
            lineTotal = pricing.lineTotal,
            appliedModifiersJson = buildAppliedModifiersJsonFromDetails(pricing.modifierDetails),
            selectedSize = selectedSize,
            notes = notes,
            createdBy = createdBy
        )
    }

    /**
     * 将用户选择的加料列表序列化为 JSON 快照。
     *
     * 输出格式:
     * ```json
     * [
     *   {"groupId":1,"groupName":"温度","modifierId":10,"modifierName":"热","price":0.0},
     *   {"groupId":2,"groupName":"加料","modifierId":20,"modifierName":"加浓缩","price":5.0}
     * ]
     * ```
     */
    private fun buildAppliedModifiersJson(modifiers: List<SelectedModifier>): String {
        if (modifiers.isEmpty()) return "[]"

        val snapshots = modifiers.map { mod ->
            AppliedModifierSnapshot(
                groupId = mod.groupId,
                groupName = mod.groupName,
                modifierId = mod.modifierId,
                modifierName = mod.modifierName,
                price = mod.additionalPrice,
                quantity = mod.quantity
            )
        }
        return json.encodeToString(snapshots)
    }

    /**
     * 从定价明细构建 JSON 快照（当无原始 SelectedModifier 时使用）。
     */
    private fun buildAppliedModifiersJsonFromDetails(details: List<ModifierPricingDetail>): String {
        if (details.isEmpty()) return "[]"

        val snapshots = details.map { detail ->
            AppliedModifierSnapshot(
                groupId = 0,
                groupName = "",
                modifierId = detail.modifierId,
                modifierName = detail.modifierName,
                price = detail.effectivePrice
            )
        }
        return json.encodeToString(snapshots)
    }
}

/**
 * 加料快照数据结构，用于 JSON 序列化。
 */
@kotlinx.serialization.Serializable
private data class AppliedModifierSnapshot(
    val groupId: Long,
    val groupName: String,
    val modifierId: Long,
    val modifierName: String,
    val price: Double,
    val quantity: Int = 1
)
