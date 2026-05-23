package com.ordering.system.routes

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.CopyOnWriteArrayList

object OrderBroadcaster {
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

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
