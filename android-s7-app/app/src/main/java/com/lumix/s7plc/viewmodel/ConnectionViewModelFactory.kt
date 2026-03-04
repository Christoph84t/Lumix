package com.lumix.s7plc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ConnectionViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectionViewModel::class.java))
            return ConnectionViewModel() as T
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}

class MonitorViewModelFactory(
    private val viewModel: ConnectionViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MonitorViewModel::class.java))
            return MonitorViewModel(viewModel.repository) as T
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}

class DataBlockViewModelFactory(
    private val viewModel: ConnectionViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataBlockViewModel::class.java))
            return DataBlockViewModel(viewModel.repository) as T
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
