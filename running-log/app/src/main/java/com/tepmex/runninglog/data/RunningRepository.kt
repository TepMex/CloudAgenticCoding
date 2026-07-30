package com.tepmex.runninglog.data

import com.tepmex.runninglog.mi.AuthException
import com.tepmex.runninglog.mi.AuthToken
import com.tepmex.runninglog.mi.DeviceUntrustedException
import com.tepmex.runninglog.mi.MiAuth
import com.tepmex.runninglog.mi.MiConstants
import com.tepmex.runninglog.mi.MiHealthClient
import kotlinx.coroutines.flow.Flow

data class SyncResult(
    val imported: Int,
    val message: String,
)

class RunningRepository(
    private val dao: RunningActivityDao,
    private val tokenStore: AuthTokenStore,
    private val auth: MiAuth = MiAuth(),
) {
    val activities: Flow<List<RunningActivityEntity>> = dao.observeAll()

    fun currentToken(): AuthToken? = tokenStore.load()?.also { auth.restore(it) }

    fun isSignedIn(): Boolean = currentToken()?.isAuthenticated == true

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
        val runs = client.fetchRunningWorkouts(uid, startWatermark = watermark)
        // Persist refreshed token if STS/refresh mutated it
        tokenStore.save(auth.token.takeIf { it.isAuthenticated } ?: token)
        if (runs.isEmpty()) {
            return SyncResult(0, "No new running activities")
        }
        dao.upsertAll(runs.map { RunningActivityEntity.fromParsed(it) })
        return SyncResult(runs.size, "Imported ${runs.size} run(s)")
    }

    companion object {
        val regions = listOf("ru", "cn")
        val defaultRegion = MiConstants.REGION_TAG_DEFAULT
    }
}
