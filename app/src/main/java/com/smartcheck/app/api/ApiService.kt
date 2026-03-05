package com.smartcheck.app.api

import android.content.Context
import com.smartcheck.app.api.model.*
import com.smartcheck.app.data.db.*
import com.smartcheck.app.data.repository.AdminAuthRepository
import com.smartcheck.app.data.repository.RecordRepository
import com.smartcheck.app.data.repository.UserRepository
import com.smartcheck.app.domain.model.User
import com.smartcheck.app.utils.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 服务
 * 处理所有 HTTP 接口请求
 */
@Singleton
class ApiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adminAuthRepository: AdminAuthRepository,
    private val recordRepository: RecordRepository,
    private val userRepository: UserRepository,
    private val apiTokenDao: ApiTokenDao,
    private val apiAccessLogDao: ApiAccessLogDao,
    private val systemUserDao: SystemUserDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 配置路由
     */
    fun configureRouting(routing: Routing) {
        routing {
            // 健康检查（无需认证）
            get("/health") {
                call.respond(ApiResponse.success(HealthStatusResponse("ok", System.currentTimeMillis())))
            }

            // 认证相关
            route("/api/auth") {
                post("/login") {
                    handleLogin(call)
                }
            }

            // 账号同步（需要认证）
            authenticate("auth-jwt") {
                route("/api/users") {
                    post("/sync") {
                        handleSyncUsers(call)
                    }
                }
            }

            // 需要认证的接口
            authenticate("auth-jwt") {
                route("/api/records") {
                    get {
                        handleGetRecords(call)
                    }

                    get("/sync") {
                        handleSyncRecords(call)
                    }

                    get("/{id}") {
                        handleGetRecordById(call)
                    }

                    get("/statistics") {
                        handleGetStatistics(call)
                    }

                    post("/export") {
                        handleExportRecords(call)
                    }
                }

                // 员工信息
                route("/api/employees") {
                    get {
                        handleGetEmployees(call)
                    }

                    get("/sync") {
                        handleSyncEmployees(call)
                    }
                }

                // 图片下载
                get("/api/images/{filename}") {
                    handleGetImage(call)
                }

                get("/api/employee-images/{filename}") {
                    handleGetEmployeeImage(call)
                }

                get("/downloads/{filename}") {
                    handleDownloadFile(call)
                }
            }
        }
    }

    /**
     * 处理登录请求
     */
    private suspend fun handleLogin(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0
        var errorMessage: String? = null

        try {
            val request = call.receive<LoginRequest>()

            // 验证用户名密码
            val isValid = adminAuthRepository.verifyPassword(request.username, request.password)

            if (!isValid) {
                responseCode = ErrorCodes.UNAUTHORIZED
                errorMessage = "用户名或密码错误"
                call.respond(HttpStatusCode.Unauthorized, ApiResponse.error<LoginResponse>(responseCode, errorMessage))
                return
            }

            // 获取用户信息（这里简化处理，实际应该从数据库查询）
            val userId = 1L // 管理员固定ID

            // 生成 Token
            val token = JwtUtil.generateToken(userId, request.username)
            val expiresIn = JwtUtil.getExpirationTime()

            // 保存 Token 到数据库
            val tokenEntity = ApiTokenEntity(
                token = token,
                userId = userId,
                username = request.username,
                expiresAt = System.currentTimeMillis() + expiresIn * 1000
            )
            apiTokenDao.insertToken(tokenEntity)

            val response = LoginResponse(
                token = token,
                expiresIn = expiresIn,
                user = UserInfo(id = userId, username = request.username, name = "管理员")
            )

            Timber.d("Login success: ${request.username}")
            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            errorMessage = e.message
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<LoginResponse>(responseCode, "登录失败: ${e.message}"))
        } finally {
            // 记录访问日志
            logAccess(call, "/api/auth/login", "POST", responseCode, errorMessage, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理获取记录列表
     */
    private suspend fun handleGetRecords(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            // 获取查询参数
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]
            val employeeId = call.request.queryParameters["employeeId"]
            val isPassed = call.request.queryParameters["isPassed"]?.toBooleanStrictOrNull()
            val isTempNormal = call.request.queryParameters["isTempNormal"]?.toBooleanStrictOrNull()
            val isHandNormal = call.request.queryParameters["isHandNormal"]?.toBooleanStrictOrNull()
            val includeImages = call.request.queryParameters["includeImages"]?.toBoolean() ?: false
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            // 参数校验
            if (startDate == null || endDate == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<PageResponse<RecordResponse>>(ErrorCodes.INVALID_PARAMS, "startDate 和 endDate 不能为空"))
                return
            }

            // 解析日期
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startTimeMillis = dateFormat.parse(startDate)?.time ?: 0
            val endTimeMillis = dateFormat.parse(endDate)?.time?.plus(24 * 60 * 60 * 1000 - 1) ?: System.currentTimeMillis()

            // 查询记录
            val records = recordRepository.getRecordsByTimeRangeSync(startTimeMillis, endTimeMillis)

            // 过滤
            var filteredRecords = records
            if (!employeeId.isNullOrBlank()) {
                filteredRecords = filteredRecords.filter { it.employeeId == employeeId }
            }
            if (isPassed != null) {
                filteredRecords = filteredRecords.filter { it.isPassed == isPassed }
            }
            if (isTempNormal != null) {
                filteredRecords = filteredRecords.filter { it.isTempNormal == isTempNormal }
            }
            if (isHandNormal != null) {
                filteredRecords = filteredRecords.filter { it.isHandNormal == isHandNormal }
            }

            // 分页
            val total = filteredRecords.size
            val totalPages = (total + pageSize - 1) / pageSize
            val pagedRecords = filteredRecords
                .drop((page - 1) * pageSize)
                .take(pageSize)

            // 转换为响应对象
            val recordResponses = pagedRecords.map { it.toResponse(includeImages) }

            val response = PageResponse(
                list = recordResponses,
                pagination = Pagination(
                    page = page,
                    pageSize = pageSize,
                    total = total.toLong(),
                    totalPages = totalPages
                )
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Get records failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<PageResponse<RecordResponse>>(ErrorCodes.INTERNAL_ERROR, "查询失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/records", "GET", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理单条记录查询
     */
    private suspend fun handleGetRecordById(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            val id = call.parameters["id"]?.toLongOrNull()
            val includeImages = call.request.queryParameters["includeImages"]?.toBoolean() ?: true

            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<RecordResponse>(ErrorCodes.INVALID_PARAMS, "记录ID无效"))
                return
            }

            val record = recordRepository.getRecordByIdSync(id)

            if (record == null) {
                call.respond(HttpStatusCode.NotFound, ApiResponse.error<RecordResponse>(ErrorCodes.NOT_FOUND, "记录不存在"))
                return
            }

            call.respond(ApiResponse.success(record.toResponse(includeImages)))

        } catch (e: Exception) {
            Timber.e(e, "Get record by id failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<RecordResponse>(ErrorCodes.INTERNAL_ERROR, "查询失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/records/{id}", "GET", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理增量同步
     */
    private suspend fun handleSyncRecords(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            val lastRecordId = call.request.queryParameters["lastRecordId"]?.toLongOrNull()
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)

            if (lastRecordId == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<SyncResponse<RecordResponse>>(ErrorCodes.INVALID_PARAMS, "lastRecordId 不能为空"))
                return
            }

            // 查询ID大于 lastRecordId 的记录
            val records = recordRepository.getRecordsAfterId(lastRecordId, limit)

            val recordResponses = records.map { it.toResponse(false) }

            val response = SyncResponse(
                list = recordResponses,
                hasMore = records.size >= limit,
                lastRecordId = records.lastOrNull()?.id ?: lastRecordId,
                syncTime = System.currentTimeMillis()
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Sync records failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SyncResponse<RecordResponse>>(ErrorCodes.INTERNAL_ERROR, "同步失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/records/sync", "GET", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理统计请求
     */
    private suspend fun handleGetStatistics(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]

            if (startDate == null || endDate == null) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<StatisticsResponse>(ErrorCodes.INVALID_PARAMS, "startDate 和 endDate 不能为空"))
                return
            }

            // 解析日期
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startTimeMillis = dateFormat.parse(startDate)?.time ?: 0
            val endTimeMillis = dateFormat.parse(endDate)?.time?.plus(24 * 60 * 60 * 1000 - 1) ?: System.currentTimeMillis()

            // 查询记录
            val records = recordRepository.getRecordsByTimeRangeSync(startTimeMillis, endTimeMillis)

            // 统计
            val totalCheck = records.size.toLong()
            val passed = records.count { it.isPassed }.toLong()
            val failed = records.count { !it.isPassed }.toLong()
            val tempAbnormal = records.count { !it.isTempNormal }.toLong()
            val handAbnormal = records.count { !it.isHandNormal }.toLong()

            // 每日统计
            val dailyStats = records.groupBy {
                dateFormat.format(Date(it.checkTime))
            }.map { (date, dayRecords) ->
                DailyStat(
                    date = date,
                    total = dayRecords.size.toLong(),
                    passed = dayRecords.count { r -> r.isPassed }.toLong(),
                    failed = dayRecords.count { r -> !r.isPassed }.toLong(),
                    tempAbnormal = dayRecords.count { r -> !r.isTempNormal }.toLong(),
                    handAbnormal = dayRecords.count { r -> !r.isHandNormal }.toLong()
                )
            }.sortedBy { it.date }

            val response = StatisticsResponse(
                totalCheck = totalCheck,
                passed = passed,
                failed = failed,
                tempAbnormal = tempAbnormal,
                handAbnormal = handAbnormal,
                dailyStats = dailyStats
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Get statistics failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<StatisticsResponse>(ErrorCodes.INTERNAL_ERROR, "统计失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/records/statistics", "GET", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理导出请求
     */
    private suspend fun handleExportRecords(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            val request = call.receive<ExportRequest>()
            val startDate = request.startDate
            val endDate = request.endDate
            val format = request.format

            if (startDate.isBlank() || endDate.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<ExportResponse>(ErrorCodes.INVALID_PARAMS, "startDate 和 endDate 不能为空"))
                return
            }

            // 解析日期
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startTimeMillis = dateFormat.parse(startDate)?.time ?: 0
            val endTimeMillis = dateFormat.parse(endDate)?.time?.plus(24 * 60 * 60 * 1000 - 1) ?: System.currentTimeMillis()

            // 查询记录
            val records = recordRepository.getRecordsByTimeRangeSync(startTimeMillis, endTimeMillis)

            // 生成文件名
            val fileName = "records_${startDate}_${endDate}.csv"
            val file = File(context.cacheDir, fileName)

            // 生成 CSV
            file.bufferedWriter().use { writer ->
                // 表头
                writer.write("ID,姓名,工号,检测时间,体温,体温正常,手部正常,是否通过,手部状态,健康证状态,症状,备注\n")

                // 数据
                records.forEach { record ->
                    val dateStr = dateFormat.format(Date(record.checkTime))
                    writer.write("${record.id},${record.userName},${record.employeeId},$dateStr,${record.temperature},${record.isTempNormal},${record.isHandNormal},${record.isPassed},${record.handStatus},${record.healthCertStatus},${record.symptomFlags},${record.remark}\n")
                }
            }

            val response = ExportResponse(
                downloadUrl = "/api/downloads/$fileName",
                expiresAt = System.currentTimeMillis() + 3600 * 1000 // 1小时有效期
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Export records failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<ExportResponse>(ErrorCodes.INTERNAL_ERROR, "导出失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/records/export", "POST", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理获取员工列表
     */
    private suspend fun handleGetEmployees(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0
        var errorMessage: String? = null

        try {
            Timber.d("handleGetEmployees: 开始获取员工列表")
            
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()?.coerceAtMost(100) ?: 20
            val employeeId = call.request.queryParameters["employeeId"]
            val isActive = call.request.queryParameters["isActive"]?.toBooleanStrictOrNull()

            Timber.d("handleGetEmployees: page=$page, pageSize=$pageSize, employeeId=$employeeId, isActive=$isActive")

            // 获取所有员工 - 使用 first() 获取第一个值
            val allUsers = userRepository.observeAllUsers().first()

            Timber.d("handleGetEmployees: 总用户数 = ${allUsers.size}")

            // 过滤
            var filteredUsers: List<User> = allUsers
            if (!employeeId.isNullOrBlank()) {
                filteredUsers = filteredUsers.filter { it.employeeId == employeeId }
            }
            if (isActive != null) {
                filteredUsers = filteredUsers.filter { it.isActive == isActive }
            }

            // 转换为响应对象
            val employeeResponses = filteredUsers.map { it.toEmployeeResponse() }

            // 分页
            val total = employeeResponses.size
            val totalPages = (total + pageSize - 1) / pageSize
            val pagedEmployees = employeeResponses
                .drop((page - 1) * pageSize)
                .take(pageSize)

            val response = PageResponse(
                list = pagedEmployees,
                pagination = Pagination(
                    page = page,
                    pageSize = pageSize,
                    total = total.toLong(),
                    totalPages = totalPages
                )
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Get employees failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            errorMessage = e.message
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<PageResponse<EmployeeResponse>>(ErrorCodes.INTERNAL_ERROR, "查询失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/employees", "GET", responseCode, errorMessage, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理员工增量同步
     */
    private suspend fun handleSyncEmployees(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0
        var errorMessage: String? = null

        try {
            val lastEmployeeId = call.request.queryParameters["lastEmployeeId"]?.toLongOrNull() ?: 0
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceAtMost(500) ?: 100

            val employees = userRepository.getUsersAfterId(lastEmployeeId, limit)
            val employeeResponses = employees.map { it.toEmployeeResponse() }

            val lastId = employees.lastOrNull()?.id ?: lastEmployeeId

            val response = SyncResponse(
                list = employeeResponses,
                hasMore = employees.size >= limit,
                lastRecordId = lastId,
                syncTime = System.currentTimeMillis()
            )

            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Sync employees failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            errorMessage = e.message
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<SyncResponse<EmployeeResponse>>(ErrorCodes.INTERNAL_ERROR, "同步失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/employees/sync", "GET", responseCode, errorMessage, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理图片下载
     */
    private suspend fun handleGetImage(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0

        try {
            val filename = call.parameters["filename"]

            if (filename.isNullOrBlank()) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "文件名不能为空"))
                return
            }

            // 安全检查：防止目录遍历攻击
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "非法文件名"))
                return
            }

            val file = FileUtil.getRecordImageFile(context, filename)

            if (file == null || !file.exists()) {
                responseCode = ErrorCodes.NOT_FOUND
                call.respond(HttpStatusCode.NotFound, ApiResponse.error<String>(responseCode, "图片不存在"))
                return
            }

            call.respondFile(file)

        } catch (e: Exception) {
            Timber.e(e, "Get image failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<String>(responseCode, "获取图片失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/images/{filename}", "GET", responseCode, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理员工图片下载
     */
    private suspend fun handleGetEmployeeImage(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0

        try {
            val filename = call.parameters["filename"]

            if (filename.isNullOrBlank()) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "文件名不能为空"))
                return
            }

            // 安全检查
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "非法文件名"))
                return
            }

            // 图片存储在 records 目录
            val file = FileUtil.getRecordImageFile(context, filename)
            Timber.d("handleGetEmployeeImage: filename=$filename, file=${file?.absolutePath}, exists=${file?.exists()}")

            if (file == null || !file.exists()) {
                Timber.w("员工图片不存在: $filename, 查找路径: ${FileUtil.getRecordsDir(context)}")
                responseCode = ErrorCodes.NOT_FOUND
                call.respond(HttpStatusCode.NotFound, ApiResponse.error<String>(responseCode, "图片不存在"))
                return
            }

            call.respondFile(file)

        } catch (e: Exception) {
            Timber.e(e, "Get employee image failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<String>(responseCode, "获取图片失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/employee-images/{filename}", "GET", responseCode, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 记录访问日志
     */
    private suspend fun logAccess(
        call: ApplicationCall,
        endpoint: String,
        method: String,
        responseCode: Int,
        errorMessage: String?,
        durationMs: Long
    ) {
        try {
            // 获取当前用户信息
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asLong()
            val username = principal?.payload?.getClaim("username")?.asString()

            val log = ApiAccessLogEntity(
                endpoint = endpoint,
                method = method,
                responseCode = responseCode,
                responseMessage = errorMessage,
                userId = userId,
                username = username,
                ipAddress = call.request.origin.remoteHost,
                durationMs = durationMs
            )

            apiAccessLogDao.insertLog(log)
        } catch (e: Exception) {
            Timber.e(e, "Failed to log access")
        }
    }

    /**
     * 将 RecordEntity 转换为 RecordResponse
     */
    private fun RecordEntity.toResponse(includeImages: Boolean): RecordResponse {
        return RecordResponse(
            id = this.id,
            userId = this.userId,
            userName = this.userName,
            employeeId = this.employeeId,
            checkTime = this.checkTime,
            temperature = this.temperature,
            isTempNormal = this.isTempNormal,
            isHandNormal = this.isHandNormal,
            isPassed = this.isPassed,
            handStatus = this.handStatus,
            healthCertStatus = this.healthCertStatus,
            symptomFlags = this.symptomFlags,
            remark = this.remark,
            images = if (includeImages) {
                RecordImages(
                    face = this.faceImagePath?.let { "/api/images/$it" },
                    palm = this.handPalmPath?.let { "/api/images/$it" },
                    back = this.handBackPath?.let { "/api/images/$it" }
                )
            } else null
        )
    }

    /**
     * 处理账号同步请求
     */
    private suspend fun handleSyncUsers(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()

        try {
            val request = call.receive<UserSyncRequest>()

            // 参数校验
            val action = request.action.lowercase()
            if (action !in listOf("create", "update", "delete", "sync")) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<UserSyncResponse>(ErrorCodes.INVALID_PARAMS, "无效的操作类型"))
                return
            }

            if (request.users.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<UserSyncResponse>(ErrorCodes.INVALID_PARAMS, "用户列表不能为空"))
                return
            }

            if (request.users.size > 100) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<UserSyncResponse>(ErrorCodes.INVALID_PARAMS, "单次同步不能超过100条"))
                return
            }

            // 全量同步：先清空所有账号
            if (action == "sync") {
                systemUserDao.deleteAllUsers()
            }

            val details = mutableListOf<UserSyncDetail>()
            var successCount = 0
            var failedCount = 0

            for (user in request.users) {
                try {
                    when (action) {
                        "create" -> {
                            val existing = systemUserDao.getUserByUsername(user.username)
                            if (existing != null) {
                                details.add(UserSyncDetail(user.username, "failed", "用户名已存在"))
                                failedCount++
                            } else {
                                val passwordHash = hashPassword(user.password ?: "")
                                val newUser = SystemUserEntity(
                                    username = user.username,
                                    passwordHash = passwordHash,
                                    passwordType = user.passwordType ?: "plain",
                                    employeeId = user.employeeId,
                                    role = user.role ?: "employee",
                                    status = user.status ?: "active"
                                )
                                systemUserDao.insertUser(newUser)
                                details.add(UserSyncDetail(user.username, "success", "同步成功"))
                                successCount++
                            }
                        }

                        "update" -> {
                            val existing = systemUserDao.getUserByUsername(user.username)
                            if (existing == null) {
                                details.add(UserSyncDetail(user.username, "failed", "用户不存在"))
                                failedCount++
                            } else {
                                val updated = existing.copy(
                                    passwordHash = user.password?.let { hashPassword(it) } ?: existing.passwordHash,
                                    passwordType = user.passwordType ?: existing.passwordType,
                                    employeeId = user.employeeId ?: existing.employeeId,
                                    role = user.role ?: existing.role,
                                    status = user.status ?: existing.status,
                                    updatedAt = System.currentTimeMillis()
                                )
                                systemUserDao.updateUser(updated)
                                details.add(UserSyncDetail(user.username, "success", "同步成功"))
                                successCount++
                            }
                        }

                        "delete" -> {
                            val existing = systemUserDao.getUserByUsername(user.username)
                            if (existing == null) {
                                details.add(UserSyncDetail(user.username, "failed", "用户不存在"))
                                failedCount++
                            } else {
                                systemUserDao.deleteUserByUsername(user.username)
                                details.add(UserSyncDetail(user.username, "success", "删除成功"))
                                successCount++
                            }
                        }

                        "sync" -> {
                            val passwordHash = hashPassword(user.password ?: "")
                            val newUser = SystemUserEntity(
                                username = user.username,
                                passwordHash = passwordHash,
                                passwordType = user.passwordType ?: "plain",
                                employeeId = user.employeeId,
                                role = user.role ?: "employee",
                                status = user.status ?: "active"
                            )
                            systemUserDao.insertUser(newUser)
                            details.add(UserSyncDetail(user.username, "success", "同步成功"))
                            successCount++
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Sync user failed: ${user.username}")
                    details.add(UserSyncDetail(user.username, "failed", "处理失败: ${e.message}"))
                    failedCount++
                }
            }

            val response = UserSyncResponse(
                received = request.users.size,
                success = successCount,
                failed = failedCount,
                details = details
            )

            Timber.d("User sync success: received=${request.users.size}, success=$successCount, failed=$failedCount")
            call.respond(ApiResponse.success(response))

        } catch (e: Exception) {
            Timber.e(e, "Sync users failed")
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<UserSyncResponse>(ErrorCodes.INTERNAL_ERROR, "同步失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/users/sync", "POST", 0, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 处理下载导出文件
     */
    private suspend fun handleDownloadFile(call: ApplicationCall) {
        val startTime = System.currentTimeMillis()
        var responseCode = 0

        try {
            val filename = call.parameters["filename"]

            if (filename.isNullOrBlank()) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "文件名不能为空"))
                return
            }

            // 安全检查：防止目录遍历攻击
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                responseCode = ErrorCodes.INVALID_PARAMS
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error<String>(responseCode, "非法文件名"))
                return
            }

            // 检查文件是否在 cache 目录
            val file = File(context.cacheDir, filename)

            if (!file.exists()) {
                responseCode = ErrorCodes.NOT_FOUND
                call.respond(HttpStatusCode.NotFound, ApiResponse.error<String>(responseCode, "文件不存在或已过期"))
                return
            }

            // 检查文件是否过期（1小时）
            val oneHourAgo = System.currentTimeMillis() - 3600 * 1000
            if (file.lastModified() < oneHourAgo) {
                file.delete()
                responseCode = ErrorCodes.NOT_FOUND
                call.respond(HttpStatusCode.NotFound, ApiResponse.error<String>(responseCode, "文件已过期"))
                return
            }

            call.respondFile(file)

        } catch (e: Exception) {
            Timber.e(e, "Download file failed")
            responseCode = ErrorCodes.INTERNAL_ERROR
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error<String>(responseCode, "下载失败: ${e.message}"))
        } finally {
            logAccess(call, "/api/downloads/{filename}", "GET", responseCode, null, System.currentTimeMillis() - startTime)
        }
    }

    /**
     * 简单密码哈希（生产环境应使用更安全的方式）
     */
    private fun hashPassword(password: String): String {
        return if (password.isEmpty()) {
            ""
        } else {
            // 简单处理：直接返回原密码（实际应该加密）
            // TODO: 生产环境使用 BCrypt 或其他加密方式
            password
        }
    }

    /**
     * 将 User 转换为 EmployeeResponse
     */
    private fun User.toEmployeeResponse(): EmployeeResponse {
        Timber.d("toEmployeeResponse: faceImagePath=$faceImagePath, healthCertImagePath=$healthCertImagePath")
        val faceImageUrl = faceImagePath?.let { path ->
            val fileName = path.substringAfterLast("/")
            if (fileName.isNotBlank()) {
                Timber.d("toEmployeeResponse: face fileName=$fileName")
                "/api/employee-images/$fileName"
            } else null
        }
        val healthCertImageUrl = healthCertImagePath.let { path ->
            if (path.isNotBlank()) {
                val fileName = path.substringAfterLast("/")
                if (fileName.isNotBlank()) {
                    Timber.d("toEmployeeResponse: cert fileName=$fileName")
                    "/api/employee-images/$fileName"
                } else null
            } else null
        }
        return EmployeeResponse(
            id = id,
            name = name,
            employeeId = employeeId,
            idCardNumber = idCardNumber,
            healthCertStatus = getHealthCertStatus().name,
            healthCertStartDate = healthCertStartDate,
            healthCertEndDate = healthCertEndDate,
            faceImage = faceImageUrl,
            healthCertImage = healthCertImageUrl,
            isActive = isActive,
            createdAt = createdAt
        )
    }
}
