package com.lumix.s7plc.model

import android.util.Log
import com.lumix.s7plc.protocol.S7Area
import com.lumix.s7plc.protocol.S7Client
import com.lumix.s7plc.protocol.S7Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlcRepository {

    private val client = S7Client()
    val isConnected get() = client.isConnected
    val negotiatedPduSize get() = client.negotiatedPduSize

    companion object {
        private const val TAG = "PlcRepository"
    }

    // -------------------------------------------------------------------------
    // Verbindung
    // -------------------------------------------------------------------------

    suspend fun connect(connection: PlcConnection): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.connect(connection.host, connection.rack, connection.slot)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        client.disconnect()
    }

    // -------------------------------------------------------------------------
    // Rohe Byte-Lese-/Schreiboperationen (für DB-Browser)
    // -------------------------------------------------------------------------

    suspend fun readDB(dbNumber: Int, startByte: Int, sizeBytes: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching { client.readDB(dbNumber, startByte, sizeBytes) }
        }

    suspend fun writeDB(dbNumber: Int, startByte: Int, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { client.writeDB(dbNumber, startByte, data) }
        }

    suspend fun readArea(
        area: S7Area,
        dbNumber: Int,
        startByte: Int,
        sizeBytes: Int
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching { client.readArea(area, dbNumber, startByte, sizeBytes) }
    }

    // -------------------------------------------------------------------------
    // Typisiertes Tag-Lesen
    // -------------------------------------------------------------------------

    suspend fun readTag(tag: PlcTag): Result<TagValue> = withContext(Dispatchers.IO) {
        runCatching {
            val buf = client.readArea(tag.area, tag.dbNumber, tag.byteOffset, tag.dataType.sizeBytes)
            when (tag.dataType) {
                PlcDataType.BOOL  -> TagValue.BoolValue(client.getBoolean(buf, 0, tag.bitOffset))
                PlcDataType.BYTE  -> TagValue.IntValue(buf[0].toLong() and 0xFF, tag.unit)
                PlcDataType.WORD  -> TagValue.IntValue(client.getWord(buf, 0).toLong(), tag.unit)
                PlcDataType.INT   -> TagValue.IntValue(client.getInt(buf, 0).toLong(), tag.unit)
                PlcDataType.DWORD -> TagValue.IntValue(client.getDWord(buf, 0), tag.unit)
                PlcDataType.DINT  -> TagValue.IntValue(
                    ByteArray(4).also { buf.copyInto(it) }.let {
                        ((it[0].toLong() and 0xFF) shl 24) or
                        ((it[1].toLong() and 0xFF) shl 16) or
                        ((it[2].toLong() and 0xFF) shl 8)  or
                         (it[3].toLong() and 0xFF)
                    }, tag.unit)
                PlcDataType.REAL  -> TagValue.RealValue(client.getReal(buf, 0), tag.unit)
            }
        }
    }

    suspend fun writeTag(tag: PlcTag, rawBytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { client.writeArea(tag.area, tag.dbNumber, tag.byteOffset, tag.dataType.wordLen, rawBytes) }
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
