package com.karin.hyperpill.pods

import java.io.EOFException
import java.io.InputStream

/**
 * Protocol facts extracted from Moondrop Link 2.24.2c (com.moondroplab.moondrop.moondrop_app).
 *
 * Pill uses Qualcomm GAIA v3 over RFCOMM/SPP:
 * - vendor ID 0x001D
 * - SPP UUID 00001101-0000-1000-8000-00805F9B34FB
 * - RFCOMM frame: FF <version=01> <flags> <length[1|2]> <GAIA PDU> [checksum]
 * - GAIA PDU: <vendor:u16> <command:u16> <payload>
 * - command bits: feature[6:0] << 9 | type[1:0] << 7 | command[6:0]
 */
object GaiaProtocol {

    const val VENDOR_ID = 0x001D
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // GAIA v3 packet types
    const val TYPE_COMMAND = 0
    const val TYPE_NOTIFICATION = 1
    const val TYPE_RESPONSE = 2
    const val TYPE_ERROR = 3

    // QTIL feature IDs (from V3BatteryPlugin / QTILFeature)
    const val FEATURE_BASIC = 0
    const val FEATURE_MUSIC_PROCESSING = 5
    const val FEATURE_BATTERY = 13
    const val FEATURE_VOICE = 14
    const val FEATURE_DAC_GAIN = 15
    const val FEATURE_ONEBRINGTWO = 20
    const val FEATURE_BT_ADDRESS = 21

    // FeaturesFetcher (BASIC feature) command IDs
    const val CMD_GET_SUPPORTED_FEATURES = 1
    const val CMD_GET_SUPPORTED_FEATURES_NEXT = 2
    const val CMD_GET_SERIAL_NUMBER = 3
    const val CMD_GET_VARIANT = 4
    const val CMD_GET_APPLICATION_VERSION = 5
    const val CMD_GET_EARBUD_SN_LEFT = 20
    const val CMD_GET_EARBUD_SN_RIGHT = 21

    // Battery plugin command IDs
    const val CMD_GET_SUPPORTED_BATTERIES = 0
    const val CMD_GET_BATTERY_LEVELS = 1

    // VOICE plugin command IDs (V2 voice conf: [enabled, volume, index])
    const val CMD_GET_VOICE_CONF = 1
    const val CMD_SET_VOICE_CONF = 2

    // DAC_GAIN plugin command IDs
    const val CMD_GET_GAIN = 1
    const val CMD_SET_GAIN = 2

    // MUSIC_PROCESSING plugin command IDs
    const val CMD_GET_EQ_STATE = 0
    const val CMD_GET_AVAILABLE_EQ_PRE_SETS = 1
    const val CMD_GET_EQ_SET = 2
    const val CMD_SET_EQ_SET = 3

    // ONEBRINGTWO plugin command IDs
    const val CMD_GET_ONEBRINGTWO_STATE = 1
    const val CMD_SET_ONEBRINGTWO_STATE = 2
    const val CMD_GET_ONEBRINGTWO_TIMEOUT = 3
    const val CMD_SET_ONEBRINGTWO_TIMEOUT = 4
    const val CMD_GET_CURRENT_DEVICES = 5
    const val CMD_GET_CURRENT_DEVICES_NEXT = 6
    const val CMD_DISCONNECT_DEVICE = 7

    // BT_ADDRESS plugin command IDs
    const val CMD_GET_BT_ADDRESS = 1
    const val CMD_SET_BT_ADDRESS = 2

    // Battery enum values
    const val BATTERY_SINGLE_DEVICE = 0
    const val BATTERY_LEFT = 1
    const val BATTERY_RIGHT = 2
    const val BATTERY_CHARGER_CASE = 3

    fun commandValue(feature: Int, type: Int, command: Int): Int =
        (feature shl 9) or (type shl 7) or command

    fun batteryLevelsCommand(batteries: IntArray = intArrayOf(
        BATTERY_SINGLE_DEVICE,
        BATTERY_LEFT,
        BATTERY_RIGHT,
        BATTERY_CHARGER_CASE
    )): ByteArray {
        val payload = ByteArray(batteries.size) { batteries[it].toByte() }
        return buildGaiaPdu(commandValue(FEATURE_BATTERY, TYPE_COMMAND, CMD_GET_BATTERY_LEVELS), payload)
    }

    fun supportedBatteriesCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BATTERY, TYPE_COMMAND, CMD_GET_SUPPORTED_BATTERIES), ByteArray(0))

    fun supportedFeaturesCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_SUPPORTED_FEATURES), ByteArray(0))

    fun getVoiceConfCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_VOICE, TYPE_COMMAND, CMD_GET_VOICE_CONF), ByteArray(0))

    fun setVoiceConfCommand(enabled: Boolean, volume: Int, index: Int): ByteArray =
        buildGaiaPdu(
            commandValue(FEATURE_VOICE, TYPE_COMMAND, CMD_SET_VOICE_CONF),
            byteArrayOf(
                if (enabled) 1 else 0,
                volume.toByte(),
                index.toByte()
            )
        )

    fun supportedFeaturesNextCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_SUPPORTED_FEATURES_NEXT), ByteArray(0))

    fun getSerialNumberCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_SERIAL_NUMBER), ByteArray(0))

    fun getVariantNameCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_VARIANT), ByteArray(0))

    fun getApplicationVersionCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_APPLICATION_VERSION), ByteArray(0))

    fun getEarbudSnLeftCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_EARBUD_SN_LEFT), ByteArray(0))

    fun getEarbudSnRightCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BASIC, TYPE_COMMAND, CMD_GET_EARBUD_SN_RIGHT), ByteArray(0))

    fun getGainCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_DAC_GAIN, TYPE_COMMAND, CMD_GET_GAIN), ByteArray(0))

    fun setGainCommand(index: Int): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_DAC_GAIN, TYPE_COMMAND, CMD_SET_GAIN), byteArrayOf(index.toByte()))

    fun getEqStateCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_MUSIC_PROCESSING, TYPE_COMMAND, CMD_GET_EQ_STATE), ByteArray(0))

    fun getAvailablePreSetsCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_MUSIC_PROCESSING, TYPE_COMMAND, CMD_GET_AVAILABLE_EQ_PRE_SETS), ByteArray(0))

    fun getEqSetCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_MUSIC_PROCESSING, TYPE_COMMAND, CMD_GET_EQ_SET), ByteArray(0))

    fun setEqSetCommand(presetId: Int): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_MUSIC_PROCESSING, TYPE_COMMAND, CMD_SET_EQ_SET), byteArrayOf(presetId.toByte()))

    fun getOneBringTwoStateCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_ONEBRINGTWO, TYPE_COMMAND, CMD_GET_ONEBRINGTWO_STATE), ByteArray(0))

    fun setOneBringTwoStateCommand(enabled: Boolean): ByteArray =
        buildGaiaPdu(
            commandValue(FEATURE_ONEBRINGTWO, TYPE_COMMAND, CMD_SET_ONEBRINGTWO_STATE),
            byteArrayOf(if (enabled) 1 else 0)
        )

    fun getOneBringTwoTimeoutCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_ONEBRINGTWO, TYPE_COMMAND, CMD_GET_ONEBRINGTWO_TIMEOUT), ByteArray(0))

    fun setOneBringTwoTimeoutCommand(timeout: Int): ByteArray =
        buildGaiaPdu(
            commandValue(FEATURE_ONEBRINGTWO, TYPE_COMMAND, CMD_SET_ONEBRINGTWO_TIMEOUT),
            byteArrayOf(timeout.toByte())
        )

    fun getBtAddressCommand(): ByteArray =
        buildGaiaPdu(commandValue(FEATURE_BT_ADDRESS, TYPE_COMMAND, CMD_GET_BT_ADDRESS), ByteArray(0))

    fun buildGaiaPdu(commandValue: Int, payload: ByteArray): ByteArray {
        val pdu = ByteArray(4 + payload.size)
        writeUInt16(pdu, 0, VENDOR_ID)
        writeUInt16(pdu, 2, commandValue)
        payload.copyInto(pdu, 4)
        return pdu
    }

    /**
     * Wrap a raw GAIA PDU into the RFCOMM frame used by the Moondrop app.
     * Checksum is disabled in the app's RFCOMM formatter; length extension is used
     * only when payload > 255 bytes (never for battery commands).
     */
    fun wrapRfcomm(pdu: ByteArray): ByteArray {
        val payloadLength = pdu.size - 4
        require(payloadLength in 0..255) { "RFCOMM single-frame payload too large: $payloadLength" }
        val frame = ByteArray(4 + pdu.size)
        frame[0] = 0xFF.toByte()
        frame[1] = 0x01
        frame[2] = 0x00 // flags: no checksum, no length extension
        frame[3] = payloadLength.toByte()
        pdu.copyInto(frame, 4)
        return frame
    }

    /**
     * Read one RFCOMM frame from [input] and return the inner GAIA PDU.
     */
    fun readFrame(input: InputStream): ByteArray {
        val sof = input.read()
        if (sof < 0) throw EOFException("Bluetooth stream closed")
        if (sof != 0xFF) throw IllegalStateException("Bad RFCOMM SOF: 0x${sof.toString(16)}")

        val version = input.read()
        if (version < 0) throw EOFException("Unexpected EOF while reading RFCOMM version")
        val flags = input.read()
        if (flags < 0) throw EOFException("Unexpected EOF while reading RFCOMM flags")

        val hasLengthExtension = flags and 0x02 != 0
        val hasChecksum = flags and 0x01 != 0

        val payloadLength = if (hasLengthExtension) {
            val hi = input.read()
            val lo = input.read()
            if (hi < 0 || lo < 0) throw EOFException("Unexpected EOF while reading extended length")
            (hi shl 8) or lo
        } else {
            val len = input.read()
            if (len < 0) throw EOFException("Unexpected EOF while reading length")
            len
        }

        val pdu = ByteArray(payloadLength + 4)
        var read = 0
        while (read < pdu.size) {
            val n = input.read(pdu, read, pdu.size - read)
            if (n < 0) throw EOFException("Unexpected EOF while reading PDU")
            read += n
        }

        if (hasChecksum) {
            val checksum = input.read()
            if (checksum < 0) throw EOFException("Unexpected EOF while reading checksum")
            // The app's RFCOMM formatter sends without checksum; if a device sends one,
            // we currently accept it without verification to keep the first milestone simple.
        }

        return pdu
    }

    /** Parse a battery-levels response payload: repeated [batteryId, level] pairs. */
    fun parseBatteryLevels(payload: ByteArray): Map<Int, Int> {
        val result = LinkedHashMap<Int, Int>()
        var i = 0
        while (i + 1 < payload.size) {
            val battery = payload[i].toInt() and 0xFF
            val level = payload[i + 1].toInt() and 0xFF
            result[battery] = level
            i += 2
        }
        return result
    }

    /** Parse a supported-batteries response payload: list of battery IDs. */
    fun parseSupportedBatteries(payload: ByteArray): List<Int> =
        payload.map { it.toInt() and 0xFF }

    /**
     * Parse a supported-features response payload.
     * byte0 = hasMoreData (1 = more pages), then [featureId, version] pairs.
     */
    fun parseSupportedFeatures(payload: ByteArray): SupportedFeaturesPage {
        val hasMore = payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == 1
        val features = ArrayList<GaiaFeature>()
        var i = 1
        while (i + 1 < payload.size) {
            val id = payload[i].toInt() and 0xFF
            val version = payload[i + 1].toInt() and 0xFF
            features.add(GaiaFeature(id, version))
            i += 2
        }
        return SupportedFeaturesPage(features, hasMore)
    }

    /** Parse a V2 voice-conf response: [enabled, volume, index]. */
    fun parseVoiceConf(payload: ByteArray): VoiceConf? {
        if (payload.size < 3) return null
        return VoiceConf(
            enabled = (payload[0].toInt() and 0xFF) == 1,
            volume = payload[1].toInt() and 0xFF,
            index = payload[2].toInt() and 0xFF
        )
    }

    /** Parse GAIA text responses (serial number / variant / app version / earbud SN). */
    fun parseText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val end = payload.indexOfFirst { it.toInt() == 0 }.let { if (it == -1) payload.size else it }
        return String(payload, 0, end, Charsets.US_ASCII).trim()
    }

    fun featureName(id: Int): String = when (id) {
        0 -> "BASIC"
        1 -> "EARBUD"
        2 -> "ANC"
        3 -> "VOICE_UI"
        4 -> "DEBUG"
        5 -> "MUSIC_PROCESSING"
        6 -> "UPGRADE"
        7 -> "HANDSET_SERVICE"
        8 -> "AUDIO_CURATION"
        9 -> "EARBUD_FIT"
        10 -> "VOICE_PROCESSING"
        11 -> "GESTURE_CONFIGURATION"
        12 -> "STATISTICS"
        13 -> "BATTERY"
        14 -> "VOICE"
        15 -> "DAC_GAIN"
        16 -> "CODEC_TYPE"
        17 -> "LIGHT_SENSOR"
        18 -> "SPATIAL_AUDIO"
        19 -> "LED"
        20 -> "ONEBRINGTWO"
        21 -> "BT_ADDRESS"
        22 -> "TOUCHV2"
        23 -> "AUDIO_RESOURCE"
        24 -> "POWER_CONTROL"
        25 -> "POWER_TIMEOUT"
        26 -> "TOUCHV3"
        27 -> "DYBASS"
        28 -> "AUDIO_FILE_STORAGE"
        else -> "UNKNOWN($id)"
    }

    /**
     * Best-effort display names for Qualcomm EQ presets.
     * 0 = flat/off, 63 = user, 1..9 are the common named presets.
     * If the actual Moondrop mapping differs, this can be corrected after on-device verification.
     */
    fun eqPresetName(id: Int): String = when (id) {
        0 -> "FLAT"
        1 -> "POP"
        2 -> "ROCK"
        3 -> "R&B"
        4 -> "CLASSICAL"
        5 -> "AMBIENT"
        6 -> "FUNK"
        7 -> "METAL"
        8 -> "SPEECH"
        9 -> "TECHNO"
        63 -> "USER"
        else -> "PRESET $id"
    }

    fun parseGainIndex(payload: ByteArray): Int =
        if (payload.isEmpty()) 0 else payload[0].toInt() and 0xFF

    fun parseEqState(payload: ByteArray): Int =
        if (payload.isEmpty()) 0 else payload[0].toInt() and 0xFF

    fun parseAvailablePreSets(payload: ByteArray): List<Int> {
        if (payload.isEmpty()) return emptyList()
        val count = payload[0].toInt() and 0xFF
        return (1 until payload.size.coerceAtMost(count + 1)).map { payload[it].toInt() and 0xFF }
    }

    fun parseEqSet(payload: ByteArray): Int =
        if (payload.isEmpty()) 0 else payload[0].toInt() and 0xFF

    fun parseOneBringTwoState(payload: ByteArray): Boolean =
        payload.isNotEmpty() && (payload[0].toInt() and 0xFF) == 1

    fun parseOneBringTwoTimeout(payload: ByteArray): Int =
        if (payload.isEmpty()) 0 else payload[0].toInt() and 0xFF

    fun formatBtAddress(payload: ByteArray): String =
        payload.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    fun commandValueOf(pdu: ByteArray): Int {
        require(pdu.size >= 4)
        return ((pdu[2].toInt() and 0xFF) shl 8) or (pdu[3].toInt() and 0xFF)
    }

    fun payloadOf(pdu: ByteArray): ByteArray {
        require(pdu.size >= 4)
        return pdu.copyOfRange(4, pdu.size)
    }

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}

data class VoiceConf(
    val enabled: Boolean,
    val volume: Int,
    val index: Int
)

data class GaiaFeature(
    val id: Int,
    val version: Int
)

data class SupportedFeaturesPage(
    val features: List<GaiaFeature>,
    val hasMoreData: Boolean
)
