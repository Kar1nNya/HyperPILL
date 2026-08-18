package com.karin.hyperpill.pods

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GaiaProtocolTest {

    @Test
    fun `application version command uses BASIC feature command 5`() {
        val pdu = GaiaProtocol.getApplicationVersionCommand()
        assertEquals(
            GaiaProtocol.commandValue(GaiaProtocol.FEATURE_BASIC, GaiaProtocol.TYPE_COMMAND, 5),
            GaiaProtocol.commandValueOf(pdu)
        )
        assertEquals(0, GaiaProtocol.payloadOf(pdu).size)
    }

    @Test
    fun `serial number command uses BASIC feature command 3`() {
        val pdu = GaiaProtocol.getSerialNumberCommand()
        assertEquals(
            GaiaProtocol.commandValue(GaiaProtocol.FEATURE_BASIC, GaiaProtocol.TYPE_COMMAND, 3),
            GaiaProtocol.commandValueOf(pdu)
        )
    }

    @Test
    fun `variant command uses BASIC feature command 4`() {
        val pdu = GaiaProtocol.getVariantNameCommand()
        assertEquals(
            GaiaProtocol.commandValue(GaiaProtocol.FEATURE_BASIC, GaiaProtocol.TYPE_COMMAND, 4),
            GaiaProtocol.commandValueOf(pdu)
        )
    }

    @Test
    fun `earbud sn commands use BASIC commands 20 and 21`() {
        val left = GaiaProtocol.getEarbudSnLeftCommand()
        val right = GaiaProtocol.getEarbudSnRightCommand()
        assertEquals(
            GaiaProtocol.commandValue(GaiaProtocol.FEATURE_BASIC, GaiaProtocol.TYPE_COMMAND, 20),
            GaiaProtocol.commandValueOf(left)
        )
        assertEquals(
            GaiaProtocol.commandValue(GaiaProtocol.FEATURE_BASIC, GaiaProtocol.TYPE_COMMAND, 21),
            GaiaProtocol.commandValueOf(right)
        )
    }

    @Test
    fun `parseText strips null terminator and trims whitespace`() {
        assertEquals("1.6.0", GaiaProtocol.parseText("1.6.0\u0000".toByteArray(Charsets.US_ASCII)))
        assertEquals("ABC123", GaiaProtocol.parseText("ABC123".toByteArray(Charsets.US_ASCII)))
        assertEquals("", GaiaProtocol.parseText(byteArrayOf()))
    }

    @Test
    fun `voice conf command payload is enabled volume index`() {
        val pdu = GaiaProtocol.setVoiceConfCommand(enabled = true, volume = 80, index = 3)
        assertArrayEquals(
            byteArrayOf(1, 80, 3),
            GaiaProtocol.payloadOf(pdu)
        )
    }
}
