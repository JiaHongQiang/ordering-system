package com.ordering.system.routes

import com.ordering.system.domain.model.Order
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class OrderSocketEvent(
    val type: String,
    val orderId: Long,
    val orderNumber: String,
    val status: String
)

object OrderBroadcaster {
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()
    private val json = Json { encodeDefaults = true }

    fun register(session: WebSocketSession) {
        sessions.add(session)
    }

    fun unregister(session: WebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(message: String) {
        sessions.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (_: Exception) {
                sessions.remove(session)
            }
        }
    }

    suspend fun broadcastOrderChanged(type: String, order: Order) {
        broadcast(json.encodeToString(OrderSocketEvent(type, order.id, order.orderNumber, order.status.name)))
    }
}

fun Route.websocketRoutes() {
    webSocket("/ws/orders") {
        OrderBroadcaster.register(this)
        try {
            for (frame in incoming) {
                // keep connection alive
            }
        } finally {
            OrderBroadcaster.unregister(this)
        }
    }
}
