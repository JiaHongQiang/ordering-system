package com.ordering.system.domain.engine

import com.ordering.system.domain.model.CartItem
import com.ordering.system.domain.model.LineItemPricing
import com.ordering.system.domain.model.ModifierPricingDetail
import com.ordering.system.domain.model.OrderPricing
import com.ordering.system.domain.model.SelectedModifier
import kotlin.math.round

class PricingEngine {

    fun calculateLineItem(item: CartItem): LineItemPricing {
        require(item.quantity > 0) { "数量必须大于 0: ${item.quantity}" }
        require(item.basePrice >= 0) { "基础价格不能为负: ${item.basePrice}" }

        val modifierDetails = item.selectedModifiers.map { mod ->
            calculateModifierPrice(mod, item.selectedSize)
        }

        val modifierTotal = roundToCent(
            item.selectedModifiers.zip(modifierDetails).sumOf { (mod, detail) ->
                detail.effectivePrice * mod.quantity
            }
        )
        val effectiveBasePrice = item.basePrice
        val lineTotal = roundToCent((effectiveBasePrice + modifierTotal) * item.quantity)

        return LineItemPricing(
            productId = item.productId,
            productName = item.productName,
            quantity = item.quantity,
            basePrice = item.basePrice,
            effectiveBasePrice = effectiveBasePrice,
            modifierTotal = modifierTotal,
            lineTotal = lineTotal,
            modifierDetails = modifierDetails
        )
    }

    fun calculateModifierPrice(modifier: SelectedModifier, selectedSize: String): ModifierPricingDetail {
        val effectivePrice = if (selectedSize.isNotBlank()) {
            modifier.sizeOverrides[selectedSize] ?: modifier.additionalPrice
        } else {
            modifier.additionalPrice
        }

        return ModifierPricingDetail(
            modifierId = modifier.modifierId,
            modifierName = modifier.modifierName,
            basePrice = modifier.additionalPrice,
            effectivePrice = roundToCent(effectivePrice),
            appliedSize = selectedSize
        )
    }

    fun calculateOrder(items: List<CartItem>): OrderPricing {
        require(items.isNotEmpty()) { "订单不能为空" }

        val itemPricings = items.map { calculateLineItem(it) }
        val total = roundToCent(itemPricings.sumOf { it.lineTotal })
        val itemCount = items.sumOf { it.quantity }

        return OrderPricing(
            itemPricings = itemPricings,
            total = total,
            itemCount = itemCount
        )
    }

    fun applyDiscount(originalPrice: Double, discountRate: Double): Double {
        require(discountRate in 0.0..1.0) { "折扣率必须在 0 到 1 之间: $discountRate" }
        return roundToCent(originalPrice * discountRate)
    }

    private fun roundToCent(value: Double): Double {
        return round(value * 100) / 100
    }
}
