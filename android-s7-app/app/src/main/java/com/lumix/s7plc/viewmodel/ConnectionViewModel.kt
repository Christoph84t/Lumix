package com.lumix.s7plc.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumix.s7plc.model.PlcConnection
import com.lumix.s7plc.model.PlcRepository
import kotlinx.coroutines.launch

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val pduSize: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ConnectionViewModel : ViewModel() {

    val repository = PlcRepository()

    private val _state = MutableLiveData<ConnectionState>(ConnectionState.Disconnected)
    val state: LiveData<ConnectionState> = _state

    // Zuletzt verwendete Verbindungsparameter
    var lastHost = "192.168.0.1"
    var lastRack = 0
    var lastSlot = 1

    fun connect(host: String, rack: Int, slot: Int, alias: String = "") {
        lastHost = host; lastRack = rack; lastSlot = slot
        _state.value = ConnectionState.Connecting
        viewModelScope.launch {
            val conn = PlcConnection(host.trim(), rack, slot, alias.trim())
            repository.connect(conn).fold(
                onSuccess = {
                    _state.postValue(ConnectionState.Connected(repository.negotiatedPduSize))
                },
                onFailure = { e ->
                    _state.postValue(ConnectionState.Error(e.message ?: "Unbekannter Fehler"))
                }
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _state.postValue(ConnectionState.Disconnected)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { repository.disconnect() }
    }
}
