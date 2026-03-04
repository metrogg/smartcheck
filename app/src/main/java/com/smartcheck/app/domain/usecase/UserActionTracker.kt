package com.smartcheck.app.domain.usecase

import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

object UserActionTracker {

    private val logs = ConcurrentLinkedQueue<UserActionLog>()

    fun track(
        action: ActionType,
        screen: String,
        detail: String = "",
        result: ActionResult = ActionResult.SUCCESS,
        durationMs: Long? = null
    ) {
        val log = UserActionLog(
            timestamp = System.currentTimeMillis(),
            userId = getCurrentUserId(),
            action = action,
            screen = screen,
            detail = detail,
            result = result,
            durationMs = durationMs
        )

        logs.offer(log)

        Timber.tag("Action")
            .d("${action.name} on $screen: $detail [${result.name}]" + 
               (durationMs?.let { " (${it}ms)" } ?: ""))
    }

    fun trackStart(action: ActionType, screen: String): Long {
        return System.currentTimeMillis()
    }

    fun trackEnd(
        startTime: Long,
        action: ActionType,
        screen: String,
        detail: String = "",
        result: ActionResult = ActionResult.SUCCESS
    ) {
        val durationMs = System.currentTimeMillis() - startTime
        track(action, screen, detail, result, durationMs)
    }

    fun getRecentLogs(count: Int = 100): List<UserActionLog> {
        return logs.toList().takeLast(count)
    }

    fun clear() {
        logs.clear()
    }

    private fun getCurrentUserId(): Long? {
        return null
    }
}

data class UserActionLog(
    val timestamp: Long,
    val userId: Long?,
    val action: ActionType,
    val screen: String,
    val detail: String = "",
    val result: ActionResult,
    val durationMs: Long? = null
)

enum class ActionType {
    MORNING_CHECK_START,
    FACE_RECOGNIZED,
    TEMPERATURE_MEASURED,
    HAND_CHECK_COMPLETED,
    RECORD_SUBMITTED,
    EMPLOYEE_ADDED,
    EMPLOYEE_UPDATED,
    EMPLOYEE_DELETED,
    RECORDS_VIEWED,
    RECORDS_EXPORTED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT
}

enum class ActionResult {
    SUCCESS,
    FAILED,
    CANCELLED
}
