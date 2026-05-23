package com.ordering.system.routes

import com.ordering.system.db.CategoryDao
import com.ordering.system.db.ProductDao
import com.ordering.system.domain.model.Product
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class CategoryResponse(
    val id: Long, val displayName: String, val sortOrder: Int,
    val colorHex: String, val isActive: Boolean
)

@Serializable
data class ProductResponse(
    val id: Long, val categoryId: Long, val name: String,
    val basePrice: Double, val description: String,
    val hasModifiers: Boolean = false,
    val imageUrl: String?, val isActive: Boolean, val sortOrder: Int,
    val currentStock: Int
)

@Serializable
data class ModifierGroupResponse(
    val id: Long, val name: String, val isRequired: Boolean,
    val maxSelection: Int, val minSelection: Int, val sortOrder: Int,
    val modifiers: List<ModifierResponse>
)

@Serializable
data class ModifierResponse(
    val id: Long, val name: String, val additionalPrice: Double,
    val sizeOverrides: Map<String, Double>, val sortOrder: Int
)

@Serializable
data class ProductDetailResponse(
    val product: ProductResponse,
    val modifierGroups: List<ModifierGroupResponse>
)

@Serializable
data class CreateProductRequest(
    val categoryId: Long,
    val name: String,
    val basePrice: Double,
    val description: String = "",
    val hasModifiers: Boolean = false,
    val imageUrl: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class UpdateProductRequest(
    val categoryId: Long,
    val name: String,
    val basePrice: Double,
    val description: String = "",
    val hasModifiers: Boolean = false,
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

@Serializable
data class CreateCategoryRequest(
    val displayName: String,
    val colorHex: String = "#FFFFFF",
    val sortOrder: Int = 0
)

@Serializable
data class UpdateCategoryRequest(
    val displayName: String,
    val colorHex: String = "#FFFFFF",
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

fun Product.toResponse() = ProductResponse(
    id, categoryId, name, basePrice, description, hasModifiers,
    imageUrl, isActive, sortOrder, currentStock
)

fun Route.menuRoutes() {
    // ==================== 分类 ====================
    get("/api/categories") {
        val categories = CategoryDao.findAllActive().map {
            CategoryResponse(it.id, it.displayName, it.sortOrder, it.colorHex, it.isActive)
        }
        call.respond(HttpStatusCode.OK, categories)
    }

    get("/api/admin/categories") {
        val categories = CategoryDao.findAll().map {
            CategoryResponse(it.id, it.displayName, it.sortOrder, it.colorHex, it.isActive)
        }
        call.respond(HttpStatusCode.OK, categories)
    }

    post("/api/categories") {
        val req = call.receive<CreateCategoryRequest>()
        val id = CategoryDao.create(req.displayName, req.colorHex, req.sortOrder)
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }

    put("/api/categories/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val req = call.receive<UpdateCategoryRequest>()
        val ok = CategoryDao.update(id, req.displayName, req.colorHex, req.sortOrder, req.isActive)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"分类不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    delete("/api/categories/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val ok = CategoryDao.delete(id)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"分类不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    // ==================== 商品 ====================
    get("/api/products") {
        val categoryId = call.request.queryParameters["categoryId"]?.toLongOrNull()
        val all = call.request.queryParameters["all"] == "true"
        val products = when {
            categoryId != null -> ProductDao.findByCategory(categoryId)
            all -> ProductDao.findAll()
            else -> ProductDao.findAllActive()
        }
        call.respond(HttpStatusCode.OK, products.map { it.toResponse() })
    }

    get("/api/products/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val product = ProductDao.findById(id)
        if (product == null) {
            call.respondText("""{"error":"商品不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            return@get
        }
        val modifierGroups = ProductDao.findModifierGroupsForProduct(id).map { (group, modifiers) ->
            ModifierGroupResponse(
                group.id, group.name, group.isRequired,
                group.maxSelection, group.minSelection, group.sortOrder,
                modifiers.map {
                    ModifierResponse(it.id, it.name, it.additionalPrice, it.sizeOverrides, it.sortOrder)
                }
            )
        }
        call.respond(HttpStatusCode.OK, ProductDetailResponse(product.toResponse(), modifierGroups))
    }

    post("/api/products") {
        val req = call.receive<CreateProductRequest>()
        val id = ProductDao.create(Product(
            categoryId = req.categoryId,
            name = req.name,
            basePrice = req.basePrice,
            description = req.description,
            hasModifiers = req.hasModifiers,
            imageUrl = req.imageUrl,
            sortOrder = req.sortOrder
        ))
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }

    put("/api/products/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val req = call.receive<UpdateProductRequest>()
        val ok = ProductDao.update(id, Product(
            categoryId = req.categoryId,
            name = req.name,
            basePrice = req.basePrice,
            description = req.description,
            hasModifiers = req.hasModifiers,
            imageUrl = req.imageUrl,
            isActive = req.isActive,
            sortOrder = req.sortOrder
        ))
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"商品不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }

    patch("/api/products/{id}/toggle") {
        val id = call.parameters["id"]!!.toLong()
        val newActive = ProductDao.toggleActive(id)
        call.respond(HttpStatusCode.OK, mapOf("isActive" to newActive))
    }

    delete("/api/products/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val ok = ProductDao.delete(id)
        if (ok) call.respond(HttpStatusCode.OK, mapOf("success" to true))
        else call.respondText("""{"error":"商品不存在"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
    }
}
