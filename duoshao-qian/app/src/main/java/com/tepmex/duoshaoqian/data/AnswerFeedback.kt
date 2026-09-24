package com.tepmex.duoshaoqian.data

enum class AnswerTone(val fileName: String) {
    Success("sfx_success.mp3"),
    Error("sfx_error.mp3"),
}

object AnswerFeedback {
    fun toneFor(result: ShopGame.PayResult): AnswerTone? = when (result) {
        ShopGame.PayResult.Correct -> AnswerTone.Success
        ShopGame.PayResult.Incorrect -> AnswerTone.Error
        ShopGame.PayResult.NeedListen -> null
    }
}
