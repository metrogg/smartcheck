package com.smartcheck.app.api.model

import kotlinx.serialization.Serializable

/**
 * 通用 API 响应包装
 */
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T, message: String = "success"): ApiResponse<T> {
            return ApiResponse(code = 0, message = message, data = data)
        }

        fun <T> error(code: Int, message: String): ApiResponse<T> {
            return ApiResponse(code = code, message = message, data = null)
        }
    }
}

/**
 * 登录请求
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录响应
 */
@Serializable
data class LoginResponse(
    val token: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
    val user: UserInfo? = null
)

/**
 * 用户信息
 */
@Serializable
data class UserInfo(
    val id: Long,
    val username: String,
    val name: String? = null
)

/**
 * 健康检查响应
 */
@Serializable
data class HealthStatusResponse(
    val status: String,
    val timestamp: Long
)

/**
 * 错误响应
 */
@Serializable
data class ErrorResponse(
    val code: Int,
    val message: String
)

/**
 * 分页响应
 */
@Serializable
data class PageResponse<T>(
    val list: List<T>,
    val pagination: Pagination
)

/**
 * 分页信息
 */
@Serializable
data class Pagination(
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int
)

/**
 * 晨检记录响应
 */
@Serializable
data class RecordResponse(
    val id: Long,
    val userId: Long,
    val userName: String,
    val employeeId: String,
    val checkTime: Long,
    val temperature: Float,
    val isTempNormal: Boolean,
    val isHandNormal: Boolean,
    val isPassed: Boolean,
    val handStatus: String,
    val healthCertStatus: String,
    val symptomFlags: String,
    val remark: String,
    val images: RecordImages? = null
)

/**
 * 记录图片
 */
@Serializable
data class RecordImages(
    val face: String? = null,
    val palm: String? = null,
    val back: String? = null
)

/**
 * 增量同步响应
 */
@Serializable
data class SyncResponse<T>(
    val list: List<T>,
    val hasMore: Boolean,
    val lastRecordId: Long,
    val syncTime: Long
)

/**
 * 统计响应
 */
@Serializable
data class StatisticsResponse(
    val totalCheck: Long,
    val passed: Long,
    val failed: Long,
    val tempAbnormal: Long,
    val handAbnormal: Long,
    val dailyStats: List<DailyStat>
)

/**
 * 每日统计
 */
@Serializable
data class DailyStat(
    val date: String,
    val total: Long,
    val passed: Long,
    val failed: Long,
    val tempAbnormal: Long,
    val handAbnormal: Long
)

/**
 * 导出响应
 */
@Serializable
data class ExportResponse(
    val downloadUrl: String,
    val expiresAt: Long
)

/**
 * 账号同步请求
 */
@Serializable
data class UserSyncRequest(
    val action: String,
    val syncTime: Long? = null,
    val users: List<UserSyncItem>
)

/**
 * 账号同步项
 */
@Serializable
data class UserSyncItem(
    val username: String,
    val password: String? = null,
    val passwordType: String? = null,
    val employeeId: String? = null,
    val role: String? = null,
    val status: String? = null
)

/**
 * 账号同步响应
 */
@Serializable
data class UserSyncResponse(
    val received: Int,
    val success: Int,
    val failed: Int,
    val details: List<UserSyncDetail>
)

/**
 * 账号同步详情
 */
@Serializable
data class UserSyncDetail(
    val username: String,
    val status: String,
    val message: String
)

/**
 * 员工信息响应
 */
@Serializable
data class EmployeeResponse(
    val id: Long,
    val name: String,
    val employeeId: String,
    val idCardNumber: String,
    val healthCertStatus: String,
    val healthCertStartDate: Long? = null,
    val healthCertEndDate: Long? = null,
    val faceImage: String? = null,
    val healthCertImage: String? = null,
    val isActive: Boolean,
    val createdAt: Long
)

/**
 * 员工导入请求
 */
@Serializable
data class EmployeeImportRequest(
    val employees: List<EmployeeImportItem>
)

/**
 * 员工导入项
 */
@Serializable
data class EmployeeImportItem(
    val name: String,
    val employeeId: String,
    val idCardNumber: String = "",
    val faceImageBase64: String? = null,
    val healthCertImageBase64: String? = null,
    val healthCertStartDate: Long? = null,
    val healthCertEndDate: Long? = null,
    val isActive: Boolean = true
)

/**
 * 员工照片上传请求
 */
@Serializable
data class PhotoUploadRequest(
    val fileName: String,
    val imageBase64: String
)

/**
 * 员工导入响应
 */
@Serializable
data class EmployeeImportResponse(
    val total: Int,
    val success: Int,
    val failed: Int,
    val details: List<EmployeeImportDetail>
)

/**
 * 员工导入详情
 */
@Serializable
data class EmployeeImportDetail(
    val employeeId: String,
    val status: String,
    val message: String,
    val userId: Long? = null
)

/**
 * 导出请求
 */
@Serializable
data class ExportRequest(
    val startDate: String,
    val endDate: String,
    val format: String = "csv",
    val employeeId: String? = null
)


object ErrorCodes {
    const val SUCCESS = 0
    const val UNAUTHORIZED = 1001
    const val FORBIDDEN = 1002
    const val NOT_FOUND = 1003
    const val INVALID_PARAMS = 1004
    const val INTERNAL_ERROR = 1005
    const val TOKEN_EXPIRED = 1006
    
    const val USERNAME_EXISTS = 2005
    const val VALIDATION_FAILED = 2006
    const val PASSWORD_EMPTY = 2009
    const val INVALID_ROLE = 2010
    const val INVALID_STATUS = 2011
}
