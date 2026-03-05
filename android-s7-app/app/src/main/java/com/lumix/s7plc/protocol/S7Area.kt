package com.lumix.s7plc.protocol

/**
 * S7 Speicherbereiche (Areas)
 */
enum class S7Area(val code: Byte, val label: String, val shortName: String) {
    INPUTS(0x81.toByte(),   "Eingänge",      "E"),
    OUTPUTS(0x82.toByte(),  "Ausgänge",      "A"),
    MERKER(0x83.toByte(),   "Merker",        "M"),
    DB(0x84.toByte(),       "Datenbaustein", "DB"),
    COUNTER(0x1C.toByte(),  "Zähler",        "Z"),
    TIMER(0x1D.toByte(),    "Timer",         "T");

    companion object {
        fun fromCode(code: Byte) = values().firstOrNull { it.code == code }
    }
}

/**
 * S7 Datentypen für Lese-/Schreiboperationen
 */
enum class S7WordLen(val code: Byte, val label: String, val sizeInBytes: Int) {
    BIT(0x01,   "Bit",   1),
    BYTE(0x02,  "Byte",  1),
    CHAR(0x03,  "Char",  1),
    WORD(0x04,  "Word",  2),
    INT(0x05,   "Int",   2),
    DWORD(0x06, "DWord", 4),
    DINT(0x07,  "DInt",  4),
    REAL(0x08,  "Real",  4);

    companion object {
        fun fromCode(code: Byte) = values().firstOrNull { it.code == code }
    }
}
