package com.smartcheck.app.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.Date
import java.util.UUID

/**
 * JWT 工具类
 */
object JwtUtil {

    // JWT 密钥（生产环境应从配置文件读取）
    private const val SECRET = "SmartCheck2024SecureKeyForJwtTokenGeneration"
    
    // Token 有效期：24小时
    private const val EXPIRATION_TIME = 24 * 60 * 60 * 1000L

    private val algorithm = Algorithm.HMAC256(SECRET)

    /**
     * 生成 Token
     */
    fun generateToken(userId: Long, username: String): String {
        val now = Date()
        val expiry = Date(now.time + EXPIRATION_TIME)
        
        return JWT.create()
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    /**
     * 验证 Token
     */
    fun verifyToken(token: String): DecodedJWT? {
        return try {
            val verifier = JWT.require(algorithm)
                .build()
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            null
        }
    }

    /**
     * 从 Token 中提取用户ID
     */
    fun getUserId(decodedJWT: DecodedJWT): Long {
        return decodedJWT.getClaim("userId").asLong()
    }

    /**
     * 从 Token 中提取用户名
     */
    fun getUsername(decodedJWT: DecodedJWT): String {
        return decodedJWT.getClaim("username").asString()
    }

    /**
     * 获取 Token 过期时间
     */
    fun getExpirationTime(): Long {
        return EXPIRATION_TIME / 1000 // 返回秒
    }

    /**
     * 获取 JWT Verifier（供 Ktor 使用）
     */
    val verifyToken: JWTVerifier
        get() = JWT.require(algorithm).build()
}
