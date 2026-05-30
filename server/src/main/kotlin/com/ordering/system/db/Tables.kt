package com.ordering.system.db

import com.ordering.system.domain.model.OrderStatus
import org.jetbrains.exposed.sql.Table

object CategoriesTable : Table("categories") {
    val id = long("id").autoIncrement()
    val displayName = varchar("display_name", 100)
    val sortOrder = integer("sort_order").default(0)
    val colorHex = varchar("color_hex", 10).default("#FFFFFF")
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

object ProductsTable : Table("products") {
    val id = long("id").autoIncrement()
    val categoryId = long("category_id").references(CategoriesTable.id)
    val name = varchar("name", 200)
    val basePrice = double("base_price")
    val stockWarningThreshold = integer("stock_warning_threshold").default(0)
    val currentStock = integer("current_stock").default(-1)
    val description = text("description").default("")
    val imageUrl = varchar("image_url", 500).nullable()
    val isActive = bool("is_active").default(true)
    val hasModifiers = bool("has_modifiers").default(false)
    val sortOrder = integer("sort_order").default(0)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

object ModifierGroupsTable : Table("modifier_groups") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val isRequired = bool("is_required").default(false)
    val isDefault = bool("is_default").default(false)
    val maxSelection = integer("max_selection").default(0)
    val minSelection = integer("min_selection").default(0)
    val sortOrder = integer("sort_order").default(0)
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

object ModifiersTable : Table("modifiers") {
    val id = long("id").autoIncrement()
    val groupId = long("group_id").references(ModifierGroupsTable.id)
    val name = varchar("name", 100)
    val additionalPrice = double("additional_price").default(0.0)
    val sizeOverrides = text("size_overrides").default("{}")
    val isActive = bool("is_active").default(true)
    val sortOrder = integer("sort_order").default(0)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

object ProductModifierGroupXrefTable : Table("product_modifier_group_xref") {
    val productId = long("product_id").references(ProductsTable.id)
    val groupId = long("group_id").references(ModifierGroupsTable.id)
    override val primaryKey = PrimaryKey(productId, groupId)
}

object OrdersTable : Table("orders") {
    val id = long("id").autoIncrement()
    val orderNumber = varchar("order_number", 50)
    val tableNumber = varchar("table_number", 20).default("")
    val status = varchar("status", 20).default(OrderStatus.PREPARING.name)
    val totalAmount = double("total_amount").default(0.0)
    val itemCount = integer("item_count").default(0)
    val paymentMethod = varchar("payment_method", 50).default("")
    val notes = text("notes").default("")
    val createdBy = varchar("created_by", 100).default("")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    val completedAt = long("completed_at").nullable()
    val syncStatus = integer("sync_status").default(0)
    override val primaryKey = PrimaryKey(id)
}

object OrderItemsTable : Table("order_items") {
    val id = long("id").autoIncrement()
    val orderId = long("order_id").references(OrdersTable.id)
    val productId = long("product_id")
    val productName = varchar("product_name", 200)
    val quantity = integer("quantity").default(1)
    val priceAtSnap = double("price_at_snap")
    val modifierTotal = double("modifier_total").default(0.0)
    val lineTotal = double("line_total").default(0.0)
    val appliedModifiersJson = text("applied_modifiers_json").default("[]")
    val selectedSize = varchar("selected_size", 50).default("")
    val notes = text("notes").default("")
    val createdBy = varchar("created_by", 100).default("")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    val updatedAt = long("updated_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

object DailyOrderSequencesTable : Table("daily_order_sequences") {
    val businessDate = varchar("business_date", 8)
    val lastValue = integer("last_value").default(0)
    override val primaryKey = PrimaryKey(businessDate)
}

object SettingsTable : Table("settings") {
    val key = varchar("key", 50)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}
