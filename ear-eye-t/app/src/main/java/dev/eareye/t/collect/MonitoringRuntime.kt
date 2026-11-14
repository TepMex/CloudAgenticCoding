package dev.eareye.t.collect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeSnapshot(
    val listenerConnected: Boolean = false,
    val audioActive: Boolean = false,
    val sessionId: Long? = null,
)

object MonitoringRuntime {
    private val stateFlow = MutableStateFlow(RuntimeSnapshot())
    val state: StateFlow<RuntimeSnapshot> = stateFlow.asStateFlow()

    fun update(block: (RuntimeSnapshot) -> RuntimeSnapshot) {
        stateFlow.value = block(stateFlow.value)
    }
}

object MonitorBridge {
    @Volatile
    var resnapshot: (() -> Unit)? = null
}
