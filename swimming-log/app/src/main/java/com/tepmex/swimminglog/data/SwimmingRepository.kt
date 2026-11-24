package com.tepmex.swimminglog.data

import com.tepmex.swimminglog.mi.AuthException
import com.tepmex.swimminglog.mi.AuthToken
import com.tepmex.swimminglog.mi.BrowserLoginSession
import com.tepmex.swimminglog.mi.DeviceUntrustedException
import com.tepmex.swimminglog.mi.MiAuth
import com.tepmex.swimminglog.mi.MiConstants
import com.tepmex.swimminglog.mi.MiHealthClient
import kotlinx.coroutines.flow.Flow

data class SyncResult(
    val imported: Int,
    val message: String,
)

class SwimmingRepository(
    private val dao: SwimmingActivityDao,
    private val tokenStore: AuthTokenStore,
    private val auth: MiAuth = MiAuth(),
) {
    val activities: Flow<List<SwimmingActivityEntity>> = dao.observeAll()

    fun currentToken(): AuthToken? = tokenStore.load()?.also { auth.restore(it) }

    fun isSignedIn(): Boolean = currentToken()?.isAuthenticated == true

    suspend fun startBrowserLogin(): BrowserLoginSession = auth.startBrowserLoginSession()

    suspend fun completeBrowserLogin(
        session: BrowserLoginSession,
        region: String,
    ): AuthToken {
        val token = auth.awaitBrowserLogin(session, region)
        tokenStore.save(token)
        return token
    }

    suspend fun loginWithPassToken(
        passToken: String,
        userId: String,
        region: String,
        deviceId: String = "",
    ): AuthToken {
        val token = auth.loginWithPassToken(passToken, userId, region, deviceId)
        tokenStore.save(token)
        return token
    }

    suspend fun login(username: String, password: String, region: String): AuthToken {
        return try {
            val token = auth.login(username, password, region)
            tokenStore.save(token)
            token
        } catch (e: DeviceUntrustedException) {
            // Keep partial auth state in MiAuth for SMS
            tokenStore.save(
                auth.token.copy(
                    region = region,
                    username = username,
                ),
            )
            throw e
        }
    }

    suspend fun sendSmsCode(): String = auth.sendVerificationCode()

    suspend fun confirmSms(code: String): AuthToken {
        val token = auth.loginWithVerificationCode(code)
        tokenStore.save(token)
        return token
    }

    fun signOut() {
        auth.clear()
        tokenStore.clear()
    }

    suspend fun sync(): SyncResult {
        val token = currentToken() ?: throw AuthException("Not signed in")
        if (!token.isAuthenticated) throw AuthException("Not signed in")
        val uid = token.userId.toLongOrNull()
            ?: throw AuthException("Invalid user id in token")
        val client = MiHealthClient(token, auth)
        val watermark = dao.maxWatermark()
        val swims = client.fetchSwimmingWorkouts(uid, startWatermark = watermark)
        // Persist refreshed token if STS/refresh mutated it
        tokenStore.save(auth.token.takeIf { it.isAuthenticated } ?: token)
        if (swims.isEmpty()) {
            return SyncResult(0, "No new swimming activities")
        }
        val entities = swims
            .map { SwimmingActivityEntity.fromParsed(it) }
            .distinctBy { it.workoutId }
        dao.upsertAll(entities)
        return SyncResult(entities.size, "Imported ${entities.size} swim(s)")
    }

    companion object {
        val regions = listOf("ru", "cn")
        val defaultRegion = MiConstants.REGION_TAG_DEFAULT
    }
}
