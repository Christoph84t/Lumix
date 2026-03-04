package com.lumix.s7plc.model

import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.protocol.S7WordLen

data class PlcConnection(
    val host: String,
    val rack: Int = 0,
    val slot: Int = 1,
    val alias: String = ""
) {
    val displayName: String get() = if (alias.isNotBlank()) alias else host

    companion object {
        // Standard-Konfigurationen für verschiedene SPS-Typen
        val S7_300 = PlcConnection("192.168.0.1", rack = 0, slot = 2, alias = "S7-300")
        val S7_1200 = PlcConnection("192.168.0.1", rack = 0, slot = 1, alias = "S7-1200/1500")
        val LOGO = PlcConnection("192.168.0.1", rack = 0, slot = 1, alias = "LOGO! 0BA8")
    }
}

/**
 * Ein konfigurierbarer SPS-Datenpunkt (Tag)
 */
data class PlcTag(
    val id: String,
    val name: String,
    val area: S7Area,
    val dbNumber: Int = 0,
    val byteOffset: Int = 0,
    val bitOffset: Int = 0,
    val dataType: PlcDataType,
    val description: String = "",
    val unit: String = ""
)

enum class PlcDataType(val label: String, val sizeBytes: Int, val wordLen: S7WordLen) {
    BOOL("Bool",  1, S7WordLen.BYTE),
    BYTE("Byte",  1, S7WordLen.BYTE),
    WORD("Word",  2, S7WordLen.WORD),
    INT("Int",    2, S7WordLen.INT),
    DWORD("DWord",4, S7WordLen.DWORD),
    DINT("DInt",  4, S7WordLen.DINT),
    REAL("Real",  4, S7WordLen.REAL);
}

/**
 * Aktueller Zustand eines Tags (inkl. gelesenen Wert)
 */
sealed class TagValue {
    data class BoolValue(val value: Boolean) : TagValue() {
        override fun formatted() = if (value) "TRUE" else "FALSE"
    }
    data class IntValue(val value: Long, val unit: String = "") : TagValue() {
        override fun formatted() = "$value${if (unit.isNotBlank()) " $unit" else ""}"
    }
    data class RealValue(val value: Float, val unit: String = "") : TagValue() {
        override fun formatted() = "%.3f${if (unit.isNotBlank()) " $unit" else ""}".format(value)
    }
    data class ErrorValue(val message: String) : TagValue() {
        override fun formatted() = "Fehler: $message"
    }

    abstract fun formatted(): String
}
