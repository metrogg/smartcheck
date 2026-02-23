package com.smartcheck.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 员工信息实体
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val employeeId: String,
    val department: String = "",
    
    // 人脸特征向量（将来接入 SeetaFace 后存储）
    val faceEmbedding: ByteArray? = null,
    
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

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
