package com.smartcheck.app.api

import android.content.Context
import com.smartcheck.app.api.model.ErrorCodes
import com.smartcheck.app.data.db.ApiTokenDao
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor 服务器管理器
 * 负责启动、停止和管理 HTTP 服务器
 */
@Singleton
class KtorServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val apiTokenDao: ApiTokenDao
) {
    private var server: NettyApplicationEngine? = null
    private val serverPort = 8080

    /**
     * 启动服务器
     */
    fun start() {
        if (server != null) {
            Timber.w("Ktor server is already running")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = serverPort) {
                    configureServer()
                }.start(wait = false)

                Timber.i("Ktor server started on port $serverPort")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start Ktor server")
            }
        }
    }

    /**
     * 停止服务器
     */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Timber.i("Ktor server stopped")
    }

    /**
     * 重启服务器
     */
    fun restart() {
        stop()
        start()
    }

    /**
     * 检查服务器是否运行中
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 配置 Ktor 应用
     */
    private fun Application.configureServer() {
        // 安装内容协商（JSON 序列化）
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        // 安装 CORS（跨域支持）
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
        }

        // 安装 JWT 认证
        install(Authentication) {
            jwt("auth-jwt") {
                realm = "SmartCheck API"
                verifier(JwtUtil.verifyToken)
                validate { credential ->
                    val token = credential.payload.id
                    // 验证 Token 是否在数据库中且未过期
                    val tokenEntity = apiTokenDao.getValidToken(token)
                    if (tokenEntity != null) {
                        // 更新最后使用时间
                        apiTokenDao.updateLastUsed(token)
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { defaultScheme, realm ->
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "code" to ErrorCodes.UNAUTHORIZED,
                            "message" to "Token 无效或已过期"
                        )
                    )
                }
            }
        }

        // 安装错误处理
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Timber.e(cause, "Unhandled exception in Ktor")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "code" to ErrorCodes.INTERNAL_ERROR,
                        "message" to "服务器内部错误: ${cause.message}"
                    )
                )
            }
        }

        // 配置路由
        routing {
            apiService.configureRouting(this)
        }
    }
}
