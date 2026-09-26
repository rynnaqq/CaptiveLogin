package com.example.hotspotportal.net

/**
 * Minimal DNS wire codec — parse and build, no dependencies.
 *
 * The portal only ever needs to answer one thing: "give me an A record".
 * Everything else gets NOERROR with zero answers, which stops clients from
 * preferring IPv6 while they are still blocked (see spec 5.4).
 *
 * Wire format reference (RFC 1035): a 12-byte header, then question/answer
 * records. Names are length-prefixed labels ending in a 0x00 byte.
 */
object DnsCodec {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val CLASS_IN = 1

    private const val HEADER_SIZE = 12
    private const val FLAG_QR_RESPONSE = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val DEFAULT_TTL_SECONDS = 10

    /** A parsed DNS question, or null if [bytes] is not a well-formed query. */
    data class Query(
        val id: Int,
        val name: String,
        val type: Int,
        val `class`: Int,
        /** Byte offset of the question section, so answers can echo it verbatim. */
        val questionOffset: Int,
        val questionEnd: Int,
    )

    /**
     * Parses a DNS query. Returns null for anything malformed — the socket
     * also receives stray UDP from the catch-all redirect, so bad input is
     * normal traffic, not an error condition.
     */
    fun parseQuery(bytes: ByteArray): Query? {
        if (bytes.size < HEADER_SIZE) return null

        val id = readU16(bytes, 0)
        val flags = readU16(bytes, 2)
        // Ignore anything that is not a standard query.
        if (flags and FLAG_QR_RESPONSE != 0) return null
        if (readU16(bytes, 4) < 1) return null // QDCOUNT must be >= 1

        var offset = HEADER_SIZE
        val labels = StringBuilder()
        while (true) {
            if (offset >= bytes.size) return null
            val len = bytes[offset].toInt() and 0xFF
            when {
                len == 0 -> {
                    offset++
                    break
                }
                // Compression pointers are legal in a question section but
                // never used by resolvers; treat them as malformed rather
                // than risk an infinite loop.
                (len and 0xC0) != 0 -> return null
                offset + 1 + len > bytes.size -> return null
            }
            for (i in 1..len) {
                val c = bytes[offset + i].toInt() and 0xFF
                labels.append(c.toChar())
            }
            labels.append('.')
            offset += 1 + len
        }

        if (offset + 4 > bytes.size) return null
        val qType = readU16(bytes, offset)
        val qClass = readU16(bytes, offset + 2)

        return Query(
            id = id,
            name = labels.toString().trimEnd('.'),
            type = qType,
            `class` = qClass,
            questionOffset = HEADER_SIZE,
            questionEnd = offset + 4,
        )
    }

    /**
     * Builds a response that echoes the original question and carries
     * [ipv4] as the single A record. Pass null [ipv4] for a NOERROR /
     * zero-answer reply (used for AAAA and everything else).
     */
    fun buildResponse(query: Query, original: ByteArray, ipv4: ByteArray?): ByteArray {
        val question = original.copyOfRange(query.questionOffset, query.questionEnd)
        val answerCount = if (ipv4 != null) 1 else 0

        val out = ByteArray(HEADER_SIZE + question.size + if (ipv4 != null) 16 else 0)

        writeU16(out, 0, query.id)
        // QR=1 (response), copy RD, set RA. Opcode stays 0 (QUERY).
        var flags = FLAG_QR_RESPONSE or FLAG_RA
        if (readU16(original, 2) and FLAG_RD != 0) flags = flags or FLAG_RD
        writeU16(out, 2, flags)
        writeU16(out, 4, 1) // QDCOUNT
        writeU16(out, 6, answerCount)
        writeU16(out, 8, 0) // NSCOUNT
        writeU16(out, 10, 0) // ARCOUNT

        var p = HEADER_SIZE
        question.copyInto(out, p)
        p += question.size

        if (ipv4 != null) {
            // Answer name is a pointer back to the question at offset 0x0C.
            out[p++] = 0xC0.toByte()
            out[p++] = 0x0C.toByte()
            writeU16(out, p, TYPE_A); p += 2
            writeU16(out, p, CLASS_IN); p += 2
            writeU32(out, p, DEFAULT_TTL_SECONDS); p += 4
            writeU16(out, p, 4); p += 2
            ipv4.copyInto(out, p)
        }
        return out
    }

    private fun readU16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun writeU16(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v shr 8) and 0xFF).toByte()
        b[o + 1] = (v and 0xFF).toByte()
    }

    private fun writeU32(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v shr 24) and 0xFF).toByte()
        b[o + 1] = ((v shr 16) and 0xFF).toByte()
        b[o + 2] = ((v shr 8) and 0xFF).toByte()
        b[o + 3] = (v and 0xFF).toByte()
    }
}
