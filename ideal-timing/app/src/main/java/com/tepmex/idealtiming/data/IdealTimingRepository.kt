package com.tepmex.idealtiming.data

import com.tepmex.idealtiming.domain.SleepRecordParser
import com.tepmex.idealtiming.mi.AuthException
import com.tepmex.idealtiming.mi.AuthToken
import com.tepmex.idealtiming.mi.BrowserLoginSession
import com.tepmex.idealtiming.mi.DeviceUntrustedException
import com.tepmex.idealtiming.mi.MiAuth
import com.tepmex.idealtiming.mi.MiConstants
import com.tepmex.idealtiming.mi.MiHealthClient

data class SyncResult(
    val wake: WakeSnapshot,
    val message: String,
)

class IdealTimingRepository(
    private val tokenStore: AuthTokenStore,
    private val wakeStore: WakeSnapshotStore,
    private val auth: MiAuth = MiAuth(),
) {
    fun currentToken(): AuthToken? = tokenStore.load()?.also { auth.restore(it) }

    fun isSignedIn(): Boolean = currentToken()?.isAuthenticated == true

    fun currentWake(): WakeSnapshot? = wakeStore.load()

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
        // Keep last wake snapshot so a re-login still has a clock until next sync.
    }

    suspend fun syncWake(
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
        days: Int = 3,
    ): SyncResult {
        val token = currentToken() ?: throw AuthException("Not signed in")
        if (!token.isAuthenticated) throw AuthException("Not signed in")
        val uid = token.userId.toLongOrNull()
            ?: throw AuthException("Invalid user id in token")
        val client = MiHealthClient(token, auth)
        val records = client.fetchSleepRecords(uid, days = days, nowEpochSec = nowEpochSec)
        tokenStore.save(auth.token.takeIf { it.isAuthenticated } ?: token)
        val choice = SleepRecordParser.chooseWake(records, nowEpochSec)
            ?: throw IllegalStateException("No sleep wake-up time in the last $days day(s)")
        val snap = WakeSnapshot(
            wakeEpochSec = choice.wakeEpochSec,
            syncedAtEpochSec = nowEpochSec,
            sourceDateEpochSec = choice.sourceDateEpochSec,
            sleepScore = choice.sleepScore,
        )
        wakeStore.save(snap)
        return SyncResult(snap, "Wake synced")
    }

    companion object {
        val regions = listOf("ru", "cn")
        val defaultRegion = MiConstants.REGION_TAG_DEFAULT
    }
}
