package com.ordering.system.routes

import com.ordering.system.db.OrderDao
import com.ordering.system.db.OrderItemDao
import com.ordering.system.db.ProductDao
import com.ordering.system.domain.engine.OrderItemAdapter
import com.ordering.system.domain.engine.OrderValidator
import com.ordering.system.domain.engine.PricingEngine
import com.ordering.system.domain.model.*
import com.ordering.system.print.*
import com.ordering.system.print.connection.NetworkPrinterConnector
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// 用于解析 appliedModifiersJson
@kotlinx.serialization.Serializable
private data class ModSnapshot(
    val groupId: Long = 0,
    val groupName: String = "",
    val modifierId: Long = 0,
    val modifierName: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1
)

private val pricingEngine = PricingEngine()
private val orderItemAdapter = OrderItemAdapter()
private val orderValidator = OrderValidator()

@Serializable
data class CreateOrderRequest(
    val items: List<CartItemRequest>,
    val tableNumber: String = "",
    val notes: String = "",
    val createdBy: String = ""
)

@Serializable
data class CartItemRequest(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val basePrice: Double,
    val selectedSize: String = "",
    val selectedModifiers: List<SelectedModifierRequest> = emptyList(),
    val notes: String = ""
)

@Serializable
data class SelectedModifierRequest(
    val groupId: Long,
    val groupName: String,
    val modifierId: Long,
    val modifierName: String,
    val additionalPrice: Double,
    val quantity: Int = 1
)

@Serializable
data class UpdateStatusRequest(val status: String)

@Serializable
data class ErrorResponse(val error: String, val violations: List<ViolationResponse>? = null)

@Serializable
data class ViolationResponse(val type: String, val productId: String, val message: String)

@Serializable
data class OrderResponse(
    val id: Long, val orderNumber: String, val tableNumber: String,
    val status: String, val totalAmount: Double,
    val itemCount: Int, val paymentMethod: String, val notes: String,
    val createdBy: String, val createdAt: Long, val completedAt: Long?,
    val items: List<OrderItemResponse> = emptyList()
)

@Serializable
data class OrderItemResponse(
    val id: Long, val productId: Long, val productName: String,
    val quantity: Int, val priceAtSnap: Double,
    val modifierTotal: Double, val lineTotal: Double,
    val appliedModifiersJson: String, val selectedSize: String, val notes: String
)

fun Order.toResponse(items: List<OrderItem> = emptyList()) = OrderResponse(
    id = id, orderNumber = orderNumber, tableNumber = tableNumber,
    status = status.name, totalAmount = totalAmount,
    itemCount = itemCount, paymentMethod = paymentMethod, notes = notes,
    createdBy = createdBy, createdAt = createdAt, completedAt = completedAt,
    items = items.map {
        OrderItemResponse(
            it.id, it.productId, it.productName, it.quantity,
            it.priceAtSnap, it.modifierTotal,
            it.lineTotal, it.appliedModifiersJson,
            it.selectedSize, it.notes
        )
    }
)

fun Route.orderRoutes() {
    post("/api/orders") {
        val request = call.receive<CreateOrderRequest>()

        val cartItems = request.items.map { item ->
            CartItem(
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                basePrice = item.basePrice,
                selectedSize = item.selectedSize,
                selectedModifiers = item.selectedModifiers.map {
                    SelectedModifier(
                        groupId = it.groupId, groupName = it.groupName,
                        modifierId = it.modifierId, modifierName = it.modifierName,
                        additionalPrice = it.additionalPrice, quantity = it.quantity
                    )
                },
                notes = item.notes
            )
        }

        val products = cartItems.mapNotNull { ProductDao.findById(it.productId) }
            .associateBy { it.id }
        val productModifierGroups = products.keys.associateWith { pid ->
            ProductDao.findModifierGroupsForProduct(pid)
        }

        val validation = orderValidator.validateOrder(cartItems, productModifierGroups, products)
        if (!validation.isValid) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                error = "订单校验失败",
                violations = validation.violations.map {
                    ViolationResponse(it.type.name, it.productId.toString(), it.message)
                }
            ))
            return@post
        }

        val pricing = pricingEngine.calculateOrder(cartItems)

        // 取餐号：3 位数，每天重置
        val todayPrefix = SimpleDateFormat("MMdd").format(Date())
        val seq = (OrderDao.findAll().size + 1) % 1000
        val orderNumber = String.format("%03d", seq)

        val orderItems = cartItems.zip(pricing.itemPricings).map { (item, itemPricing) ->
            orderItemAdapter.toOrderItem(
                pricing = itemPricing,
                selectedModifiers = item.selectedModifiers,
                selectedSize = item.selectedSize,
                notes = item.notes,
                createdBy = request.createdBy
            )
        }

        val order = Order(
            orderNumber = orderNumber,
            tableNumber = request.tableNumber,
            status = OrderStatus.PENDING,
            totalAmount = pricing.total,
            itemCount = pricing.itemCount,
            notes = request.notes,
            createdBy = request.createdBy
        )

        val savedOrder = OrderDao.create(order, orderItems)

        cartItems.forEach { cartItem ->
            val product = products[cartItem.productId]
            if (product != null && product.currentStock >= 0) {
                ProductDao.updateStock(cartItem.productId, -cartItem.quantity)
            }
        }

        call.respond(HttpStatusCode.Created, savedOrder.toResponse(orderItems))
    }

    get("/api/orders") {
        val status = call.request.queryParameters["status"]
        val orders = if (status != null) {
            OrderDao.findByStatus(OrderStatus.valueOf(status))
        } else {
            OrderDao.findAll()
        }
        call.respond(HttpStatusCode.OK, orders.map { it.toResponse() })
    }

    get("/api/orders/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val order = OrderDao.findById(id)
        if (order == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("订单不存在"))
            return@get
        }
        val items = OrderItemDao.findByOrderId(id)
        call.respond(HttpStatusCode.OK, order.toResponse(items))
    }

    patch("/api/orders/{id}/status") {
        val id = call.parameters["id"]!!.toLong()
        val request = call.receive<UpdateStatusRequest>()
        val status = try {
            OrderStatus.valueOf(request.status)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("无效的订单状态"))
            return@patch
        }
        val updated = OrderDao.updateStatus(id, status)
        if (updated) {
            val order = OrderDao.findById(id)!!
            call.respond(HttpStatusCode.OK, order.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("订单不存在"))
        }
    }

    post("/api/orders/{id}/print") {
        val id = call.parameters["id"]!!.toLong()
        val order = OrderDao.findById(id)
        if (order == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("订单不存在"))
            return@post
        }
        val items = OrderItemDao.findByOrderId(id)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val settings = com.ordering.system.db.SettingsDao.getAll()
        val printData = OrderPrintData(
            orderId = order.id,
            orderNumber = order.orderNumber,
            tableNumber = order.tableNumber,
            createdAt = dateFormat.format(Date(order.createdAt)),
            items = items.map { item ->
                val mods = try {
                    kotlinx.serialization.json.Json.decodeFromString<List<ModSnapshot>>(item.appliedModifiersJson)
                } catch (_: Exception) { emptyList() }
                OrderItemPrintData(
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.priceAtSnap,
                    lineTotal = item.lineTotal,
                    selectedSize = item.selectedSize,
                    modifiers = mods.map { ModifierPrintData(it.groupName, it.modifierName, it.price, it.quantity) },
                    notes = item.notes
                )
            },
            totalAmount = order.totalAmount,
            notes = order.notes,
            createdBy = order.createdBy,
            storeName = settings["store_name"] ?: "",
            storePhone = settings["store_phone"] ?: "",
            storeTagline = settings["store_tagline"] ?: "",
            receiptFooter = settings["receipt_footer"] ?: "感谢光临，欢迎再来!"
        )
        try {
            val connector = NetworkPrinterConnector("192.168.1.100")
            val driver = PosPrinterDriver(connector)
            driver.open()
            // 顾客联（有价格）先打印
            driver.print(driver.generateCustomerReceipt(printData))
            // 切纸
            driver.print(PosCommands.CUT_FULL)
            // 后厨联（无价格）
            driver.print(driver.generateKitchenTicket(printData))
            // 切纸
            driver.print(PosCommands.CUT_FULL)
            driver.close()
            call.respondText("""{"message":"打印成功（后厨联+顾客联）"}""", ContentType.Application.Json)
        } catch (e: Exception) {
            call.respondText("""{"error":"打印失败: ${e.message?.replace("\"", "'")}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }
}
