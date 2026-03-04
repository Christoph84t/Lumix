package com.lumix.s7plc.protocol

/**
 * Hilfsmethoden zum Erstellen und Lesen von S7-Protokollpaketen
 *
 * Protokollstack:
 *   TCP (Port 102)
 *   └── TPKT (RFC 1006)
 *       └── COTP (ISO 8073)
 *           └── S7 Communication
 */
internal object S7Packet {

    // -------------------------------------------------------------------------
    // TPKT (Transport Protocol data unit)
    // -------------------------------------------------------------------------

    /** Baut einen TPKT-Header (4 Bytes) */
    fun tpktHeader(payloadLen: Int): ByteArray {
        val total = payloadLen + 4
        return byteArrayOf(
            0x03,                         // version
            0x00,                         // reserved
            (total shr 8).toByte(),
            (total and 0xFF).toByte()
        )
    }

    /** Gibt die im TPKT-Header kodierte Gesamtlänge zurück */
    fun tpktLength(buf: ByteArray, offset: Int = 0): Int =
        ((buf[offset + 2].toInt() and 0xFF) shl 8) or (buf[offset + 3].toInt() and 0xFF)

    // -------------------------------------------------------------------------
    // COTP
    // -------------------------------------------------------------------------

    /**
     * COTP Connection Request (CR) Paket
     * Destination TSAP kodiert Rack und Slot:  HI = 1, LO = rack*32 + slot
     */
    fun cotpCR(rack: Int, slot: Int): ByteArray {
        val dstTsapHi: Byte = 0x01
        val dstTsapLo: Byte = ((rack * 32) + slot).toByte()
        return byteArrayOf(
            // COTP header (length = remaining bytes after this field)
            0x11,                   // length = 17
            0xE0.toByte(),          // PDU type: CR
            0x00, 0x00,             // DST-REF
            0x00, 0x01,             // SRC-REF
            0x00,                   // Class / options

            // Parameter: TPDU size = 1024 (0x0A)
            0xC0.toByte(), 0x01, 0x0A,

            // Parameter: SRC-TSAP (Quell-TSAP)
            0xC1.toByte(), 0x02, 0x01, 0x00,

            // Parameter: DST-TSAP (Ziel-TSAP, kodiert Rack & Slot)
            0xC2.toByte(), 0x02, dstTsapHi, dstTsapLo
        )
    }

    /** COTP DT (Data) Header */
    fun cotpDT(): ByteArray = byteArrayOf(0x02, 0xF0.toByte(), 0x80.toByte())

    // -------------------------------------------------------------------------
    // S7 Setup Communication (PDU-Verhandlung)
    // -------------------------------------------------------------------------

    fun s7SetupCommunication(maxPdu: Int = 480): ByteArray = byteArrayOf(
        0x32,                           // S7 Protokoll-ID
        0x01,                           // ROSCTR: JOB
        0x00, 0x00,                     // Redundanz ID
        0x00, 0x00,                     // PDU Referenz
        0x00, 0x08,                     // Parameter Länge
        0x00, 0x00,                     // Daten Länge
        // Parameter
        0xF0.toByte(),                  // Funktion: Setup Communication
        0x00,                           // reserviert
        0x00, 0x01,                     // Max AMQ Caller
        0x00, 0x01,                     // Max AMQ Callee
        (maxPdu shr 8).toByte(),
        (maxPdu and 0xFF).toByte()
    )

    // -------------------------------------------------------------------------
    // S7 Read Variable Request
    // -------------------------------------------------------------------------

    /**
     * Erstellt einen S7 Read-Request für einen einzelnen Operanden.
     *
     * @param area      S7-Bereich (DB, M, E, A, ...)
     * @param dbNumber  DB-Nummer (nur bei Area=DB relevant)
     * @param start     Startbyte-Adresse
     * @param count     Anzahl Elemente
     * @param wordLen   Datentyp (BYTE, WORD, DWORD, BIT, ...)
     */
    fun readVarRequest(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        count: Int,
        wordLen: S7WordLen
    ): ByteArray {
        // Bitadresse = Byteadresse * 8 (+ Bitnummer bei BIT-Zugriff)
        val bitAddr = start * 8

        val paramLen = 2 + 12  // header (2) + 1 item à 12 Bytes
        val s7 = ByteArray(10 + paramLen)

        s7[0] = 0x32            // S7 ID
        s7[1] = 0x01            // JOB
        s7[2] = 0x00; s7[3] = 0x00   // redundancy
        s7[4] = 0x00; s7[5] = 0x01   // PDU ref
        s7[6] = (paramLen shr 8).toByte()
        s7[7] = (paramLen and 0xFF).toByte()
        s7[8] = 0x00; s7[9] = 0x00   // data length = 0

        // Parameter
        s7[10] = 0x04           // Read Var function
        s7[11] = 0x01           // item count = 1

        // Item (12 Bytes, Any-Request)
        s7[12] = 0x12           // variable spec
        s7[13] = 0x0A           // remaining length
        s7[14] = 0x10           // syntax-id: S7ANY
        s7[15] = wordLen.code
        s7[16] = (count shr 8).toByte()
        s7[17] = (count and 0xFF).toByte()
        s7[18] = (dbNumber shr 8).toByte()
        s7[19] = (dbNumber and 0xFF).toByte()
        s7[20] = area.code
        // Bit address (3 bytes, big endian)
        s7[21] = ((bitAddr shr 16) and 0xFF).toByte()
        s7[22] = ((bitAddr shr 8) and 0xFF).toByte()
        s7[23] = (bitAddr and 0xFF).toByte()

        return s7
    }

    // -------------------------------------------------------------------------
    // S7 Write Variable Request
    // -------------------------------------------------------------------------

    /**
     * Erstellt einen S7 Write-Request.
     */
    fun writeVarRequest(
        area: S7Area,
        dbNumber: Int,
        start: Int,
        count: Int,
        wordLen: S7WordLen,
        data: ByteArray
    ): ByteArray {
        val bitAddr = start * 8
        val dataLen = data.size
        // Bei BYTE/CHAR/BIT: Transport size = 4 (BYTE), sonst WORD/DWORD ...
        val transportSize: Byte = when (wordLen) {
            S7WordLen.BIT   -> 0x03
            S7WordLen.BYTE,
            S7WordLen.CHAR  -> 0x04
            else            -> 0x04
        }
        // Datenlänge in Bits (für Response-Header)
        val dataLenBits = dataLen * 8

        val paramLen = 2 + 12
        // data section: 4 bytes header + data (+ optional padding for alignment)
        val dataSection = 4 + dataLen + (if (dataLen % 2 != 0) 1 else 0)

        val s7 = ByteArray(10 + paramLen + dataSection)

        s7[0] = 0x32
        s7[1] = 0x01
        s7[2] = 0x00; s7[3] = 0x00
        s7[4] = 0x00; s7[5] = 0x02   // different PDU ref
        s7[6] = (paramLen shr 8).toByte()
        s7[7] = (paramLen and 0xFF).toByte()
        s7[8] = (dataSection shr 8).toByte()
        s7[9] = (dataSection and 0xFF).toByte()

        // Parameter
        s7[10] = 0x05           // Write Var function
        s7[11] = 0x01           // item count

        // Item
        s7[12] = 0x12
        s7[13] = 0x0A
        s7[14] = 0x10
        s7[15] = wordLen.code
        s7[16] = (count shr 8).toByte()
        s7[17] = (count and 0xFF).toByte()
        s7[18] = (dbNumber shr 8).toByte()
        s7[19] = (dbNumber and 0xFF).toByte()
        s7[20] = area.code
        s7[21] = ((bitAddr shr 16) and 0xFF).toByte()
        s7[22] = ((bitAddr shr 8) and 0xFF).toByte()
        s7[23] = (bitAddr and 0xFF).toByte()

        // Data section
        val off = 10 + paramLen
        s7[off]     = 0x00              // return code (filled by PLC in response)
        s7[off + 1] = transportSize
        s7[off + 2] = (dataLenBits shr 8).toByte()
        s7[off + 3] = (dataLenBits and 0xFF).toByte()
        data.copyInto(s7, off + 4)

        return s7
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    /** Kombiniert TPKT + COTP-DT + S7-Payload zu einem TCP-Paket */
    fun wrap(s7Payload: ByteArray): ByteArray {
        val cotp = cotpDT()
        val tpkt = tpktHeader(cotp.size + s7Payload.size)
        return tpkt + cotp + s7Payload
    }
}
