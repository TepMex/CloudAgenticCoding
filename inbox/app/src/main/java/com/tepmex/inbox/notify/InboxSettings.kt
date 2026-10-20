package com.tepmex.inbox.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InboxSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _haptic = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val haptic: StateFlow<Boolean> = _haptic.asStateFlow()

    private val _openKeyboard = MutableStateFlow(prefs.getBoolean(KEY_KEYBOARD, true))
    val openKeyboard: StateFlow<Boolean> = _openKeyboard.asStateFlow()

    private val _showOnLockScreen = MutableStateFlow(prefs.getBoolean(KEY_LOCK_SCREEN, false))
    val showOnLockScreen: StateFlow<Boolean> = _showOnLockScreen.asStateFlow()

    fun setHaptic(value: Boolean) = write(KEY_HAPTIC, value, _haptic)

    fun setOpenKeyboard(value: Boolean) = write(KEY_KEYBOARD, value, _openKeyboard)

    fun setShowOnLockScreen(value: Boolean) = write(KEY_LOCK_SCREEN, value, _showOnLockScreen)

    private fun write(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    companion object {
        private const val PREFS = "inbox_settings"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_KEYBOARD = "open_keyboard"
        private const val KEY_LOCK_SCREEN = "lock_screen_text"
    }
}
