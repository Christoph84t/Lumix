package com.lumix.s7plc.protocol

class S7Exception(message: String, val errorCode: Int = -1) : Exception(message) {
    companion object {
        const val ERR_CONNECTION_FAILED  = 0x0001
        const val ERR_CONNECTION_TIMEOUT = 0x0002
        const val ERR_ISO_CONNECT        = 0x0003
        const val ERR_PDU_NEGOTIATION    = 0x0004
        const val ERR_NOT_CONNECTED      = 0x0005
        const val ERR_READ_FAILED        = 0x0006
        const val ERR_WRITE_FAILED       = 0x0007
        const val ERR_INVALID_RESPONSE   = 0x0008
        const val ERR_DATA_SIZE_MISMATCH = 0x0009
        const val ERR_ITEM_NOT_FOUND     = 0x000A
        const val ERR_BUFFER_TOO_SMALL   = 0x000B

        fun fromCode(code: Int): String = when (code) {
            ERR_CONNECTION_FAILED  -> "Verbindung fehlgeschlagen"
            ERR_CONNECTION_TIMEOUT -> "Verbindungs-Timeout"
            ERR_ISO_CONNECT        -> "ISO/COTP Verbindungsfehler"
            ERR_PDU_NEGOTIATION    -> "PDU-Verhandlung fehlgeschlagen"
            ERR_NOT_CONNECTED      -> "Nicht verbunden"
            ERR_READ_FAILED        -> "Lesen fehlgeschlagen"
            ERR_WRITE_FAILED       -> "Schreiben fehlgeschlagen"
            ERR_INVALID_RESPONSE   -> "Ungültige Antwort"
            ERR_DATA_SIZE_MISMATCH -> "Datengröße stimmt nicht überein"
            ERR_ITEM_NOT_FOUND     -> "Variable nicht gefunden"
            ERR_BUFFER_TOO_SMALL   -> "Puffer zu klein"
            else                   -> "Unbekannter Fehler (0x${code.toString(16)})"
        }
    }
}
