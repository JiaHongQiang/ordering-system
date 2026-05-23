package com.ordering.system.routes

import com.ordering.system.db.SettingsDao
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class SettingsResponse(
    val storeName: String = "",
    val storePhone: String = "",
    val storeTagline: String = "",
    val receiptFooter: String = "感谢光临，欢迎再来!"
)

@Serializable
data class UpdateSettingsRequest(
    val storeName: String? = null,
    val storePhone: String? = null,
    val storeTagline: String? = null,
    val receiptFooter: String? = null
)

fun Route.settingsRoutes() {
    get("/api/settings") {
        val all = SettingsDao.getAll()
        call.respond(HttpStatusCode.OK, SettingsResponse(
            storeName = all["store_name"] ?: "",
            storePhone = all["store_phone"] ?: "",
            storeTagline = all["store_tagline"] ?: "",
            receiptFooter = all["receipt_footer"] ?: "感谢光临，欢迎再来!"
        ))
    }

    put("/api/settings") {
        val req = call.receive<UpdateSettingsRequest>()
        val updates = mutableMapOf<String, String>()
        req.storeName?.let { updates["store_name"] = it }
        req.storePhone?.let { updates["store_phone"] = it }
        req.storeTagline?.let { updates["store_tagline"] = it }
        req.receiptFooter?.let { updates["receipt_footer"] = it }
        if (updates.isNotEmpty()) {
            SettingsDao.setAll(updates)
        }
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }
}
