package com.ordering.system.domain.engine

import com.ordering.system.domain.model.CartItem
import com.ordering.system.domain.model.ModifierGroup
import com.ordering.system.domain.model.Modifier
import com.ordering.system.domain.model.Product

/**
 * 订单提交前的逻辑校验器。
 *
 * 在订单写入 DAO 之前，执行以下规则检查：
 * 1. **产品有效性**：产品是否存在、是否在售、库存是否充足
 * 2. **必选性检查**：遍历 ModifierGroup，若 [ModifierGroup.isRequired] 为 true，
 *    则选中的修饰符数量必须 ≥ 1
 * 3. **最小选择数检查**：选中数量 ≥ [ModifierGroup.minSelection]
 * 4. **数量上限检查**：选中数量 ≤ [ModifierGroup.maxSelection]
 *
 * 所有检查结果通过 [ValidationResult] 反馈给 UI 层，包含错误类型和友好提示信息。
 */
class OrderValidator {

    /**
     * 校验整个订单（所有购物车项目）。
     *
     * @param items 购物车项目列表
     * @param productModifierGroups 每个单品关联的加料组及其加料项
     *   (productId → List<Pair<ModifierGroup, List<Modifier>>>)
     * @param products 产品信息映射 (productId → Product)
     * @return [ValidationResult] 校验结果，包含所有违规项及友好提示
     */
    fun validateOrder(
        items: List<CartItem>,
        productModifierGroups: Map<Long, List<Pair<ModifierGroup, List<Modifier>>>>,
        products: Map<Long, Product>
    ): ValidationResult {
        require(items.isNotEmpty()) { "订单不能为空" }

        val violations = mutableListOf<ValidationViolation>()

        items.forEach { cartItem ->
            val product = products[cartItem.productId]
            if (product == null) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.PRODUCT_NOT_FOUND,
                        productId = cartItem.productId,
                        message = "商品不存在: ${cartItem.productId}"
                    )
                )
                return@forEach
            }

            if (!product.isActive) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.PRODUCT_INACTIVE,
                        productId = cartItem.productId,
                        message = "商品已下架: ${product.name}"
                    )
                )
            }

            if (product.currentStock >= 0 && product.currentStock < cartItem.quantity) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.INSUFFICIENT_STOCK,
                        productId = cartItem.productId,
                        message = "库存不足: ${product.name} (库存: ${product.currentStock}, 需要: ${cartItem.quantity})"
                    )
                )
            }

            val modifierGroups = productModifierGroups[cartItem.productId] ?: emptyList()
            val itemViolations = validateCartItem(cartItem, modifierGroups)
            violations.addAll(itemViolations)
        }

        return ValidationResult(
            isValid = violations.none { it.type.isBlocking },
            violations = violations
        )
    }

    /**
     * 校验单个购物车项目的修饰符选择。
     *
     * 检查规则：
     * 1. 若 [ModifierGroup.isRequired] 为 true 且选中数量为 0 → [ViolationType.REQUIRED_MODIFIER_MISSING]
     * 2. 若选中数量 < [ModifierGroup.minSelection] → [ViolationType.MIN_SELECTIONS_NOT_MET]
     * 3. 若选中数量 > [ModifierGroup.maxSelection] → [ViolationType.MAX_SELECTIONS_EXCEEDED]
     *
     * @param item 购物车项目
     * @param modifierGroups 该项目关联的修饰符组及其加料项列表
     * @return 违规列表，空列表表示校验通过
     */
    fun validateCartItem(
        item: CartItem,
        modifierGroups: List<Pair<ModifierGroup, List<Modifier>>>
    ): List<ValidationViolation> {
        val violations = mutableListOf<ValidationViolation>()
        val selectedByGroup = item.selectedModifiers.groupBy { it.groupId }

        modifierGroups.forEach { (group, _) ->
            val selected = selectedByGroup[group.id] ?: emptyList()
            val selectedCount = selected.size

            // 1. 必选性检查：is_required = true 时，选中数必须 ≥ 1
            if (group.isRequired && selectedCount == 0) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.REQUIRED_MODIFIER_MISSING,
                        productId = item.productId,
                        groupId = group.id,
                        message = "必选加料未选择: ${group.name} (商品: ${item.productName})"
                    )
                )
            }

            // 2. 最小选择数检查
            if (group.minSelection > 0 && selectedCount < group.minSelection) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.MIN_SELECTIONS_NOT_MET,
                        productId = item.productId,
                        groupId = group.id,
                        message = "加料选择不足: ${group.name} (最少选 ${group.minSelection} 项，已选 ${selectedCount} 项)"
                    )
                )
            }

            // 3. 数量上限检查：选中数不得超过 max_selection
            if (group.maxSelection > 0 && selectedCount > group.maxSelection) {
                violations.add(
                    ValidationViolation(
                        type = ViolationType.MAX_SELECTIONS_EXCEEDED,
                        productId = item.productId,
                        groupId = group.id,
                        message = "加料超出上限: ${group.name} (最多选 ${group.maxSelection} 项，已选 ${selectedCount} 项)"
                    )
                )
            }
        }

        return violations
    }

    /**
     * 校验单个购物车项目（简化参数版本，便于单元测试）。
     */
    fun validateCartItem(
        item: CartItem,
        modifierGroups: List<ModifierGroup>,
        modifiersByGroup: Map<Long, List<Modifier>>
    ): List<ValidationViolation> {
        val pairs = modifierGroups.map { group ->
            group to (modifiersByGroup[group.id] ?: emptyList())
        }
        return validateCartItem(item, pairs)
    }
}

/**
 * 校验结果。
 *
 * @property isValid 是否通过校验（无阻断性违规）
 * @property violations 所有违规项列表
 */
data class ValidationResult(
    val isValid: Boolean,
    val violations: List<ValidationViolation>
)

/**
 * 校验违规项。
 *
 * @property type 违规类型
 * @property productId 关联的商品 ID
 * @property groupId 关联的修饰符组 ID（可选）
 * @property message 可读的违规描述信息，可直接展示给用户
 */
data class ValidationViolation(
    val type: ViolationType,
    val productId: Long,
    val groupId: Long? = null,
    val message: String
)

/**
 * 违规类型枚举。
 *
 * @property isBlocking 是否为阻断性违规，true 时阻止订单提交
 */
enum class ViolationType(val isBlocking: Boolean) {
    /** 产品 ID 不存在 */
    PRODUCT_NOT_FOUND(true),
    /** 产品已下架 */
    PRODUCT_INACTIVE(true),
    /** 库存不足 */
    INSUFFICIENT_STOCK(true),
    /** 必选修饰符组未选（is_required = true 但选中数 = 0） */
    REQUIRED_MODIFIER_MISSING(true),
    /** 选中数量 < minSelection */
    MIN_SELECTIONS_NOT_MET(true),
    /** 选中数量 > maxSelection */
    MAX_SELECTIONS_EXCEEDED(true)
}
