package com.tepmex.ankidashboard.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankidashboard.AnkiDashboardApp
import com.tepmex.ankidashboard.R
import com.tepmex.ankidashboard.data.AppPreferences
import com.tepmex.ankidashboard.data.sync.AnkiWebSync
import com.tepmex.ankidashboard.data.sync.SyncHttpClient
import com.tepmex.ankidashboard.databinding.ActivitySyncSettingsBinding
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
            showError(getString(R.string.ankiweb_username_required))
            return
        }

        binding.syncButton.isEnabled = false
        binding.saveButton.isEnabled = false
        binding.syncProgress.isVisible = true
        binding.syncErrorText.isVisible = false
        binding.syncStatusText.isVisible = true
        binding.syncStatusText.text = getString(R.string.ankiweb_sync_starting)

        lifecycleScope.launch {
            try {
                val auth = preferences.getAnkiWebAuth()
                val sync = AnkiWebSync(
                    context = this@SyncSettingsActivity,
                    endpoint = if (!auth.hkey.isNullOrBlank() && auth.endpoint != null) {
                        auth.endpoint
                    } else {
                        endpoint
                    },
                    hkey = auth.hkey.orEmpty(),
                )
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
                val mod = result.serverMeta?.opt("mod")?.toString() ?: "?"
                val mb = result.byteLength / (1024.0 * 1024.0)
                binding.syncStatusText.text = getString(R.string.ankiweb_sync_complete, mb, mod)
                binding.passwordInput.text?.clear()
                binding.logoutButton.isVisible = true
                updateLastSync(System.currentTimeMillis())
                setResult(RESULT_OK)
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.ankiweb_sync_failed))
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

    private fun showError(message: String) {
        binding.syncErrorText.isVisible = true
        binding.syncErrorText.text = message
    }
}
