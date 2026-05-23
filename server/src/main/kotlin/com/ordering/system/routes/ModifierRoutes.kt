package com.ordering.system.routes

import com.ordering.system.db.ModifierDao
import com.ordering.system.db.ModifierGroupDao
import com.ordering.system.db.ProductModifierGroupXrefTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ModifierGroupResp(
    val id: Long, val name: String, val isRequired: Boolean,
    val isDefault: Boolean = false,
    val maxSelection: Int, val minSelection: Int, val sortOrder: Int,
    val isActive: Boolean, val productIds: List<Long> = emptyList(),
    val modifiers: List<ModifierResp> = emptyList()
)

@Serializable
data class ModifierResp(
    val id: Long, val groupId: Long, val name: String,
    val additionalPrice: Double, val sortOrder: Int, val isActive: Boolean
)

@Serializable
data class CreateModifierGroupReq(
    val name: String, val isRequired: Boolean = false,
    val isDefault: Boolean = false,
    val maxSelection: Int = 0, val minSelection: Int = 0,
    val sortOrder: Int = 0, val productIds: List<Long> = emptyList()
)

@Serializable
data class UpdateModifierGroupReq(
    val name: String, val isRequired: Boolean = false,
    val isDefault: Boolean = false,
    val maxSelection: Int = 0, val minSelection: Int = 0,
    val sortOrder: Int = 0, val isActive: Boolean = true,
    val productIds: List<Long> = emptyList()
)

@Serializable
data class CreateModifierReq(
    val groupId: Long, val name: String,
    val additionalPrice: Double = 0.0, val sortOrder: Int = 0
)

@Serializable
data class UpdateModifierReq(
    val name: String, val additionalPrice: Double = 0.0,
    val sortOrder: Int = 0, val isActive: Boolean = true
)

fun Route.modifierRoutes() {
    // ==================== 商品-小料组关联 ====================
    delete("/api/products/{productId}/modifier-groups/{groupId}") {
        val productId = call.parameters["productId"]!!.toLong()
        val groupId = call.parameters["groupId"]!!.toLong()
        transaction {
            ProductModifierGroupXrefTable.deleteWhere {
                (ProductModifierGroupXrefTable.productId eq productId) and (ProductModifierGroupXrefTable.groupId eq groupId)
            }
        }
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }

    // ==================== 小料组 ====================
    get("/api/modifier-groups") {
        val all = call.request.queryParameters["all"] == "true"
        val groups = if (all) ModifierGroupDao.findAll() else ModifierGroupDao.findAllActive()
        val result = groups.map { g ->
            val mods = ModifierDao.findByGroupId(g.id)
            val productIds = ModifierGroupDao.getProductsForGroup(g.id)
            ModifierGroupResp(
                g.id, g.name, g.isRequired, g.isDefault, g.maxSelection, g.minSelection,
                g.sortOrder, g.isActive, productIds,
                mods.map { ModifierResp(it.id, it.groupId, it.name, it.additionalPrice, it.sortOrder, it.isActive) }
            )
        }
        call.respond(HttpStatusCode.OK, result)
    }

    get("/api/modifier-groups/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val g = ModifierGroupDao.findById(id)
        if (g == null) {
            call.respondText("""{"error":"小料组不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            return@get
        }
        val mods = ModifierDao.findByGroupId(g.id)
        val productIds = ModifierGroupDao.getProductsForGroup(g.id)
        call.respond(HttpStatusCode.OK, ModifierGroupResp(
            g.id, g.name, g.isRequired, g.isDefault, g.maxSelection, g.minSelection,
            g.sortOrder, g.isActive, productIds,
            mods.map { ModifierResp(it.id, it.groupId, it.name, it.additionalPrice, it.sortOrder, it.isActive) }
        ))
    }

    post("/api/modifier-groups") {
        val req = call.receive<CreateModifierGroupReq>()
        val id = ModifierGroupDao.create(req.name, req.isRequired, req.isDefault, req.maxSelection, req.minSelection, req.sortOrder)
        if (req.productIds.isNotEmpty()) {
            ModifierGroupDao.setProductsForGroup(id, req.productIds)
        }
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }

    put("/api/modifier-groups/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val req = call.receive<UpdateModifierGroupReq>()
        val ok = ModifierGroupDao.update(id, req.name, req.isRequired, req.isDefault, req.maxSelection, req.minSelection, req.sortOrder, req.isActive)
        if (ok && req.productIds.isNotEmpty()) {
            ModifierGroupDao.setProductsForGroup(id, req.productIds)
        }
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"小料组不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    delete("/api/modifier-groups/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val ok = ModifierGroupDao.delete(id)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"小料组不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    // ==================== 小料项 ====================
    get("/api/modifiers") {
        val groupId = call.request.queryParameters["groupId"]?.toLongOrNull()
        val mods = if (groupId != null) ModifierDao.findByGroupId(groupId) else emptyList()
        call.respond(HttpStatusCode.OK, mods.map { ModifierResp(it.id, it.groupId, it.name, it.additionalPrice, it.sortOrder, it.isActive) })
    }

    post("/api/modifiers") {
        val req = call.receive<CreateModifierReq>()
        val id = ModifierDao.create(req.groupId, req.name, req.additionalPrice, req.sortOrder)
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }

    put("/api/modifiers/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val req = call.receive<UpdateModifierReq>()
        val ok = ModifierDao.update(id, req.name, req.additionalPrice, req.sortOrder, req.isActive)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"小料不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    delete("/api/modifiers/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val ok = ModifierDao.delete(id)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"小料不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }
}
