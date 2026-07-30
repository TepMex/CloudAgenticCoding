package com.tepmex.runninglog.mi

open class MiException(message: String, cause: Throwable? = null) : Exception(message, cause)

open class AuthException(message: String, cause: Throwable? = null) : MiException(message, cause)

class DeviceUntrustedException(message: String = "Device untrusted; SMS verification required") :
    AuthException(message)

class CaptchaRequiredException(
    message: String,
    val captchaUrl: String,
) : AuthException(message)

class TokenExpiredException(message: String = "Auth token expired") : AuthException(message)

class ApiException(
    message: String,
    val code: Int = -1,
    cause: Throwable? = null,
) : MiException(message, cause)
