package com.smartcheck.app.domain.model

data class Record(
    val id: Long = 0,
    val userId: Long,
    val userName: String,
    val employeeId: String,
    val temperature: Float,
    val isTempNormal: Boolean,
    val isHandNormal: Boolean,
    val isPassed: Boolean,
    val handStatus: HandStatus = HandStatus.NOT_CHECKED,
    val healthCertStatus: HealthCertStatus = HealthCertStatus.VALID,
    val symptomFlags: List<SymptomType> = emptyList(),
    val faceImagePath: String? = null,
    val handPalmPath: String? = null,
    val handBackPath: String? = null,
    val checkTime: Long = System.currentTimeMillis(),
    val remark: String = ""
)
