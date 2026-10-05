package com.tepmex.cornegame.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Короткая слабая вибрация. На API 24 нет VibrationEffect, поэтому ветка ниже 26. */
fun vibrateLight(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(24, 50))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(24)
    }
}

@Composable
fun ErrorFeedback(errorNonce: Int, enabled: Boolean) {
    val context = LocalContext.current
    // Ключ только nonce: включение тумблера вибрации само по себе не должно вибрировать.
    LaunchedEffect(errorNonce) {
        if (errorNonce != 0 && enabled) vibrateLight(context)
    }
}
