package com.tepmex.duoshaoqian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tepmex.duoshaoqian.DuoShaoQianApp
import com.tepmex.duoshaoqian.data.AnswerFeedback
import com.tepmex.duoshaoqian.data.ChineseMoney
import com.tepmex.duoshaoqian.data.ProductCatalog
import com.tepmex.duoshaoqian.data.ShopGame
import com.tepmex.duoshaoqian.data.ShopSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface GameMessage {
    data object NeedListen : GameMessage
    data object Incorrect : GameMessage
    data class Correct(val priceYuan: Int, val spoken: String) : GameMessage
    data class Skipped(val priceYuan: Int, val spoken: String) : GameMessage
}

data class GameUiState(
    val snapshot: ShopSnapshot?,
    val error: String? = null,
    val message: GameMessage? = null,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DuoShaoQianApp
    private val products = ProductCatalog.playable(app.catalog.amounts)
    private val game: ShopGame? = if (products.isEmpty()) null else ShopGame(products)

    private val _state = MutableStateFlow(
        if (game == null) {
            GameUiState(snapshot = null, error = "catalog")
        } else {
            GameUiState(snapshot = game.snapshot)
        },
    )
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    fun askPrice() {
        val current = game?.snapshot ?: return
        val clip = app.catalog.clip(current.round.priceYuan) ?: return
        game.markListened()
        publish()
        app.audioPlayer.play(clip.audioBytes())
    }

    fun addNote(yuan: Int) {
        game?.addNote(yuan)
        publish()
    }

    fun removeNoteAt(index: Int) {
        game?.removeNoteAt(index)
        publish()
    }

    fun clearTender() {
        game?.clearTender()
        publish()
    }

    fun pay() {
        val currentGame = game ?: return
        val result = currentGame.pay()
        AnswerFeedback.toneFor(result)?.let { tone ->
            app.audioPlayer.stop()
            app.answerSfx.play(tone)
        }
        when (result) {
            ShopGame.PayResult.NeedListen -> publish(GameMessage.NeedListen)
            ShopGame.PayResult.Incorrect -> publish(GameMessage.Incorrect)
            ShopGame.PayResult.Correct -> {
                val price = currentGame.snapshot.round.priceYuan
                val spoken = spokenFor(price)
                currentGame.advanceAfterSuccess()
                publish(GameMessage.Correct(price, spoken))
            }
        }
    }

    fun consumeMessage() {
        publish(message = null)
    }

    fun skip() {
        val currentGame = game ?: return
        val price = currentGame.snapshot.round.priceYuan
        val spoken = spokenFor(price)
        currentGame.skip()
        publish(GameMessage.Skipped(price, spoken))
    }

    private fun spokenFor(priceYuan: Int): String {
        return app.catalog.clip(priceYuan)?.hanzi ?: ChineseMoney.toSpokenKuai(priceYuan)
    }

    private fun publish(message: GameMessage? = _state.value.message) {
        _state.value = GameUiState(
            snapshot = game?.snapshot,
            error = if (game == null) "catalog" else null,
            message = message,
        )
    }

    override fun onCleared() {
        app.audioPlayer.stop()
        app.answerSfx.stop()
        super.onCleared()
    }
}
