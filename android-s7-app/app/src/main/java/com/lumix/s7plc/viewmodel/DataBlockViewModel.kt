package com.lumix.s7plc.viewmodel

import com.lumix.s7plc.model.ByteRow
import com.lumix.s7plc.model.PlcRepository
import com.lumix.s7plc.protocol.S7Area
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class DbReadState(
    val isLoading: Boolean = false,
    val rows: List<ByteRow> = emptyList(),
    val errorMessage: String? = null,
    val dbNumber: Int = 1,
    val startByte: Int = 0,
    val sizeBytes: Int = 64
)

class DataBlockViewModel(private val repository: PlcRepository) {

    // Use IO dispatcher directly – avoids Kotlin 1.3 bug with Int params in nested withContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val state = ObservableValue(DbReadState())
    val writeStatus = ObservableValue<String>("")

    fun readDB(dbNumber: Int, startByte: Int, sizeBytes: Int) {
        // Bundle ints into array to avoid Kotlin 1.3 JVM bug with 3+ Int params in lambda
        val p = intArrayOf(dbNumber, startByte, sizeBytes)
        state.postValue(state.value.copy(
            isLoading = true, errorMessage = null,
            dbNumber = p[0], startByte = p[1], sizeBytes = p[2]
        ))
        scope.launch {
            try {
                val buf = repository.readDB(p[0], p[1], p[2])
                val rows = repository.formatBytes(buf, p[1])
                state.postValue(state.value.copy(isLoading = false, rows = rows))
            } catch (e: Exception) {
                state.postValue(state.value.copy(isLoading = false, errorMessage = e.message))
            }
        }
    }

    fun readArea(area: S7Area, startByte: Int, sizeBytes: Int) {
        val p = intArrayOf(startByte, sizeBytes)
        state.postValue(state.value.copy(isLoading = true, errorMessage = null))
        scope.launch {
            try {
                val buf = repository.readArea(area, 0, p[0], p[1])
                val rows = repository.formatBytes(buf, p[0])
                state.postValue(state.value.copy(isLoading = false, rows = rows))
            } catch (e: Exception) {
                state.postValue(state.value.copy(isLoading = false, errorMessage = e.message))
            }
        }
    }

    fun writeSingleByte(dbNumber: Int, byteAddress: Int, value: Int) {
        // Bundle ints into array to avoid Kotlin 1.3 JVM bug with 3+ Int params in lambda
        val p = intArrayOf(dbNumber, byteAddress, value)
        scope.launch {
            try {
                repository.writeDB(p[0], p[1], byteArrayOf(p[2].toByte()))
                writeStatus.postValue("DB${p[0]}.DBB${p[1]} = ${p[2]} geschrieben")
                readDB(p[0], state.value.startByte, state.value.sizeBytes)
            } catch (e: Exception) {
                writeStatus.postValue("Fehler: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        scope.cancel()
    }
}
