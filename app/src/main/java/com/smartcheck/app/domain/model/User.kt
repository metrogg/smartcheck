package com.smartcheck.app.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val employeeId: String,
    val idCardNumber: String = "",
    val faceImagePath: String? = null,
    val faceEmbedding: ByteArray? = null,
    val healthCertImagePath: String = "",
    val healthCertStartDate: Long? = null,
    val healthCertEndDate: Long? = null,
    val department: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getHealthCertStatus(): HealthCertStatus {
        val endDate = healthCertEndDate ?: return HealthCertStatus.EXPIRED
        val now = System.currentTimeMillis()
        val daysRemaining = (endDate - now) / (24 * 60 * 60 * 1000)

        return when {
            daysRemaining < 0 -> HealthCertStatus.EXPIRED
            daysRemaining <= 7 -> HealthCertStatus.EXPIRING_SOON
            else -> HealthCertStatus.VALID
        }
    }

    fun getHealthCertDaysRemaining(): Long? {
        val endDate = healthCertEndDate ?: return null
        val now = System.currentTimeMillis()
        return (endDate - now) / (24 * 60 * 60 * 1000)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (name != other.name) return false
        if (employeeId != other.employeeId) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + employeeId.hashCode()
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        return result
    }
}
