package com.lumix.s7plc.viewmodel

import com.lumix.s7plc.model.PlcRepository
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.model.TagValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TagState(
    val tag: PlcTag,
    val value: TagValue = TagValue.ErrorValue("Noch nicht gelesen"),
    val isLoading: Boolean = false
)

class MonitorViewModel(private val repository: PlcRepository) {

    // Use IO dispatcher directly – avoids Kotlin 1.3 bug with ByteArray/Int in nested withContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val tags = ObservableValue<List<TagState>>(emptyList())
    val statusMessage = ObservableValue<String>("")

    var pollingIntervalMs: Long = 1000L
    private var pollingJob: Job? = null

    fun addTag(tag: PlcTag) {
        val current = tags.value.toMutableList()
        if (current.none { it.tag.id == tag.id }) {
            current.add(TagState(tag))
            tags.postValue(current)
        }
    }

    fun removeTag(tagId: String) {
        tags.postValue(tags.value.filter { it.tag.id != tagId })
    }

    fun clearTags() {
        tags.postValue(emptyList())
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                readAllTags()
                delay(pollingIntervalMs)
            }
        }
        statusMessage.postValue("Monitoring gestartet (Intervall: ${pollingIntervalMs}ms)")
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        statusMessage.postValue("Monitoring gestoppt")
    }

    fun readOnce() {
        scope.launch { readAllTags() }
    }

    private suspend fun readAllTags() {
        val current = tags.value
        if (current.isEmpty()) return
        val updated = current.map { state ->
            val value = try {
                repository.readTag(state.tag)
            } catch (e: Exception) {
                TagValue.ErrorValue(e.message ?: "Lesefehler")
            }
            state.copy(value = value, isLoading = false)
        }
        tags.postValue(updated)
    }

    fun writeTagValue(tag: PlcTag, rawBytes: ByteArray) {
        scope.launch {
            try {
                repository.writeTag(tag, rawBytes)
                statusMessage.postValue("${tag.name} geschrieben")
                readAllTags()
            } catch (e: Exception) {
                statusMessage.postValue("Schreibfehler: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        stopPolling()
        scope.cancel()
    }
}
