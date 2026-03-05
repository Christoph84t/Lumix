package com.lumix.s7plc.protocol

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * S7Client – Kommunikation mit Siemens S7 SPSen über ISO-on-TCP (Port 102)
 *
 * Unterstützte SPS-Modelle:
 *  - S7-300, S7-400 (Rack 0, Slot 2)
 *  - S7-1200, S7-1500 (Rack 0, Slot 1)
 *  - LOGO! 0BA8 und neuer (Rack 0, Slot 1)
 *
 * Verwendung:
 *  val client = S7Client()
 *  client.connect("192.168.0.1", rack = 0, slot = 1)
 *  val data = client.readDB(1, 0, 4)   // DB1.DBD0 lesen (4 Bytes)
 *  client.disconnect()
 */
class S7Client {

    companion object {
        private const val TAG = "S7Client"
        private const val S7_PORT = 102
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val HEADER_SIZE = 4   // TPKT header
    }

    // Verbindungsstatus
    var isConnected: Boolean = false
        private set

    var lastError: String = ""
        private set

    // Verhandelte PDU-Größe
    var negotiatedPduSize: Int = 480
        private set

    // Socket & Streams
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // -------------------------------------------------------------------------
    // Verbindungsaufbau / -abbau
    // -------------------------------------------------------------------------

    /**
     * Verbindet mit der SPS.
     *
     * @param host  IP-Adresse oder Hostname der SPS
     * @param rack  Rack-Nummer (Standard: 0)
     * @param slot  Slot-Nummer (S7-300/400: 2; S7-1200/1500: 1)
     */
    @Throws(S7Exception::class)
    fun connect(host: String, rack: Int = 0, slot: Int = 1) {
        disconnect()
        try {
            Log.d(TAG, "Verbinde mit $host:$S7_PORT (Rack=$rack, Slot=$slot)")
            val s = Socket()
            s.connect(InetSocketAddress(host, S7_PORT), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS
            s.tcpNoDelay = true
            socket = s
            inputStream  = s.getInputStream()
            outputStream = s.getOutputStream()
        } catch (e: Exception) {
            throw S7Exception("TCP-Verbindung fehlgeschlagen: ${e.message}",
                S7Exception.ERR_CONNECTION_FAILED)
        }

        // 1. ISO/COTP Verbindung aufbauen
        isoConnect(rack, slot)

        // 2. S7-Kommunikation aushandeln (PDU-Größe)
        negotiatePdu()

        isConnected = true
        Log.d(TAG, "Verbunden. PDU-Größe: $negotiatedPduSize Bytes")
    }

    /** Trennt die Verbindung zur SPS. */
    fun disconnect() {
        isConnected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        Log.d(TAG, "Verbindung getrennt")
    }

    // -------------------------------------------------------------------------
    // Öffentliche Lese-API
    // -------------------------------------------------------------------------

    /**
     * Liest Bytes aus einem Datenbaustein (DB).
     *
     * @param dbNumber  DB-Nummer (z.B. 1 für DB1)
     * @param start     Startbyte (z.B. 0 für DBX0.0 / DBB0)
     * @param size      Anzahl Bytes
     */
    @Throws(S7Exception::class)
    fun readDB(dbNumber: Int, start: Int, size: Int): ByteArray =
        readArea(S7Area.DB, dbNumber, start, size, S7WordLen.BYTE)

    /**
     * Liest Bytes aus dem Merkerbereich.
     * @param start Startbyte-Adresse
     * @param size  Anzahl Bytes
     */
    @Throws(S7Exception::class)
    fun readMerker(start: Int, size: Int): ByteArray =
        readArea(S7Area.MERKER, 0, start, size, S7WordLen.BYTE)

    /**
     * Liest Bytes aus dem Eingangsbereich.
     */
    @Throws(S7Exception::class)
    fun readInputs(start: Int, size: Int): ByteArray =
        readArea(S7Area.INPUTS, 0, start, size, S7WordLen.BYTE)

    /**
     * Liest Bytes aus dem Ausgangsbereich.
     */
    @Throws(S7Exception::class)
    fun readOutputs(start: Int, size: Int): ByteArray =
        readArea(S7Area.OUTPUTS, 0, start, size, S7WordLen.BYTE)

    /**
     * Generische Lesefunktion.
     */
    @Throws(S7Exception::class)
    fun readArea(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        size: Int,
        wordLen: S7WordLen = S7WordLen.BYTE
    ): ByteArray {
        checkConnected()
        val result = ByteArray(size)
        var offset = 0
        var remaining = size

        // Aufteilung in mehrere Anfragen wenn Daten > PDU-Größe
        val maxChunk = negotiatedPduSize - 18
        while (remaining > 0) {
            val chunk = minOf(remaining, maxChunk)
            val raw = readAreaChunk(area, dbNumber, start + offset, chunk, wordLen)
            raw.copyInto(result, offset)
            offset    += chunk
            remaining -= chunk
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Öffentliche Schreib-API
    // -------------------------------------------------------------------------

    /**
     * Schreibt Bytes in einen Datenbaustein (DB).
     */
    @Throws(S7Exception::class)
    fun writeDB(dbNumber: Int, start: Int, data: ByteArray) =
        writeArea(S7Area.DB, dbNumber, start, S7WordLen.BYTE, data)

    /**
     * Schreibt Bytes in den Merkerbereich.
     */
    @Throws(S7Exception::class)
    fun writeMerker(start: Int, data: ByteArray) =
        writeArea(S7Area.MERKER, 0, start, S7WordLen.BYTE, data)

    /**
     * Schreibt Bytes in den Ausgangsbereich.
     */
    @Throws(S7Exception::class)
    fun writeOutputs(start: Int, data: ByteArray) =
        writeArea(S7Area.OUTPUTS, 0, start, S7WordLen.BYTE, data)

    /**
     * Generische Schreibfunktion.
     */
    @Throws(S7Exception::class)
    fun writeArea(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        wordLen: S7WordLen,
        data: ByteArray
    ) {
        checkConnected()
        val maxChunk = negotiatedPduSize - 35
        var offset = 0
        var remaining = data.size

        while (remaining > 0) {
            val chunk = minOf(remaining, maxChunk)
            writeAreaChunk(area, dbNumber, start + offset, chunk, wordLen,
                data.copyOfRange(offset, offset + chunk))
            offset    += chunk
            remaining -= chunk
        }
    }

    // -------------------------------------------------------------------------
    // Datentyp-Konvertierung
    // -------------------------------------------------------------------------

    fun getBoolean(buf: ByteArray, byteIndex: Int, bitIndex: Int): Boolean =
        (buf[byteIndex].toInt() and (1 shl bitIndex)) != 0

    fun setBoolean(buf: ByteArray, byteIndex: Int, bitIndex: Int, value: Boolean) {
        if (value)
            buf[byteIndex] = (buf[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        else
            buf[byteIndex] = (buf[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
    }

    fun getWord(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    fun setWord(buf: ByteArray, offset: Int, value: Int) {
        buf[offset]     = (value shr 8).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    fun getDWord(buf: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL

    fun setDWord(buf: ByteArray, offset: Int, value: Long) {
        ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt())
    }

    fun getReal(buf: ByteArray, offset: Int): Float =
        ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.BIG_ENDIAN).float

    fun setReal(buf: ByteArray, offset: Int, value: Float) {
        ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.BIG_ENDIAN).putFloat(value)
    }

    fun getInt(buf: ByteArray, offset: Int): Short =
        ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.BIG_ENDIAN).short

    fun setInt(buf: ByteArray, offset: Int, value: Short) {
        ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.BIG_ENDIAN).putShort(value)
    }

    // -------------------------------------------------------------------------
    // Interner Verbindungsaufbau
    // -------------------------------------------------------------------------

    private fun isoConnect(rack: Int, slot: Int) {
        val cotpCR = S7Packet.cotpCR(rack, slot)
        val tpkt = S7Packet.tpktHeader(cotpCR.size)
        send(tpkt + cotpCR)

        val response = receive()
        // Erwarte COTP CC (Connection Confirm = 0xD0)
        if (response.size < 5 || response[4] != 0xD0.toByte()) {
            throw S7Exception("ISO/COTP Verbindung abgelehnt", S7Exception.ERR_ISO_CONNECT)
        }
        Log.d(TAG, "COTP verbunden")
    }

    private fun negotiatePdu() {
        val s7 = S7Packet.s7SetupCommunication()
        send(S7Packet.wrap(s7))

        val response = receive()
        // Antwort: TPKT(4) + COTP-DT(3) + S7-Header(10+) => PDU-Größe in letzten 2 Bytes
        if (response.size >= 25) {
            negotiatedPduSize = getWord(response, response.size - 2)
            Log.d(TAG, "PDU-Größe verhandelt: $negotiatedPduSize")
        } else {
            throw S7Exception("PDU-Verhandlung fehlgeschlagen", S7Exception.ERR_PDU_NEGOTIATION)
        }
    }

    // -------------------------------------------------------------------------
    // Interne Lese-/Schreiboperationen
    // -------------------------------------------------------------------------

    private fun readAreaChunk(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        size: Int,
        wordLen: S7WordLen
    ): ByteArray {
        val request = S7Packet.readVarRequest(area, dbNumber, start, size, wordLen)
        send(S7Packet.wrap(request))

        val response = receive()
        return parseReadResponse(response, size)
    }

    private fun parseReadResponse(response: ByteArray, expectedSize: Int): ByteArray {
        // Mindestlänge: TPKT(4) + COTP(3) + S7-Header(12) + Item-Header(4)
        if (response.size < 23) {
            throw S7Exception("Antwort zu kurz: ${response.size} Bytes", S7Exception.ERR_INVALID_RESPONSE)
        }

        // S7-Header beginnt bei Offset 7 (nach TPKT + COTP)
        val s7Off = 7
        val errorClass = response[s7Off + 10].toInt() and 0xFF
        val errorCode  = response[s7Off + 11].toInt() and 0xFF
        if (errorClass != 0 || errorCode != 0) {
            throw S7Exception(
                "S7 Fehler: Klasse=0x${errorClass.toString(16)} Code=0x${errorCode.toString(16)}",
                S7Exception.ERR_READ_FAILED
            )
        }

        // Daten beginnen nach S7-Header (12 Bytes) + Daten-Antwort-Header (4 Bytes)
        val dataOff = s7Off + 12 + 4
        if (response.size < dataOff + expectedSize) {
            // Evtl. weniger Daten – trotzdem zurückgeben
            val available = response.size - dataOff
            if (available <= 0) throw S7Exception("Keine Daten in Antwort", S7Exception.ERR_READ_FAILED)
            return response.copyOfRange(dataOff, dataOff + available)
        }
        return response.copyOfRange(dataOff, dataOff + expectedSize)
    }

    private fun writeAreaChunk(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        size: Int,
        wordLen: S7WordLen,
        data: ByteArray
    ) {
        val request = S7Packet.writeVarRequest(area, dbNumber, start, size, wordLen, data)
        send(S7Packet.wrap(request))

        val response = receive()
        // Minimale Prüfung der Schreib-Antwort
        if (response.size < 12) {
            throw S7Exception("Ungültige Schreib-Antwort", S7Exception.ERR_WRITE_FAILED)
        }
        val s7Off = 7
        val errorClass = response.getOrNull(s7Off + 10)?.toInt()?.and(0xFF) ?: 0xFF
        val errorCode  = response.getOrNull(s7Off + 11)?.toInt()?.and(0xFF) ?: 0xFF
        if (errorClass != 0 || errorCode != 0) {
            throw S7Exception(
                "Schreibfehler: Klasse=0x${errorClass.toString(16)} Code=0x${errorCode.toString(16)}",
                S7Exception.ERR_WRITE_FAILED
            )
        }
    }

    // -------------------------------------------------------------------------
    // TCP Sende-/Empfangsmethoden
    // -------------------------------------------------------------------------

    private fun send(data: ByteArray) {
        outputStream?.write(data)
            ?: throw S7Exception("Nicht verbunden", S7Exception.ERR_NOT_CONNECTED)
        Log.v(TAG, "Gesendet: ${data.size} Bytes")
    }

    private fun receive(): ByteArray {
        val ins = inputStream
            ?: throw S7Exception("Nicht verbunden", S7Exception.ERR_NOT_CONNECTED)

        // TPKT-Header lesen (4 Bytes) – readFully statt readNBytes (API 23-kompatibel)
        val header = ByteArray(HEADER_SIZE)
        val hRead = readFully(ins, header, 0, HEADER_SIZE)
        if (hRead < HEADER_SIZE) {
            throw S7Exception("TPKT-Header unvollständig ($hRead Bytes)", S7Exception.ERR_INVALID_RESPONSE)
        }

        val totalLen = S7Packet.tpktLength(header)
        if (totalLen < HEADER_SIZE || totalLen > 65535) {
            throw S7Exception("Ungültige TPKT-Länge: $totalLen", S7Exception.ERR_INVALID_RESPONSE)
        }

        val payload = ByteArray(totalLen - HEADER_SIZE)
        val pRead = readFully(ins, payload, 0, payload.size)
        if (pRead < payload.size) {
            throw S7Exception("Verbindung unerwartet getrennt", S7Exception.ERR_CONNECTION_FAILED)
        }

        Log.v(TAG, "Empfangen: $totalLen Bytes gesamt")
        return header + payload
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    /** Liest genau [len] Bytes – API-23-kompatibel, ersetzt InputStream.readNBytes() */
    private fun readFully(ins: InputStream, buf: ByteArray, off: Int, len: Int): Int {
        var read = 0
        while (read < len) {
            val n = ins.read(buf, off + read, len - read)
            if (n < 0) return read
            read += n
        }
        return read
    }

    private fun checkConnected() {
        if (!isConnected || socket?.isConnected != true) {
            throw S7Exception("Nicht mit SPS verbunden", S7Exception.ERR_NOT_CONNECTED)
        }
    }
}
