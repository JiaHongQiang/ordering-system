package com.ordering.system

import com.ordering.system.db.DatabaseFactory
import com.ordering.system.routes.menuRoutes
import com.ordering.system.routes.modifierRoutes
import com.ordering.system.routes.orderRoutes
import com.ordering.system.routes.settingsRoutes
import com.ordering.system.routes.tableRoutes
import com.ordering.system.routes.websocketRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import java.io.File
import java.time.Duration

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
            isLenient = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                """{"error":"${cause.message?.replace("\"", "'")}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        menuRoutes()
        modifierRoutes()
        orderRoutes()
        tableRoutes()
        settingsRoutes()
        websocketRoutes()

        // 上传文件访问（图片缓存 7 天）
        val uploadsDir = File("static/uploads")
        uploadsDir.mkdirs()
        staticFiles("/uploads", uploadsDir) {
            cacheControl { listOf(CacheControl.MaxAge(maxAgeSeconds = 604800)) }
        }

        // 前端静态文件
        val staticDir = File("static")
        if (staticDir.exists() && staticDir.isDirectory) {
            staticFiles("/", staticDir) {
                default("index.html")
                // JS/CSS 等资源缓存 7 天
                cacheControl { listOf(CacheControl.MaxAge(maxAgeSeconds = 604800)) }
            }
            // SPA 路由回退
            get("/{path...}") {
                call.respondFile(File(staticDir, "index.html"))
            }
        }
    }
}
