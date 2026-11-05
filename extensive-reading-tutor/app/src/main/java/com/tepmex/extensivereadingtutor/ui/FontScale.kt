package com.tepmex.extensivereadingtutor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * Android 14, the app's minimum, lets the system font scale reach 200%.
 * Equal-width Russian labels in the tab bar, shelf filters, and profile counts
 * stop fitting once the scale passes the old 130% stop.
 */
internal const val LargeSystemFontScale = 1.5f

@Composable
@ReadOnlyComposable
internal fun largeSystemFont(): Boolean = LocalDensity.current.fontScale >= LargeSystemFontScale
