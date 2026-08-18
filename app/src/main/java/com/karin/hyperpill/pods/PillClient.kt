package com.karin.hyperpill.pods

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal RFCOMM + GAIA v3 client for Moondrop Pill.
 *
 * The connection is blocking; call [connect] from a background thread.
 * Received GAIA PDUs are dispatched on the reader thread via [Listener].
 */
class PillClient(
    private val device: BluetoothDevice,
    private val listener: Listener
) : Closeable {

    interface Listener {
        fun onConnected()
        fun onFrame(pdu: ByteArray)
        fun onError(throwable: Throwable)
        fun onDisconnected()
    }

    private val closed = AtomicBoolean(false)
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readerThread: Thread? = null

    fun connect() {
        val s = device.createRfcommSocketToServiceRecord(UUID.fromString(GaiaProtocol.SPP_UUID))
        socket = s
        s.connect()
        input = s.inputStream
        output = s.outputStream
        listener.onConnected()
        startReader()
    }

    fun sendCommand(pdu: ByteArray) {
        val out = output ?: throw IOException("Not connected")
        val frame = GaiaProtocol.wrapRfcomm(pdu)
        synchronized(this) {
            out.write(frame)
            out.flush()
        }
    }

    fun requestSupportedBatteries() {
        sendCommand(GaiaProtocol.supportedBatteriesCommand())
    }

    fun requestSupportedFeatures() {
        sendCommand(GaiaProtocol.supportedFeaturesCommand())
    }

    fun requestSupportedFeaturesNext() {
        sendCommand(GaiaProtocol.supportedFeaturesNextCommand())
    }

    fun requestVoiceConf() {
        sendCommand(GaiaProtocol.getVoiceConfCommand())
    }

    fun setVoiceConf(enabled: Boolean, volume: Int, index: Int) {
        sendCommand(GaiaProtocol.setVoiceConfCommand(enabled, volume, index))
    }

    fun requestGain() {
        sendCommand(GaiaProtocol.getGainCommand())
    }

    fun setGain(index: Int) {
        sendCommand(GaiaProtocol.setGainCommand(index))
    }

    fun requestEqState() {
        sendCommand(GaiaProtocol.getEqStateCommand())
    }

    fun requestAvailablePreSets() {
        sendCommand(GaiaProtocol.getAvailablePreSetsCommand())
    }

    fun requestEqSet() {
        sendCommand(GaiaProtocol.getEqSetCommand())
    }

    fun setEqSet(presetId: Int) {
        sendCommand(GaiaProtocol.setEqSetCommand(presetId))
    }

    fun requestOneBringTwoState() {
        sendCommand(GaiaProtocol.getOneBringTwoStateCommand())
    }

    fun setOneBringTwoState(enabled: Boolean) {
        sendCommand(GaiaProtocol.setOneBringTwoStateCommand(enabled))
    }

    fun requestOneBringTwoTimeout() {
        sendCommand(GaiaProtocol.getOneBringTwoTimeoutCommand())
    }

    fun setOneBringTwoTimeout(timeout: Int) {
        sendCommand(GaiaProtocol.setOneBringTwoTimeoutCommand(timeout))
    }

    fun requestBatteryLevels(batteries: IntArray = intArrayOf(
        GaiaProtocol.BATTERY_SINGLE_DEVICE,
        GaiaProtocol.BATTERY_LEFT,
        GaiaProtocol.BATTERY_RIGHT,
        GaiaProtocol.BATTERY_CHARGER_CASE
    )) {
        sendCommand(GaiaProtocol.batteryLevelsCommand(batteries))
    }

    private fun startReader() {
        val thread = Thread({
            try {
                val stream = input ?: throw IOException("Input stream is null")
                while (!closed.get()) {
                    val pdu = GaiaProtocol.readFrame(stream)
                    if (!closed.get()) {
                        listener.onFrame(pdu)
                    }
                }
            } catch (t: Throwable) {
                if (!closed.get()) {
                    listener.onError(t)
                }
            } finally {
                if (!closed.get()) {
                    listener.onDisconnected()
                }
            }
        }, "HyperPILL-RFCOMM-Reader")
        readerThread = thread
        thread.start()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket?.close() }
            socket = null
            input = null
            output = null
            readerThread?.interrupt()
            readerThread = null
        }
    }
}
