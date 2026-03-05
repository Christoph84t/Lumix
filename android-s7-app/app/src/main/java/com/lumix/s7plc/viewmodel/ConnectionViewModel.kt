package com.lumix.s7plc.viewmodel

import com.lumix.s7plc.model.PlcConnection
import com.lumix.s7plc.model.PlcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val pduSize: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ConnectionViewModel {

    val repository = PlcRepository()
    // Use IO dispatcher directly – avoids Kotlin 1.3 bug with Int params in nested withContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val state = ObservableValue<ConnectionState>(ConnectionState.Disconnected)

    var lastHost = "192.168.0.1"
    var lastRack = 0
    var lastSlot = 1

    fun connect(host: String, rack: Int, slot: Int, alias: String = "") {
        lastHost = host; lastRack = rack; lastSlot = slot
        state.postValue(ConnectionState.Connecting)
        // Create conn before scope.launch to avoid capturing Int params in lambda
        val conn = PlcConnection(host.trim(), rack, slot, alias.trim())
        scope.launch {
            try {
                repository.connect(conn)
                state.postValue(ConnectionState.Connected(repository.negotiatedPduSize))
            } catch (e: Exception) {
                state.postValue(ConnectionState.Error(e.message ?: "Unbekannter Fehler"))
            }
        }
    }

    fun disconnect() {
        scope.launch {
            repository.disconnect()
            state.postValue(ConnectionState.Disconnected)
        }
    }

    fun onDestroy() {
        scope.launch { repository.disconnect() }
        scope.cancel()
    }
}
