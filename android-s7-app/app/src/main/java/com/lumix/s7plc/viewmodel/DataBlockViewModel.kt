package com.lumix.s7plc.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumix.s7plc.model.ByteRow
import com.lumix.s7plc.model.PlcRepository
import com.lumix.s7plc.protocol.S7Area
import kotlinx.coroutines.launch

data class DbReadState(
    val isLoading: Boolean = false,
    val rows: List<ByteRow> = emptyList(),
    val errorMessage: String? = null,
    val dbNumber: Int = 1,
    val startByte: Int = 0,
    val sizeBytes: Int = 64
)

class DataBlockViewModel(private val repository: PlcRepository) : ViewModel() {

    private val _state = MutableLiveData(DbReadState())
    val state: LiveData<DbReadState> = _state

    private val _writeStatus = MutableLiveData<String>()
    val writeStatus: LiveData<String> = _writeStatus

    fun readDB(dbNumber: Int, startByte: Int, sizeBytes: Int) {
        _state.value = _state.value?.copy(
            isLoading = true,
            errorMessage = null,
            dbNumber = dbNumber,
            startByte = startByte,
            sizeBytes = sizeBytes
        )
        viewModelScope.launch {
            repository.readDB(dbNumber, startByte, sizeBytes).fold(
                onSuccess = { buf ->
                    val rows = repository.formatBytes(buf, startByte)
                    _state.postValue(_state.value?.copy(isLoading = false, rows = rows))
                },
                onFailure = { e ->
                    _state.postValue(_state.value?.copy(isLoading = false, errorMessage = e.message))
                }
            )
        }
    }

    fun readArea(area: S7Area, startByte: Int, sizeBytes: Int) {
        _state.value = _state.value?.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            repository.readArea(area, 0, startByte, sizeBytes).fold(
                onSuccess = { buf ->
                    val rows = repository.formatBytes(buf, startByte)
                    _state.postValue(_state.value?.copy(isLoading = false, rows = rows))
                },
                onFailure = { e ->
                    _state.postValue(_state.value?.copy(isLoading = false, errorMessage = e.message))
                }
            )
        }
    }

    fun writeSingleByte(dbNumber: Int, byteAddress: Int, value: Int) {
        viewModelScope.launch {
            repository.writeDB(dbNumber, byteAddress, byteArrayOf(value.toByte())).fold(
                onSuccess = {
                    _writeStatus.postValue("DB$dbNumber.DBB$byteAddress = $value geschrieben")
                    readDB(dbNumber, _state.value?.startByte ?: 0, _state.value?.sizeBytes ?: 64)
                },
                onFailure = { e ->
                    _writeStatus.postValue("Fehler: ${e.message}")
                }
            )
        }
    }
}
