package com.ordering.system.db

import com.ordering.system.domain.model.OrderStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        Database.connect("jdbc:sqlite:ordering.db", driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(
                CategoriesTable,
                ProductsTable,
                ModifierGroupsTable,
                ModifiersTable,
                ProductModifierGroupXrefTable,
                OrdersTable,
                OrderItemsTable,
                SettingsTable
            )
            // 迁移：旧 PENDING 订单更新为 PREPARING
            OrdersTable.update({ OrdersTable.status eq "PENDING" }) {
                it[status] = OrderStatus.PREPARING.name
            }
            insertSampleDataIfEmpty()
            insertDefaultSettings()
        }
    }

    private fun insertSampleDataIfEmpty() {
        val count = CategoriesTable.selectAll().count()
        if (count > 0) return

        // 分类
        val catNames = listOf("板面", "小菜", "饮品", "主食")
        val catColors = listOf("#FF6B35", "#4CAF50", "#2196F3", "#9C27B0")
        val catIds = mutableListOf<Long>()
        catNames.forEachIndexed { i, name ->
            CategoriesTable.insert {
                it[displayName] = name
                it[sortOrder] = i
                it[colorHex] = catColors[i]
            }
            // 获取刚插入的 id
            val id = CategoriesTable.selectAll().orderBy(CategoriesTable.id, SortOrder.DESC).first()[CategoriesTable.id]
            catIds.add(id)
        }

        // 修饰符组
        val methodGroupId = run {
            ModifierGroupsTable.insert {
                it[name] = "做法"
                it[isRequired] = true
                it[isDefault] = true
                it[maxSelection] = 1
                it[minSelection] = 1
                it[sortOrder] = 0
            }
            ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.id, SortOrder.DESC).first()[ModifierGroupsTable.id]
        }

        val noodleGroupId = run {
            ModifierGroupsTable.insert {
                it[name] = "面条"
                it[isRequired] = true
                it[isDefault] = true
                it[maxSelection] = 1
                it[minSelection] = 1
                it[sortOrder] = 1
            }
            ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.id, SortOrder.DESC).first()[ModifierGroupsTable.id]
        }

        val spicyGroupId = run {
            ModifierGroupsTable.insert {
                it[name] = "辣度"
                it[isRequired] = true
                it[isDefault] = true
                it[maxSelection] = 1
                it[minSelection] = 1
            }
            ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.id, SortOrder.DESC).first()[ModifierGroupsTable.id]
        }

        val extraGroupId = run {
            ModifierGroupsTable.insert {
                it[name] = "卤味"
                it[isRequired] = false
                it[isDefault] = true
                it[maxSelection] = 0  // 0 = 不限数量
            }
            ModifierGroupsTable.selectAll().orderBy(ModifierGroupsTable.id, SortOrder.DESC).first()[ModifierGroupsTable.id]
        }

        // 做法选项
        listOf("汤面" to 0.0, "拌面" to 0.0).forEach { (name, price) ->
            ModifiersTable.insert {
                it[groupId] = methodGroupId
                it[ModifiersTable.name] = name
                it[additionalPrice] = price
            }
        }

        // 面条选项
        listOf("宽面" to 0.0, "细面" to 0.0).forEach { (name, price) ->
            ModifiersTable.insert {
                it[groupId] = noodleGroupId
                it[ModifiersTable.name] = name
                it[additionalPrice] = price
            }
        }

        listOf("不辣" to 0.0, "微辣" to 0.0, "中辣" to 0.0, "特辣" to 0.0).forEach { (name, price) ->
            ModifiersTable.insert {
                it[groupId] = spicyGroupId
                it[ModifiersTable.name] = name
                it[additionalPrice] = price
            }
        }

        listOf("卤蛋" to 2.0, "卤肉" to 5.0, "豆皮" to 2.0, "海带结" to 2.0, "丸子" to 3.0, "豆腐" to 2.0, "鸡腿" to 6.0, "鸡翅" to 5.0).forEach { (name, price) ->
            ModifiersTable.insert {
                it[groupId] = extraGroupId
                it[ModifiersTable.name] = name
                it[additionalPrice] = price
            }
        }

        // 商品
        data class ProductSeed(
            val name: String, val catIdx: Int, val price: Double,
            val desc: String, val hasModifiers: Boolean = false
        )

        val products = listOf(
            ProductSeed("招牌板面", 0, 12.0, "老李招牌手擀板面，劲道爽滑", true),
            ProductSeed("牛肉板面", 0, 15.0, "精选牛腩板面，肉烂汤浓", true),
            ProductSeed("羊肉板面", 0, 16.0, "鲜羊肉板面，鲜香四溢", true),
            ProductSeed("素板面", 0, 10.0, "时蔬板面，清淡可口", true),
            ProductSeed("凉拌黄瓜", 1, 8.0, "爽脆开胃"),
            ProductSeed("凉拌豆腐丝", 1, 8.0, "香辣入味"),
            ProductSeed("卤蛋", 1, 2.0, "秘制卤蛋"),
            ProductSeed("可乐", 2, 3.0, "冰镇可口可乐"),
            ProductSeed("雪碧", 2, 3.0, "冰镇雪碧"),
            ProductSeed("豆浆", 2, 4.0, "现磨豆浆"),
            ProductSeed("烧饼", 3, 2.0, "酥脆烧饼"),
            ProductSeed("米饭", 3, 2.0, "东北大米饭")
        )

        products.forEach { p ->
            ProductsTable.insert {
                it[name] = p.name
                it[categoryId] = catIds[p.catIdx]
                it[basePrice] = p.price
                it[description] = p.desc
                it[hasModifiers] = p.hasModifiers
            }
            val pid = ProductsTable.selectAll().orderBy(ProductsTable.id, SortOrder.DESC).first()[ProductsTable.id]

            if (p.hasModifiers) {
                listOf(methodGroupId, noodleGroupId, spicyGroupId, extraGroupId).forEach { gid ->
                    ProductModifierGroupXrefTable.insert {
                        it[productId] = pid
                        it[groupId] = gid
                    }
                }
            }
        }
    }

    private fun insertDefaultSettings() {
        val defaults = mapOf(
            "store_name" to "老李板面馆",
            "store_phone" to "0371-8888-6666",
            "store_tagline" to "正宗手擀板面 . 量大实惠",
            "receipt_footer" to "感谢光临，欢迎再来!"
        )
        defaults.forEach { (k, v) ->
            val exists = SettingsTable.select { SettingsTable.key eq k }.count() > 0
            if (!exists) {
                SettingsTable.insert {
                    it[key] = k
                    it[value] = v
                }
            }
        }
    }
}
