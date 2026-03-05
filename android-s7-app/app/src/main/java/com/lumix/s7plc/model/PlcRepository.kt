package com.lumix.s7plc.model

import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.protocol.S7Client

class PlcRepository {

    private val client = S7Client()
    val isConnected get() = client.isConnected
    val negotiatedPduSize get() = client.negotiatedPduSize

    // -------------------------------------------------------------------------
    // Verbindung  (blockierend – Aufruf muss im IO-Dispatcher erfolgen)
    // -------------------------------------------------------------------------

    fun connect(connection: PlcConnection) {
        client.connect(connection.host, connection.rack, connection.slot)
    }

    fun disconnect() {
        client.disconnect()
    }

    // -------------------------------------------------------------------------
    // Rohe Byte-Lese-/Schreiboperationen (werfen Exception bei Fehler)
    // -------------------------------------------------------------------------

    fun readDB(dbNumber: Int, startByte: Int, sizeBytes: Int): ByteArray =
        client.readDB(dbNumber, startByte, sizeBytes)

    fun writeDB(dbNumber: Int, startByte: Int, data: ByteArray) =
        client.writeDB(dbNumber, startByte, data)

    fun readArea(area: S7Area, dbNumber: Int, startByte: Int, sizeBytes: Int): ByteArray =
        client.readArea(area, dbNumber, startByte, sizeBytes)

    // -------------------------------------------------------------------------
    // Typisiertes Tag-Lesen
    // -------------------------------------------------------------------------

    fun readTag(tag: PlcTag): TagValue {
        val buf = client.readArea(tag.area, tag.dbNumber, tag.byteOffset, tag.dataType.sizeBytes)
        return when (tag.dataType) {
            PlcDataType.BOOL  -> TagValue.BoolValue(client.getBoolean(buf, 0, tag.bitOffset))
            PlcDataType.BYTE  -> TagValue.IntValue(buf[0].toLong() and 0xFF, tag.unit)
            PlcDataType.WORD  -> TagValue.IntValue(client.getWord(buf, 0).toLong(), tag.unit)
            PlcDataType.INT   -> TagValue.IntValue(client.getInt(buf, 0).toLong(), tag.unit)
            PlcDataType.DWORD -> TagValue.IntValue(client.getDWord(buf, 0), tag.unit)
            PlcDataType.DINT  -> {
                val v = ((buf[0].toLong() and 0xFF) shl 24) or
                        ((buf[1].toLong() and 0xFF) shl 16) or
                        ((buf[2].toLong() and 0xFF) shl 8)  or
                         (buf[3].toLong() and 0xFF)
                TagValue.IntValue(v, tag.unit)
            }
            PlcDataType.REAL  -> TagValue.RealValue(client.getReal(buf, 0), tag.unit)
        }
    }

    fun writeTag(tag: PlcTag, rawBytes: ByteArray) {
        client.writeArea(tag.area, tag.dbNumber, tag.byteOffset, tag.dataType.wordLen, rawBytes)
    }

    // -------------------------------------------------------------------------
    // Hilfsmethode: Bytes als lesbare Hex-/Dezimal-Tabelle
    // -------------------------------------------------------------------------

    fun formatBytes(buf: ByteArray, startByte: Int = 0): List<ByteRow> {
        return buf.mapIndexed { i, b ->
            val addr = startByte + i
            val unsigned = b.toInt() and 0xFF
            ByteRow(
                address = addr,
                hex = "%02X".format(unsigned),
                decimal = unsigned.toString(),
                binary = "%08d".format(unsigned.toString(2).toInt())
            )
        }
    }
}

data class ByteRow(
    val address: Int,
    val hex: String,
    val decimal: String,
    val binary: String
)
