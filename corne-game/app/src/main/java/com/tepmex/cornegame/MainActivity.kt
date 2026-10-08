package com.tepmex.cornegame

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.mapHardwareKey
import com.tepmex.cornegame.ui.CorneApp
import com.tepmex.cornegame.ui.TrainViewModel
import com.tepmex.cornegame.ui.theme.CorneTheme
import com.tepmex.cornegame.ui.trainViewModelFactory

/**
 * Одна activity. Поворот её пересоздаёт, но [TrainViewModel] остаётся в store
 * и сессия не обнуляется. configChanges специально не перехватываем.
 *
 * Символ читаем на ACTION_DOWN: у части Bluetooth-клавиатур unicode на UP пустой.
 * В сессию отдаём на ACTION_UP, как просит тренажёр, по одному разу на нажатие.
 */
class MainActivity : ComponentActivity() {
    private val trainViewModel: TrainViewModel by viewModels { trainViewModelFactory(this) }

    private var pending: TrainerAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CorneTheme {
                CorneApp(trainViewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val mapped = mapHardwareKey(event.keyCode, event.unicodeChar, event.isAltPressed)
            ?: return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) pending = mapped
                return true
            }
            KeyEvent.ACTION_UP -> {
                val action = pending ?: mapped
                pending = null
                trainViewModel.onTrainerAction(action)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
