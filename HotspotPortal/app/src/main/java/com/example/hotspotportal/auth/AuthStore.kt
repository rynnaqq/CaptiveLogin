package com.example.hotspotportal.auth

import com.example.hotspotportal.store.PortalUserDao
import com.example.hotspotportal.store.PortalUserEntity
import org.mindrot.jbcrypt.BCrypt

/**
 * Why a login attempt failed. Drives the message the guest sees.
 *
 * NO_SUCH_USER and WRONG_PASSWORD are separate on purpose: the admin asked for
 * the portal to say which half was wrong, so a guest staring at a sign-in form
 * is not left guessing. The trade-off is deliberate and worth stating - this
 * lets anyone who reaches the portal enumerate which usernames exist. The
 * per-MAC rate limit (5 failures / 5 minutes) is what bounds brute force.
 */
enum class LoginFailure { INVALID, NO_SUCH_USER, WRONG_PASSWORD, DISABLED, EXPIRED, DEVICE_LIMIT, LOCKED }

/**
 * Result of an admin-side credential check.
 *
 * Admin-only, and deliberately more precise than [LoginFailure.INVALID]: the
 * portal's /api/login must never say "no such user", or anyone on the LAN
 * could enumerate accounts. This is only reachable from the local app UI, where
 * the person using it already owns every account, and it is the only way to
 * tell a mistyped username from a mistyped password.
 */
enum class CredentialVerdict { CORRECT, NO_SUCH_USER, WRONG_PASSWORD, DISABLED, EXPIRED }

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
            ?: return LoginResult.Failure(LoginFailure.NO_SUCH_USER)
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return LoginResult.Failure(LoginFailure.WRONG_PASSWORD)
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

    /**
     * Checks a username/password pair and says which half is wrong.
     *
     * Local admin use only - see [CredentialVerdict]. This is also why a stored
     * password can never be shown: only the BCrypt hash exists, which is the
     * point of storing a hash. A lost password is replaced, not recovered.
     */
    suspend fun diagnose(username: String, password: String): CredentialVerdict {
        val user = userDao.byUsername(username.trim().lowercase())
            ?: return CredentialVerdict.NO_SUCH_USER
        if (!BCrypt.checkpw(password, user.passwordHash)) return CredentialVerdict.WRONG_PASSWORD
        if (!user.enabled) return CredentialVerdict.DISABLED
        if (user.expired) return CredentialVerdict.EXPIRED
        return CredentialVerdict.CORRECT
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
