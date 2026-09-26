package com.example.hotspotportal.auth

import com.example.hotspotportal.store.PortalUserDao
import com.example.hotspotportal.store.PortalUserEntity
import org.mindrot.jbcrypt.BCrypt

/** Why a login attempt failed. Drives the message the guest sees. */
enum class LoginFailure { INVALID, DISABLED, EXPIRED, DEVICE_LIMIT, LOCKED }

sealed interface LoginResult {
    data class Success(val user: PortalUserEntity) : LoginResult
    data class Failure(val reason: LoginFailure, val remainingAttempts: Int = 0, val retryAfterSeconds: Long = 0) : LoginResult
}

/**
 * Credential verification.
 *
 * BCrypt at cost 12 (spec requires >= 10). The username is compared against
 * a lower-cased unique index so lookup is case-insensitive; the password is
 * verified in constant time by BCrypt itself.
 */
class AuthStore(private val userDao: PortalUserDao) {

    suspend fun verify(username: String, password: String): LoginResult {
        val user = userDao.byUsername(username.trim().lowercase())
            ?: return LoginResult.Failure(LoginFailure.INVALID)
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return LoginResult.Failure(LoginFailure.INVALID)
        }
        if (!user.enabled) return LoginResult.Failure(LoginFailure.DISABLED)
        if (user.expired) return LoginResult.Failure(LoginFailure.EXPIRED)
        return LoginResult.Success(user)
    }

    suspend fun createUser(
        username: String,
        password: String,
        deviceLimit: Int = 1,
        expiresAt: Long? = null,
        note: String = "",
    ): Result<PortalUserEntity> = runCatching {
        require(username.isNotBlank()) { "username is required" }
        require(password.length >= 4) { "password must be at least 4 characters" }
        val entity = PortalUserEntity(
            username_lower = username.trim().lowercase(),
            username_display = username.trim(),
            passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST)),
            deviceLimit = deviceLimit.coerceAtLeast(1),
            expiresAt = expiresAt,
            note = note,
        )
        val id = userDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun setPassword(user: PortalUserEntity, newPassword: String) {
        require(newPassword.length >= 4) { "password must be at least 4 characters" }
        userDao.update(user.copy(passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(BCRYPT_COST))))
    }

    /** A readable random password for the "Generate" field. */
    fun generatePassword(length: Int = 10): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return (1..length).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    companion object {
        const val BCRYPT_COST = 12
    }
}
