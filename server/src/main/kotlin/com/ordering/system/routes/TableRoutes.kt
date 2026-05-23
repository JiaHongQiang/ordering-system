package com.ordering.system.routes

import com.ordering.system.db.OrderDao
import com.ordering.system.db.OrderItemDao
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class TableInfo(val number: String, val occupied: Boolean, val orderId: Long? = null)

@Serializable
data class TableDetailResponse(
    val number: String,
    val order: OrderResponse? = null
)

fun Route.tableRoutes() {
    get("/api/tables") {
        val occupiedTables = OrderDao.findOccupiedTables()
        val tables = (1..20).map { num ->
            val numStr = num.toString()
            val activeOrder = if (numStr in occupiedTables) {
                OrderDao.findByTableNumber(numStr)
            } else null
            TableInfo(
                number = numStr,
                occupied = activeOrder != null,
                orderId = activeOrder?.id
            )
        }
        call.respond(HttpStatusCode.OK, tables)
    }

    get("/api/tables/{number}/order") {
        val number = call.parameters["number"]!!
        val order = OrderDao.findByTableNumber(number)
        if (order != null) {
            val items = OrderItemDao.findByOrderId(order.id)
            call.respond(HttpStatusCode.OK, TableDetailResponse(number, order.toResponse(items)))
        } else {
            call.respond(HttpStatusCode.OK, TableDetailResponse(number, null))
        }
    }
}
