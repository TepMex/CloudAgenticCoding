package com.tepmex.heilauncher.domain

/**
 * Implicit intents for the default calendar and clock.
 *
 * Clock tries the platform clock category first, then the launcher activity of
 * whichever app handles [ACTION_SHOW_ALARMS]. Calendar tries the platform
 * calendar category, then a view of the current instant in the calendar provider.
 */
object DefaultApps {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val CATEGORY_APP_CALENDAR = "android.intent.category.APP_CALENDAR"
    const val CATEGORY_APP_CLOCK = "android.intent.category.APP_CLOCK"
    const val ACTION_SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"
    private const val CALENDAR_TIME = "content://com.android.calendar/time/"

    fun calendar(nowMillis: Long): List<AppIntentSpec> = listOf(
        AppIntentSpec(
            action = ACTION_MAIN,
            categories = listOf(CATEGORY_APP_CALENDAR),
        ),
        AppIntentSpec(
            action = ACTION_VIEW,
            data = CALENDAR_TIME + nowMillis,
        ),
    )

    fun clock(): List<AppIntentSpec> = listOf(
        AppIntentSpec(
            action = ACTION_MAIN,
            categories = listOf(CATEGORY_APP_CLOCK),
        ),
        AppIntentSpec(
            action = ACTION_SHOW_ALARMS,
            openLauncher = true,
        ),
    )
}

/** True when resolution landed on the system disambiguation dialog, not an app. */
fun isSystemChooser(packageName: String?, className: String?): Boolean {
    if (packageName == null || packageName == "android") return true
    val name = className.orEmpty()
    return name.endsWith("ResolverActivity") ||
        name.endsWith("ChooserActivity") ||
        packageName == "com.android.intentresolver" ||
        packageName == "com.google.android.intentresolver"
}

data class AppIntentSpec(
    val action: String,
    val categories: List<String> = emptyList(),
    /** Open the resolved package's launcher activity instead of this intent. */
    val openLauncher: Boolean = false,
    val data: String? = null,
)
