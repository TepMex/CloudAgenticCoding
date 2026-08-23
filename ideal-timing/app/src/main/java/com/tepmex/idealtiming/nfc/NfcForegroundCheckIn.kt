package com.tepmex.idealtiming.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * While this composable is in the composition and the host activity is resumed,
 * any NFC tag (NDEF or not) is treated as a physical check-in.
 *
 * No-op on devices without NFC or when the adapter is powered off.
 */
@Composable
fun NfcForegroundCheckIn(onTag: () -> Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestOnTag by rememberUpdatedState(onTag)

    DisposableEffect(activity, lifecycleOwner) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            return@DisposableEffect onDispose { }
        }
        val callback = NfcAdapter.ReaderCallback {
            activity.runOnUiThread {
                if (latestOnTag()) {
                    vibrateCheckIn(activity)
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (adapter.isEnabled) {
                        adapter.enableReaderMode(activity, callback, flags, null)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> disableQuietly(adapter, activity)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && adapter.isEnabled) {
            adapter.enableReaderMode(activity, callback, flags, null)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disableQuietly(adapter, activity)
        }
    }
}

private fun disableQuietly(adapter: NfcAdapter, activity: Activity) {
    try {
        adapter.disableReaderMode(activity)
    } catch (_: Exception) {
        // Adapter may already be off or reader mode was never enabled.
    }
}

private fun vibrateCheckIn(activity: Activity) {
    val vibrator = activity.getSystemService(VibratorManager::class.java)?.defaultVibrator
        ?: activity.getSystemService(Vibrator::class.java)
        ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
}
