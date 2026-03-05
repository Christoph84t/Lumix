package com.lumix.s7plc

import android.app.Application
import com.lumix.s7plc.viewmodel.ConnectionViewModel

/** Application-Klasse – hält den gemeinsamen ConnectionViewModel über Activities hinweg. */
class S7PlcApp : Application() {
    val connectionViewModel = ConnectionViewModel()
}
