package com.ordering.system.db

import com.ordering.system.domain.model.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ==================== CategoryDao ====================

object CategoryDao {
    fun findAllActive(): List<Category> = transaction {
        CategoriesTable.select { CategoriesTable.isActive eq true }
            .orderBy(CategoriesTable.sortOrder)
            .map { it.toCategory() }
    }

    fun findById(id: Long): Category? = transaction {
        CategoriesTable.select { CategoriesTable.id eq id }
            .map { it.toCategory() }
            .singleOrNull()
    }

    fun findAll(): List<Category> = transaction {
        CategoriesTable.selectAll().orderBy(CategoriesTable.sortOrder).map { it.toCategory() }
    }

    fun create(displayName: String, colorHex: String, sortOrder: Int): Long = transaction {
        CategoriesTable.insert {
            it[CategoriesTable.displayName] = displayName
            it[CategoriesTable.colorHex] = colorHex
            it[CategoriesTable.sortOrder] = sortOrder
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }
        CategoriesTable.selectAll().orderBy(CategoriesTable.id, SortOrder.DESC).first()[CategoriesTable.id]
    }

    fun update(id: Long, displayName: String, colorHex: String, sortOrder: Int, isActive: Boolean): Boolean = transaction {
        CategoriesTable.update({ CategoriesTable.id eq id }) {
            it[CategoriesTable.displayName] = displayName
            it[CategoriesTable.colorHex] = colorHex
            it[CategoriesTable.sortOrder] = sortOrder
            it[CategoriesTable.isActive] = isActive
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun delete(catId: Long): Boolean = transaction {
        val count = CategoriesTable.deleteWhere { CategoriesTable.id eq catId }
        count > 0
    }

    private fun ResultRow.toCategory() = Category(
        id = this[CategoriesTable.id],
        displayName = this[CategoriesTable.displayName],
        sortOrder = this[CategoriesTable.sortOrder],
        colorHex = this[CategoriesTable.colorHex],
        isActive = this[CategoriesTable.isActive],
        createdAt = this[CategoriesTable.createdAt],
        updatedAt = this[CategoriesTable.updatedAt]
    )
}

// ==================== ProductDao ====================

object ProductDao {
    fun findByCategory(categoryId: Long): List<Product> = transaction {
        ProductsTable.select {
            (ProductsTable.categoryId eq categoryId) and (ProductsTable.isActive eq true)
        }.orderBy(ProductsTable.sortOrder)
            .map { it.toProduct() }
    }

    fun findAllActive(): List<Product> = transaction {
        ProductsTable.select { ProductsTable.isActive eq true }
            .orderBy(ProductsTable.sortOrder)
            .map { it.toProduct() }
    }

    fun findById(id: Long): Product? = transaction {
        ProductsTable.select { ProductsTable.id eq id }
            .map { it.toProduct() }
            .singleOrNull()
    }

    fun findModifierGroupsForProduct(productId: Long): List<Pair<ModifierGroup, List<Modifier>>> = transaction {
        val groupIds = ProductModifierGroupXrefTable
            .select { ProductModifierGroupXrefTable.productId eq productId }
            .map { it[ProductModifierGroupXrefTable.groupId] }

        groupIds.mapNotNull { gid ->
            val group = ModifierGroupsTable.select { ModifierGroupsTable.id eq gid }
                .map { it.toModifierGroup() }
                .singleOrNull() ?: return@mapNotNull null

            val modifiers = ModifiersTable.select {
                (ModifiersTable.groupId eq gid) and (ModifiersTable.isActive eq true)
            }.orderBy(ModifiersTable.sortOrder)
                .map { it.toModifier() }

            group to modifiers
        }.sortedBy { it.first.sortOrder }
    }

    fun updateStock(productId: Long, delta: Int) = transaction {
        ProductsTable.update({ ProductsTable.id eq productId }) {
            with(SqlExpressionBuilder) {
                it.update(ProductsTable.currentStock, ProductsTable.currentStock + delta)
            }
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    fun findAll(): List<Product> = transaction {
        ProductsTable.selectAll().orderBy(ProductsTable.sortOrder).map { it.toProduct() }
    }

    fun create(product: Product): Long = transaction {
        ProductsTable.insert {
            it[categoryId] = product.categoryId
            it[name] = product.name
            it[basePrice] = product.basePrice
            it[description] = product.description
            it[imageUrl] = product.imageUrl
            it[isActive] = product.isActive
            it[hasModifiers] = product.hasModifiers
            it[sortOrder] = product.sortOrder
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }
        val productId = ProductsTable.selectAll().orderBy(ProductsTable.id, SortOrder.DESC).first()[ProductsTable.id]

        // 启用小料时自动关联默认小料组
        if (product.hasModifiers) {
            val defaultGroups = ModifierGroupsTable.select { ModifierGroupsTable.isDefault eq true }
            defaultGroups.forEach { row ->
                ProductModifierGroupXrefTable.insert {
                    it[ProductModifierGroupXrefTable.productId] = productId
                    it[ProductModifierGroupXrefTable.groupId] = row[ModifierGroupsTable.id]
                }
            }
        }

        productId
    }

    fun update(id: Long, product: Product): Boolean = transaction {
        // 检查是否从关闭小料变为开启小料
        val oldProduct = ProductsTable.select { ProductsTable.id eq id }.firstOrNull()
        val wasDisabled = oldProduct != null && !oldProduct[ProductsTable.hasModifiers] && product.hasModifiers

        val count = ProductsTable.update({ ProductsTable.id eq id }) {
            it[categoryId] = product.categoryId
            it[name] = product.name
            it[basePrice] = product.basePrice
            it[description] = product.description
            it[imageUrl] = product.imageUrl
            it[isActive] = product.isActive
            it[hasModifiers] = product.hasModifiers
            it[sortOrder] = product.sortOrder
            it[updatedAt] = System.currentTimeMillis()
        }

        // 开启小料时自动关联默认小料组
        if (wasDisabled) {
            val defaultGroups = ModifierGroupsTable.select { ModifierGroupsTable.isDefault eq true }
            defaultGroups.forEach { row ->
                val exists = ProductModifierGroupXrefTable.select {
                    (ProductModifierGroupXrefTable.productId eq id) and (ProductModifierGroupXrefTable.groupId eq row[ModifierGroupsTable.id])
                }.count() > 0
                if (!exists) {
                    ProductModifierGroupXrefTable.insert {
                        it[productId] = id
                        it[groupId] = row[ModifierGroupsTable.id]
                    }
                }
            }
        }

        count > 0
    }

    fun toggleActive(id: Long): Boolean = transaction {
        val product = ProductsTable.select { ProductsTable.id eq id }.firstOrNull() ?: return@transaction false
        val newActive = !product[ProductsTable.isActive]
        ProductsTable.update({ ProductsTable.id eq id }) {
            it[isActive] = newActive
            it[updatedAt] = System.currentTimeMillis()
        }
        newActive
    }

    fun delete(productId: Long): Boolean = transaction {
        ProductsTable.deleteWhere { id eq productId } > 0
    }

    private fun ResultRow.toProduct() = Product(
        id = this[ProductsTable.id],
        categoryId = this[ProductsTable.categoryId],
        name = this[ProductsTable.name],
        basePrice = this[ProductsTable.basePrice],
        stockWarningThreshold = this[ProductsTable.stockWarningThreshold],
        currentStock = this[ProductsTable.currentStock],
        description = this[ProductsTable.description],
        imageUrl = this[ProductsTable.imageUrl],
        isActive = this[ProductsTable.isActive],
        hasModifiers = this[ProductsTable.hasModifiers],
        sortOrder = this[ProductsTable.sortOrder],
        createdAt = this[ProductsTable.createdAt],
        updatedAt = this[ProductsTable.updatedAt]
    )

    private fun ResultRow.toModifierGroup() = ModifierGroup(
        id = this[ModifierGroupsTable.id],
        name = this[ModifierGroupsTable.name],
        isRequired = this[ModifierGroupsTable.isRequired],
        maxSelection = this[ModifierGroupsTable.maxSelection],
        minSelection = this[ModifierGroupsTable.minSelection],
        sortOrder = this[ModifierGroupsTable.sortOrder],
        isActive = this[ModifierGroupsTable.isActive],
        createdAt = this[ModifierGroupsTable.createdAt],
        updatedAt = this[ModifierGroupsTable.updatedAt]
    )

    private fun ResultRow.toModifier(): Modifier {
        val sizeOverridesStr = this[ModifiersTable.sizeOverrides]
        val sizeOverrides: Map<String, Double> = try {
            json.decodeFromString(sizeOverridesStr)
        } catch (_: Exception) {
            emptyMap()
        }
        return Modifier(
            id = this[ModifiersTable.id],
            groupId = this[ModifiersTable.groupId],
            name = this[ModifiersTable.name],
            additionalPrice = this[ModifiersTable.additionalPrice],
            sizeOverrides = sizeOverrides,
            isActive = this[ModifiersTable.isActive],
            sortOrder = this[ModifiersTable.sortOrder],
            createdAt = this[ModifiersTable.createdAt],
            updatedAt = this[ModifiersTable.updatedAt]
        )
    }
}

// ==================== OrderDao ====================

object OrderDao {
    fun create(order: Order, items: List<OrderItem>): Order = transaction {
        OrdersTable.insert {
            it[orderNumber] = order.orderNumber
            it[tableNumber] = order.tableNumber
            it[status] = order.status.name
            it[totalAmount] = order.totalAmount
            it[itemCount] = order.itemCount
            it[paymentMethod] = order.paymentMethod
            it[notes] = order.notes
            it[createdBy] = order.createdBy
            it[createdAt] = order.createdAt
            it[updatedAt] = order.updatedAt
            it[syncStatus] = order.syncStatus
        }
        val orderId = OrdersTable.selectAll().orderBy(OrdersTable.id, SortOrder.DESC).first()[OrdersTable.id]

        items.forEach { item ->
            OrderItemsTable.insert {
                it[OrderItemsTable.orderId] = orderId
                it[productId] = item.productId
                it[productName] = item.productName
                it[quantity] = item.quantity
                it[priceAtSnap] = item.priceAtSnap
                it[modifierTotal] = item.modifierTotal
                it[lineTotal] = item.lineTotal
                it[appliedModifiersJson] = item.appliedModifiersJson
                it[selectedSize] = item.selectedSize
                it[notes] = item.notes
                it[createdBy] = item.createdBy
                it[createdAt] = item.createdAt
                it[updatedAt] = item.updatedAt
            }
        }

        order.copy(id = orderId)
    }

    fun findById(id: Long): Order? = transaction {
        OrdersTable.select { OrdersTable.id eq id }
            .map { it.toOrder() }
            .singleOrNull()
    }

    fun findByStatus(status: OrderStatus): List<Order> = transaction {
        OrdersTable.select { OrdersTable.status eq status.name }
            .orderBy(OrdersTable.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun findAll(): List<Order> = transaction {
        OrdersTable.selectAll()
            .orderBy(OrdersTable.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun countToday(): Long = transaction {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        OrdersTable.select { OrdersTable.createdAt greaterEq todayStart }.count()
    }

    fun updateStatus(id: Long, status: OrderStatus): Boolean = transaction {
        val now = System.currentTimeMillis()
        val updateCount = OrdersTable.update({ OrdersTable.id eq id }) {
            it[OrdersTable.status] = status.name
            it[updatedAt] = now
            if (status == OrderStatus.COMPLETED) {
                it[completedAt] = now
            }
        }
        updateCount > 0
    }

    fun findByTableNumber(tableNumber: String): Order? = transaction {
        OrdersTable.select {
            (OrdersTable.tableNumber eq tableNumber) and
                (OrdersTable.status inList listOf(
                    OrderStatus.PENDING.name,
                    OrderStatus.PREPARING.name,
                    OrderStatus.READY.name
                ))
        }.orderBy(OrdersTable.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
            .firstOrNull()
    }

    fun findOccupiedTables(): List<String> = transaction {
        OrdersTable.select {
            OrdersTable.status inList listOf(
                OrderStatus.PENDING.name,
                OrderStatus.PREPARING.name,
                OrderStatus.READY.name
            )
        }.map { it[OrdersTable.tableNumber] }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun ResultRow.toOrder() = Order(
        id = this[OrdersTable.id],
        orderNumber = this[OrdersTable.orderNumber],
        tableNumber = this[OrdersTable.tableNumber],
        status = OrderStatus.valueOf(this[OrdersTable.status]),
        totalAmount = this[OrdersTable.totalAmount],
        itemCount = this[OrdersTable.itemCount],
        paymentMethod = this[OrdersTable.paymentMethod],
        notes = this[OrdersTable.notes],
        createdBy = this[OrdersTable.createdBy],
        createdAt = this[OrdersTable.createdAt],
        updatedAt = this[OrdersTable.updatedAt],
        completedAt = this[OrdersTable.completedAt],
        syncStatus = this[OrdersTable.syncStatus]
    )
}

// ==================== OrderItemDao ====================

object OrderItemDao {
    fun findByOrderId(orderId: Long): List<OrderItem> = transaction {
        OrderItemsTable.select { OrderItemsTable.orderId eq orderId }
            .map { it.toOrderItem() }
    }

    private fun ResultRow.toOrderItem() = OrderItem(
        id = this[OrderItemsTable.id],
        orderId = this[OrderItemsTable.orderId],
        productId = this[OrderItemsTable.productId],
        productName = this[OrderItemsTable.productName],
        quantity = this[OrderItemsTable.quantity],
        priceAtSnap = this[OrderItemsTable.priceAtSnap],
        modifierTotal = this[OrderItemsTable.modifierTotal],
        lineTotal = this[OrderItemsTable.lineTotal],
        appliedModifiersJson = this[OrderItemsTable.appliedModifiersJson],
        selectedSize = this[OrderItemsTable.selectedSize],
        notes = this[OrderItemsTable.notes],
        createdBy = this[OrderItemsTable.createdBy],
        createdAt = this[OrderItemsTable.createdAt],
        updatedAt = this[OrderItemsTable.updatedAt]
    )
}

// ==================== ModifierGroupDao ====================

object ModifierGroupDao {
    fun findAll(): List<ModifierGroup> = transaction {
        ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.sortOrder).map { it.toModifierGroup() }
    }

    fun findAllActive(): List<ModifierGroup> = transaction {
        ModifierGroupsTable.select { ModifierGroupsTable.isActive eq true }
            .orderBy(ModifierGroupsTable.sortOrder).map { it.toModifierGroup() }
    }

    fun findDefaults(): List<ModifierGroup> = transaction {
        ModifierGroupsTable.select { (ModifierGroupsTable.isDefault eq true) and (ModifierGroupsTable.isActive eq true) }
            .orderBy(ModifierGroupsTable.sortOrder).map { it.toModifierGroup() }
    }

    fun findById(id: Long): ModifierGroup? = transaction {
        ModifierGroupsTable.select { ModifierGroupsTable.id eq id }
            .map { it.toModifierGroup() }.singleOrNull()
    }

    fun create(name: String, isRequired: Boolean, isDefault: Boolean, maxSelection: Int, minSelection: Int, sortOrder: Int): Long = transaction {
        ModifierGroupsTable.insert {
            it[ModifierGroupsTable.name] = name
            it[ModifierGroupsTable.isRequired] = isRequired
            it[ModifierGroupsTable.isDefault] = isDefault
            it[ModifierGroupsTable.maxSelection] = maxSelection
            it[ModifierGroupsTable.minSelection] = minSelection
            it[ModifierGroupsTable.sortOrder] = sortOrder
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }
        ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.id, SortOrder.DESC).first()[ModifierGroupsTable.id]
    }

    fun update(id: Long, name: String, isRequired: Boolean, isDefault: Boolean, maxSelection: Int, minSelection: Int, sortOrder: Int, isActive: Boolean): Boolean = transaction {
        ModifierGroupsTable.update({ ModifierGroupsTable.id eq id }) {
            it[ModifierGroupsTable.name] = name
            it[ModifierGroupsTable.isRequired] = isRequired
            it[ModifierGroupsTable.isDefault] = isDefault
            it[ModifierGroupsTable.maxSelection] = maxSelection
            it[ModifierGroupsTable.minSelection] = minSelection
            it[ModifierGroupsTable.sortOrder] = sortOrder
            it[ModifierGroupsTable.isActive] = isActive
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        // 先删关联的小料
        ModifiersTable.deleteWhere { ModifiersTable.groupId eq id }
        // 删商品关联
        ProductModifierGroupXrefTable.deleteWhere { ProductModifierGroupXrefTable.groupId eq id }
        ModifierGroupsTable.deleteWhere { ModifierGroupsTable.id eq id } > 0
    }

    fun getProductsForGroup(groupId: Long): List<Long> = transaction {
        ProductModifierGroupXrefTable
            .select { ProductModifierGroupXrefTable.groupId eq groupId }
            .map { it[ProductModifierGroupXrefTable.productId] }
    }

    fun setProductsForGroup(groupId: Long, productIds: List<Long>) = transaction {
        ProductModifierGroupXrefTable.deleteWhere { ProductModifierGroupXrefTable.groupId eq groupId }
        productIds.forEach { pid ->
            ProductModifierGroupXrefTable.insert {
                it[productId] = pid
                it[ProductModifierGroupXrefTable.groupId] = groupId
            }
        }
    }

    private fun ResultRow.toModifierGroup() = ModifierGroup(
        id = this[ModifierGroupsTable.id],
        name = this[ModifierGroupsTable.name],
        isRequired = this[ModifierGroupsTable.isRequired],
        isDefault = this[ModifierGroupsTable.isDefault],
        maxSelection = this[ModifierGroupsTable.maxSelection],
        minSelection = this[ModifierGroupsTable.minSelection],
        sortOrder = this[ModifierGroupsTable.sortOrder],
        isActive = this[ModifierGroupsTable.isActive],
        createdAt = this[ModifierGroupsTable.createdAt],
        updatedAt = this[ModifierGroupsTable.updatedAt]
    )
}

// ==================== ModifierDao ====================

object ModifierDao {
    fun findByGroupId(groupId: Long): List<Modifier> = transaction {
        ModifiersTable.select { ModifiersTable.groupId eq groupId }
            .orderBy(ModifiersTable.sortOrder).map { it.toModifier() }
    }

    fun findById(id: Long): Modifier? = transaction {
        ModifiersTable.select { ModifiersTable.id eq id }
            .map { it.toModifier() }.singleOrNull()
    }

    fun create(groupId: Long, name: String, additionalPrice: Double, sortOrder: Int): Long = transaction {
        ModifiersTable.insert {
            it[ModifiersTable.groupId] = groupId
            it[ModifiersTable.name] = name
            it[ModifiersTable.additionalPrice] = additionalPrice
            it[ModifiersTable.sortOrder] = sortOrder
            it[createdAt] = System.currentTimeMillis()
            it[updatedAt] = System.currentTimeMillis()
        }
        ModifiersTable.selectAll().orderBy(ModifiersTable.id, SortOrder.DESC).first()[ModifiersTable.id]
    }

    fun update(id: Long, name: String, additionalPrice: Double, sortOrder: Int, isActive: Boolean): Boolean = transaction {
        ModifiersTable.update({ ModifiersTable.id eq id }) {
            it[ModifiersTable.name] = name
            it[ModifiersTable.additionalPrice] = additionalPrice
            it[ModifiersTable.sortOrder] = sortOrder
            it[ModifiersTable.isActive] = isActive
            it[updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        ModifiersTable.deleteWhere { ModifiersTable.id eq id } > 0
    }

    private fun ResultRow.toModifier(): Modifier {
        val sizeOverridesStr = this[ModifiersTable.sizeOverrides]
        val sizeOverrides: Map<String, Double> = try {
            json.decodeFromString(sizeOverridesStr)
        } catch (_: Exception) { emptyMap() }
        return Modifier(
            id = this[ModifiersTable.id],
            groupId = this[ModifiersTable.groupId],
            name = this[ModifiersTable.name],
            additionalPrice = this[ModifiersTable.additionalPrice],
            sizeOverrides = sizeOverrides,
            isActive = this[ModifiersTable.isActive],
            sortOrder = this[ModifiersTable.sortOrder],
            createdAt = this[ModifiersTable.createdAt],
            updatedAt = this[ModifiersTable.updatedAt]
        )
    }
}

// ==================== SettingsDao ====================

object SettingsDao {
    fun get(key: String): String? = transaction {
        SettingsTable.select { SettingsTable.key eq key }
            .map { it[SettingsTable.value] }
            .singleOrNull()
    }

    fun getAll(): Map<String, String> = transaction {
        SettingsTable.selectAll().associate { it[SettingsTable.key] to it[SettingsTable.value] }
    }

    fun set(key: String, value: String) = transaction {
        val exists = SettingsTable.select { SettingsTable.key eq key }.count() > 0
        if (exists) {
            SettingsTable.update({ SettingsTable.key eq key }) {
                it[SettingsTable.value] = value
            }
        } else {
            SettingsTable.insert {
                it[SettingsTable.key] = key
                it[SettingsTable.value] = value
            }
        }
    }

    fun setAll(settings: Map<String, String>) = transaction {
        settings.forEach { (k, v) -> set(k, v) }
    }
}
