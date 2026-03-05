package com.lumix.s7plc.viewmodel

import android.os.Handler
import android.os.Looper

/**
 * Minimaler LiveData-Ersatz ohne AndroidX-Abhängigkeit.
 */
class ObservableValue<T>(initial: T) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(T) -> Unit>()

    var value: T = initial
        set(v) {
            field = v
            listeners.toList().forEach { it(v) }
        }

    /** Fügt Listener hinzu und ruft ihn sofort mit dem aktuellen Wert auf. */
    fun observe(listener: (T) -> Unit) {
        listeners += listener
        listener(value)
    }

    fun removeObserver(listener: (T) -> Unit) {
        listeners -= listener
    }

    fun removeAllObservers() {
        listeners.clear()
    }

    /** Thread-sicheres Setzen des Wertes (posted auf den Main-Thread). */
    fun postValue(v: T) {
        mainHandler.post { value = v }
    }
}
