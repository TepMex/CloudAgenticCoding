package com.tepmex.ankidashboard.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankidashboard.AnkiDashboardApp
import com.tepmex.ankidashboard.R
import com.tepmex.ankidashboard.data.AppPreferences
import com.tepmex.ankidashboard.data.sync.AnkiWebSync
import com.tepmex.ankidashboard.data.sync.CollectionStore
import com.tepmex.ankidashboard.data.sync.SyncDiagnostics
import com.tepmex.ankidashboard.data.sync.SyncException
import com.tepmex.ankidashboard.data.sync.SyncHttpClient
import com.tepmex.ankidashboard.databinding.ActivitySyncSettingsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class SyncSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncSettingsBinding
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = (application as AnkiDashboardApp).preferences

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ankiweb_settings_title)

        lifecycleScope.launch {
            val username = preferences.ankiWebUsername.first()
            val endpoint = preferences.ankiWebEndpoint.first()
            val syncedAt = preferences.ankiWebSyncedAt.first()
            val auth = preferences.getAnkiWebAuth()
            binding.usernameInput.setText(username)
            binding.endpointInput.setText(endpoint)
            binding.passwordInput.hint = if (!auth.hkey.isNullOrBlank()) {
                getString(R.string.ankiweb_password_reuse)
            } else {
                getString(R.string.ankiweb_password)
            }
            updateLastSync(syncedAt)
            binding.logoutButton.isVisible = !auth.hkey.isNullOrBlank()
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                preferences.saveAnkiWebSettings(
                    binding.usernameInput.text?.toString().orEmpty().trim(),
                    SyncHttpClient.resolveSyncBaseUrl(binding.endpointInput.text?.toString()),
                )
                binding.syncErrorText.isVisible = false
                binding.syncStatusText.isVisible = true
                binding.syncStatusText.text = getString(R.string.ankiweb_saved)
            }
        }

        binding.syncButton.setOnClickListener { runSync() }

        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                preferences.clearAnkiWebAuth()
                binding.passwordInput.text?.clear()
                binding.logoutButton.isVisible = false
                binding.syncStatusText.isVisible = true
                binding.syncStatusText.text = getString(R.string.ankiweb_logged_out)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun runSync() {
        val username = binding.usernameInput.text?.toString().orEmpty().trim()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val endpoint = SyncHttpClient.resolveSyncBaseUrl(binding.endpointInput.text?.toString())
        if (username.isBlank()) {
            showSyncError(
                summary = getString(R.string.ankiweb_username_required),
                details = getString(R.string.ankiweb_username_required),
                phase = "validation",
            )
            return
        }

        binding.syncButton.isEnabled = false
        binding.saveButton.isEnabled = false
        binding.syncProgress.isVisible = true
        binding.syncErrorText.isVisible = false
        binding.syncStatusText.isVisible = true
        binding.syncStatusText.text = getString(R.string.ankiweb_sync_starting)

        lifecycleScope.launch {
            var syncClient: SyncHttpClient? = null
            var reusedSession = false
            try {
                val auth = preferences.getAnkiWebAuth()
                val canReuseSession = password.isBlank() &&
                    !auth.hkey.isNullOrBlank() &&
                    auth.username == username &&
                    SyncHttpClient.resolveSyncBaseUrl(auth.endpoint) == endpoint
                reusedSession = canReuseSession

                val syncEndpoint = if (canReuseSession && auth.endpoint != null) {
                    auth.endpoint
                } else {
                    endpoint
                }

                val sync = AnkiWebSync(
                    context = this@SyncSettingsActivity,
                    endpoint = syncEndpoint,
                    hkey = if (canReuseSession) auth.hkey.orEmpty() else "",
                )
                syncClient = sync.clientForDiagnostics()

                val result = sync.sync(
                    username = username,
                    password = password.ifBlank { null },
                    endpoint = endpoint,
                    preferences = preferences,
                ) { progress ->
                    runOnUiThread {
                        binding.syncStatusText.text = when (progress.phase) {
                            "meta" -> getString(R.string.ankiweb_sync_meta)
                            "download" -> {
                                val mb = progress.received / (1024.0 * 1024.0)
                                getString(R.string.ankiweb_sync_download, mb)
                            }
                            "saving" -> getString(R.string.ankiweb_sync_saving)
                            else -> getString(R.string.ankiweb_sync_starting)
                        }
                    }
                }
                val mod = result.serverMeta?.let { meta ->
                    when {
                        meta.has("mod") && !meta.isNull("mod") -> meta.get("mod").toString()
                        else -> null
                    }
                } ?: CollectionStore.serverModLabel(this@SyncSettingsActivity)
                    ?: getString(R.string.ankiweb_mod_unknown)
                val mb = result.byteLength / (1024.0 * 1024.0)
                binding.syncStatusText.text = getString(R.string.ankiweb_sync_complete, mb, mod)
                binding.passwordInput.text?.clear()
                binding.logoutButton.isVisible = true
                updateLastSync(System.currentTimeMillis())
                // Drop manual collection pick so the fresh AnkiWeb cache is used on the dashboard.
                preferences.setCollectionUri(null)
                setResult(RESULT_OK)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SyncDiagnostics.logFailure(
                    phase = (e as? SyncException)?.phase,
                    throwable = e,
                )
                val report = SyncDiagnostics.buildReport(
                    throwable = e,
                    phase = (e as? SyncException)?.phase,
                    username = username,
                    endpoint = endpoint,
                    syncHost = syncClient?.syncHost,
                    baseUrl = syncClient?.baseUrl,
                    reusedSession = reusedSession,
                )
                showSyncError(report.summary, report.details, (e as? SyncException)?.phase)
            } finally {
                binding.syncButton.isEnabled = true
                binding.saveButton.isEnabled = true
                binding.syncProgress.isVisible = false
            }
        }
    }

    private fun updateLastSync(timestamp: Long?) {
        val label = if (timestamp != null && timestamp > 0L) {
            DateFormat.getDateTimeInstance().format(Date(timestamp))
        } else {
            getString(R.string.ankiweb_never)
        }
        binding.lastSyncText.text = getString(R.string.ankiweb_last_sync, label)
    }

    private fun showSyncError(summary: String, details: String, phase: String?) {
        binding.syncErrorText.isVisible = true
        binding.syncErrorText.text = summary
        binding.syncStatusText.isVisible = false

        val messageView = TextView(this).apply {
            text = details
            movementMethod = ScrollingMovementMethod()
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 8)
        }

        AlertDialog.Builder(this)
            .setTitle(
                phase?.let { getString(R.string.ankiweb_sync_error_title_phase, it) }
                    ?: getString(R.string.ankiweb_sync_error_title),
            )
            .setMessage(summary)
            .setView(messageView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.ankiweb_copy_error) { _, _ ->
                SyncDiagnostics.copyToClipboard(this, details)
            }
            .show()
    }
}
