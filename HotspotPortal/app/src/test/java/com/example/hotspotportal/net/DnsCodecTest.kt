package com.example.hotspotportal.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test 1 (spec 13): DNS round-trip against real captured dig-style query
 * bytes.
 *
 * The AAAA case is the one that matters most: a blocked client must not be
 * able to prefer IPv6 and walk straight past the v4 chain.
 */
class DnsCodecTest {

    // Byte literals are signed: 192/168 must be written as their negative
    // two's-complement values or they do not fit a Byte.
    private val gateway = byteArrayOf(192.toByte(), 168.toByte(), 43, 1)

    /**
     * Real `dig` query for connectivitycheck.gstatic.com A, ID 0x1234.
     *
     * Label lengths must match the bytes that follow: "connectivitycheck"
     * is 17 (0x11), "gstatic" is 7 (0x07), "com" is 3.
     */
    private val aQuery = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        // "connectivitycheck" — 17 bytes
        0x11, 'c'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte(), 'n'.code.toByte(),
        'e'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte(), 'i'.code.toByte(),
        'v'.code.toByte(), 'i'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(),
        'c'.code.toByte(), 'h'.code.toByte(), 'e'.code.toByte(),
        'c'.code.toByte(), 'k'.code.toByte(),
        // "gstatic" — 7 bytes
        0x07, 'g'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
        // "com" — 3 bytes
        0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        0x00, 0x00, 0x01, 0x00, 0x01,
    )

    /** Same name, AAAA (type 28). */
    private val aaaaQuery = aQuery.copyOf().also { q ->
        q[HEADER_QTYPE_OFFSET] = 0x00
        q[HEADER_QTYPE_OFFSET + 1] = 0x1C
    }

    @Test
    fun `parses a real A query`() {
        val q = DnsCodec.parseQuery(aQuery)
        assertNotNull(q)
        assertEquals(0x1234, q!!.id)
        assertEquals("connectivitycheck.gstatic.com", q.name)
        assertEquals(DnsCodec.TYPE_A, q.type)
        assertEquals(DnsCodec.CLASS_IN, q.`class`)
    }

    @Test
    fun `answers A query with the gateway address`() {
        val q = DnsCodec.parseQuery(aQuery)!!
        val res = DnsCodec.buildResponse(q, aQuery, gateway)

        assertEquals(q.id, readU16(res, 0))
        // QR (0x8000) + RA (0x0080) + RD copied from the query (0x0100).
        assertEquals(0x8180, readU16(res, 2))
        assertEquals(1, readU16(res, 4)) // QDCOUNT
        assertEquals(1, readU16(res, 6)) // ANCOUNT
        // Question echoed verbatim.
        assertArrayEquals(aQuery.copyOfRange(12, aQuery.size), res.copyOfRange(12, q.questionEnd))
        // A record: type 1, class 1, TTL 1, rdlength 4, then the address.
        val answer = q.questionEnd
        assertEquals(1, readU16(res, answer + 2))
        assertEquals(1, readU16(res, answer + 4))
        assertEquals(1L, readU32(res, answer + 6))
        assertEquals(4, readU16(res, answer + 10))
        assertArrayEquals(gateway, res.copyOfRange(answer + 12, answer + 16))
    }

    /**
     * The blocking answer is a lie with a lifetime, and the lifetime is the bug.
     *
     * While a guest is blocked, its A query is answered with the hotspot's own
     * address so the OS captive-portal check fires. The client caches that. Once
     * it authenticates, the firewall stops intercepting, but the client keeps
     * sending traffic to the phone until the entry expires - which is exactly
     * the reported "signs in, then has no internet for a while".
     *
     * One second bounds that to about a second, and costs a blocked client
     * nothing because the answer never changes. Asserted explicitly so a
     * well-meaning "let's cache it properly for a minute" cannot come back.
     */
    @Test
    fun `the blocking answer expires almost immediately`() {
        val q = DnsCodec.parseQuery(aQuery)!!
        val res = DnsCodec.buildResponse(q, aQuery, gateway)

        val ttl = readU32(res, q.questionEnd + 6)
        assertTrue(
            "blocking A answers must not be cached by the client, was TTL=$ttl",
            ttl in 1..2,
        )
    }

    @Test
    fun `answers AAAA with NOERROR and zero records`() {
        val q = DnsCodec.parseQuery(aaaaQuery)!!
        assertEquals(DnsCodec.TYPE_AAAA, q.type)
        val res = DnsCodec.buildResponse(q, aaaaQuery, null)

        assertEquals(0, readU16(res, 6)) // ANCOUNT must be 0
        // RCODE NOERROR (0) — a SERVFAIL here would make clients give up on
        // the network entirely instead of showing the portal.
        assertEquals(0, readU16(res, 2) and 0x000F)
        assertEquals(q.questionEnd, res.size) // nothing after the question
    }

    @Test
    fun `rejects truncated and malformed packets`() {
        // Too short to hold a header.
        assertNull(DnsCodec.parseQuery(byteArrayOf(0x12, 0x34)))
        assertNull(DnsCodec.parseQuery(ByteArray(0)))
        // QDCOUNT 0: nothing to answer.
        assertNull(DnsCodec.parseQuery(ByteArray(12)))
        // QDCOUNT 1, but the QNAME never terminates.
        val unterminated = ByteArray(12).also {
            it[5] = 1 // QDCOUNT = 1
        } + byteArrayOf(5, 'a'.code.toByte())
        assertNull(DnsCodec.parseQuery(unterminated))
        // QDCOUNT 1, name terminates, but QTYPE+QCLASS are cut off.
        val noTail = ByteArray(12).also { it[5] = 1 } +
            byteArrayOf(1, 'a'.code.toByte(), 0x00)
        assertNull(DnsCodec.parseQuery(noTail))
    }

    @Test
    fun `rejects a response so it is never answered`() {
        val resp = byteArrayOf(0x12, 0x34.toByte(), 0x81.toByte(), 0x80.toByte()) +
            ByteArray(8)
        assertNull(DnsCodec.parseQuery(resp))
    }

    private fun readU16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun readU32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    companion object {
        // 12-byte header, then the name, then QTYPE. "connectivitycheck" (17)
        // + "gstatic" (7) + "com" (3) = 27 chars, 30 label bytes, +1 null.
        private const val HEADER_QTYPE_OFFSET = 12 + 30 + 1
    }
}
