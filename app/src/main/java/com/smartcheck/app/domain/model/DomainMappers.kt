package com.smartcheck.app.domain.model

import com.smartcheck.app.data.db.RecordEntity
import com.smartcheck.app.data.db.UserEntity

fun UserEntity.toDomain(): User = User(
    id = id,
    name = name,
    employeeId = employeeId,
    idCardNumber = idCardNumber,
    faceImagePath = faceImagePath,
    faceEmbedding = faceEmbedding,
    healthCertImagePath = healthCertImagePath,
    healthCertStartDate = healthCertStartDate,
    healthCertEndDate = healthCertEndDate,
    department = department,
    isActive = isActive,
    createdAt = createdAt
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    name = name,
    employeeId = employeeId,
    idCardNumber = idCardNumber,
    faceImagePath = faceImagePath,
    faceEmbedding = faceEmbedding,
    healthCertImagePath = healthCertImagePath,
    healthCertStartDate = healthCertStartDate,
    healthCertEndDate = healthCertEndDate,
    department = department,
    isActive = isActive,
    createdAt = createdAt
)

fun RecordEntity.toDomain(): Record = Record(
    id = id,
    userId = userId,
    userName = userName,
    employeeId = employeeId,
    temperature = temperature,
    isTempNormal = isTempNormal,
    isHandNormal = isHandNormal,
    isPassed = isPassed,
    handStatus = handStatus.toHandStatus(),
    healthCertStatus = healthCertStatus.toHealthCertStatus(),
    symptomFlags = symptomFlags.toSymptomTypeList(),
    faceImagePath = faceImagePath,
    handPalmPath = handPalmPath,
    handBackPath = handBackPath,
    checkTime = checkTime,
    remark = remark
)

fun Record.toEntity(): RecordEntity = RecordEntity(
    id = id,
    userId = userId,
    userName = userName,
    employeeId = employeeId,
    temperature = temperature,
    isTempNormal = isTempNormal,
    isHandNormal = isHandNormal,
    isPassed = isPassed,
    handStatus = handStatus.name,
    healthCertStatus = healthCertStatus.name,
    symptomFlags = symptomFlags.joinToString(",") { it.name },
    faceImagePath = faceImagePath,
    handPalmPath = handPalmPath,
    handBackPath = handBackPath,
    checkTime = checkTime,
    remark = remark
)

private fun String.toHandStatus(): HandStatus = try {
    HandStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    HandStatus.NOT_CHECKED
}

private fun String.toHealthCertStatus(): HealthCertStatus = try {
    HealthCertStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    HealthCertStatus.VALID
}

private fun String.toSymptomTypeList(): List<SymptomType> = if (this.isEmpty()) {
    emptyList()
} else {
    this.split(",").mapNotNull {
        try {
            SymptomType.valueOf(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
