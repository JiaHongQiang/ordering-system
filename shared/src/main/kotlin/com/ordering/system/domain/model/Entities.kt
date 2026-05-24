package com.ordering.system.domain.model

import kotlinx.serialization.Serializable

enum class OrderStatus {
    PENDING, PREPARING, READY, COMPLETED, CANCELLED
}

data class Product(
    val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val basePrice: Double,
    val stockWarningThreshold: Int = 0,
    val currentStock: Int = -1,
    val description: String = "",
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val hasModifiers: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: Long = 0,
    val displayName: String,
    val sortOrder: Int = 0,
    val colorHex: String = "#FFFFFF",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ModifierGroup(
    val id: Long = 0,
    val name: String,
    val isRequired: Boolean = false,
    val isDefault: Boolean = false,
    val maxSelection: Int = 0,
    val minSelection: Int = 0,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Modifier(
    val id: Long = 0,
    val groupId: Long,
    val name: String,
    val additionalPrice: Double = 0.0,
    val sizeOverrides: Map<String, Double> = emptyMap(),
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Order(
    val id: Long = 0,
    val orderNumber: String,
    val tableNumber: String = "",
    val status: OrderStatus = OrderStatus.PENDING,
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val paymentMethod: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val syncStatus: Int = 0
)

data class OrderItem(
    val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int = 1,
    val priceAtSnap: Double,
    val modifierTotal: Double = 0.0,
    val lineTotal: Double = 0.0,
    val appliedModifiersJson: String = "[]",
    val selectedSize: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProductModifierGroupCrossRef(
    val productId: Long,
    val groupId: Long
)
