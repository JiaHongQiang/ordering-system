package com.ordering.system.domain.model

/**
 * 购物车行项目，订单提交前的中间状态。
 *
 * 包含规格选择和加料明细，供 [com.ordering.system.domain.engine.PricingEngine]
 * 和 [com.ordering.system.domain.engine.OrderValidator] 使用。
 */
data class CartItem(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val basePrice: Double,
    val selectedSize: String = "",
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val notes: String = ""
)

/**
 * 已选加料项。
 *
 * @property groupId 所属修饰符组 ID
 * @property groupName 修饰符组名称（快照）
 * @property modifierId 加料项 ID
 * @property modifierName 加料项名称（快照）
 * @property additionalPrice 基础附加价格
 * @property sizeOverrides 不同规格下的价格覆盖映射。
 *   示例: {"small": 0.0, "medium": 2.0, "large": 4.0}
 *   空 Map 表示所有规格统一使用 [additionalPrice]
 */
data class SelectedModifier(
    val groupId: Long,
    val groupName: String,
    val modifierId: Long,
    val modifierName: String,
    val additionalPrice: Double,
    val quantity: Int = 1,
    val sizeOverrides: Map<String, Double> = emptyMap()
)

/**
 * 单行项目定价结果。
 *
 * 所有金额字段均经过四舍五入到分（两位小数），避免浮点累积误差。
 * [modifierTotal] 为所有加料有效价格之和（单份），[lineTotal] 为含税行总价。
 */
data class LineItemPricing(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val basePrice: Double,
    val effectiveBasePrice: Double,
    val modifierTotal: Double,
    val lineTotal: Double,
    val modifierDetails: List<ModifierPricingDetail>
)

/**
 * 单个加料项的定价明细。
 */
data class ModifierPricingDetail(
    val modifierId: Long,
    val modifierName: String,
    val basePrice: Double,
    val effectivePrice: Double,
    val appliedSize: String = ""
)

/**
 * 整单定价汇总。
 */
data class OrderPricing(
    val itemPricings: List<LineItemPricing>,
    val total: Double,
    val itemCount: Int
)
