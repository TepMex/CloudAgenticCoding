package com.tepmex.idealtiming.mi

open class MiException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class AuthException(message: String, cause: Throwable? = null) : MiException(message, cause)

class DeviceUntrustedException(message: String = "Device untrusted; SMS verification required") :
    AuthException(message)

class CaptchaRequiredException(
    message: String,
    val captchaUrl: String,
) : AuthException(message)

/** Password login needs interactive 2FA / identity check in a browser or WebView. */
class NotificationUrlRequiredException(
    message: String,
    val notificationUrl: String,
) : AuthException(message)

class TokenExpiredException(message: String = "Auth token expired") : AuthException(message)

class BrowserLoginTimeoutException(
    message: String = "Xiaomi browser sign-in timed out",
) : AuthException(message)

class BrowserLoginCancelledException(
    message: String = "Xiaomi browser sign-in cancelled",
) : AuthException(message)

class ApiException(
    message: String,
    val code: Int = -1,
    cause: Throwable? = null,
) : MiException(message, cause)
