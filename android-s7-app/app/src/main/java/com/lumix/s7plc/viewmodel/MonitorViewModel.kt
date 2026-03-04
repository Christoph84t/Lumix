package com.lumix.s7plc.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumix.s7plc.model.PlcRepository
import com.lumix.s7plc.model.PlcTag
import com.lumix.s7plc.model.TagValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TagState(
    val tag: PlcTag,
    val value: TagValue = TagValue.ErrorValue("Noch nicht gelesen"),
    val isLoading: Boolean = false
)

class MonitorViewModel(private val repository: PlcRepository) : ViewModel() {

    private val _tags = MutableLiveData<List<TagState>>(emptyList())
    val tags: LiveData<List<TagState>> = _tags

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    var pollingIntervalMs: Long = 1000L
    private var pollingJob: Job? = null

    // -------------------------------------------------------------------------
    // Tag-Verwaltung
    // -------------------------------------------------------------------------

    fun addTag(tag: PlcTag) {
        val current = _tags.value.orEmpty().toMutableList()
        if (current.none { it.tag.id == tag.id }) {
            current.add(TagState(tag))
            _tags.value = current
        }
    }

    fun removeTag(tagId: String) {
        _tags.value = _tags.value.orEmpty().filter { it.tag.id != tagId }
    }

    fun clearTags() {
        _tags.value = emptyList()
    }

    // -------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                readAllTags()
                delay(pollingIntervalMs)
            }
        }
        _statusMessage.value = "Monitoring gestartet (Intervall: ${pollingIntervalMs}ms)"
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _statusMessage.value = "Monitoring gestoppt"
    }

    fun readOnce() {
        viewModelScope.launch { readAllTags() }
    }

    private suspend fun readAllTags() {
        val current = _tags.value.orEmpty()
        if (current.isEmpty()) return

        val updated = current.map { state ->
            val result = repository.readTag(state.tag)
            state.copy(
                value = result.getOrElse { TagValue.ErrorValue(it.message ?: "Lesefehler") },
                isLoading = false
            )
        }
        _tags.postValue(updated)
    }

    // -------------------------------------------------------------------------
    // Schreiben
    // -------------------------------------------------------------------------

    fun writeTagValue(tag: PlcTag, rawBytes: ByteArray) {
        viewModelScope.launch {
            repository.writeTag(tag, rawBytes).fold(
                onSuccess = {
                    _statusMessage.postValue("${tag.name} geschrieben")
                    readOnce()
                },
                onFailure = { e ->
                    _statusMessage.postValue("Schreibfehler: ${e.message}")
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
